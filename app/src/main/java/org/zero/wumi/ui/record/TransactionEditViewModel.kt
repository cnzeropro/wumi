package org.zero.wumi.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.CategoryType
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.data.repo.AccountRepository
import org.zero.wumi.data.repo.CategoryRepository
import org.zero.wumi.data.repo.TransactionRepository
import org.zero.wumi.util.Money
import java.math.BigDecimal
import javax.inject.Inject

/**
 * 交易编辑的选择类/预填状态（合并为单一流以占满 combine 的第 5 个位置，原因同 RecordViewModel）。
 * [loaded] 为 true 后才允许保存——防止预填未完成时以空值覆盖原交易。
 */
data class EditSelection(
    val loaded: Boolean = false,
    val currency: String = "CNY",
    val date: Long = 0L,               // 编辑不改日期，保存时原样带回
    val categoryId: String? = null,
    val accountId: String? = null,
    val targetAccountId: String? = null,
    val note: String = "",
)

/** 交易编辑 UI 状态：按原交易预填 + 分类宫格/账户选择 + canSave 门禁。 */
data class TransactionEditUiState(
    val loaded: Boolean = false,
    val type: Int = TxType.EXPENSE,
    val amountText: String = "",
    val currency: String = "CNY",
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedAccountId: String? = null,
    val selectedTargetAccountId: String? = null,
    val note: String = "",
    val canSave: Boolean = false,
)

/**
 * 交易编辑 ViewModel（Task 10）：加载原值预填（getWithRefs，已删/不存在即回退），
 * 类型联动分类列表与金额自由文本输入（parseToMinor 校验），保存走 update（LWW）、删除走 softDelete。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    private val txRepo: TransactionRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
) : ViewModel() {
    private var editingId: String? = null
    private val _type = MutableStateFlow(TxType.EXPENSE)
    private val _amount = MutableStateFlow("")
    private val _edit = MutableStateFlow(EditSelection())

    /** 记住各类型当前分类（spec §7.2 智能预选：切类型时恢复，预填时种入原分类）。 */
    private var lastExpenseCategoryId: String? = null
    private var lastIncomeCategoryId: String? = null

    /** 分类随类型联动（observeActive 已滤软删与归档），口径与 RecordViewModel 一致。 */
    private val categories = _type.flatMapLatest { t ->
        categoryRepo.observeActive(if (t == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE)
    }

    val uiState: StateFlow<TransactionEditUiState> =
        combine(_type, _amount, categories, accountRepo.observeAll(), _edit) { type, amount, cats, accs, e ->
            val decimals = Money.decimalsOf(e.currency)
            TransactionEditUiState(
                loaded = e.loaded,
                type = type,
                amountText = amount,
                currency = e.currency,
                categories = cats,
                accounts = accs,
                selectedCategoryId = e.categoryId,
                selectedAccountId = e.accountId,
                selectedTargetAccountId = e.targetAccountId,
                note = e.note,
                canSave = e.loaded
                        && Money.parseToMinor(amount, decimals)?.let { it > 0 } == true
                        && e.accountId != null
                        && (type == TxType.TRANSFER || cats.isEmpty() || e.categoryId != null)
                        && (type != TxType.TRANSFER || (e.targetAccountId != null && e.targetAccountId != e.accountId)),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionEditUiState())

    /**
     * 加载原交易并预填。已软删或不存在时回调 [onMissing] 供宿主回退——
     * 软删后的交易不可再进入编辑（Task 10 约定）。
     */
    fun load(txId: String, onMissing: () -> Unit) {
        if (editingId == txId) return
        viewModelScope.launch {
            val ref = txRepo.getWithRefs(txId)
            if (ref == null) {
                onMissing()
                return@launch
            }
            editingId = txId
            val tx = ref.tx
            if (tx.type == TxType.INCOME) lastIncomeCategoryId = tx.categoryId else lastExpenseCategoryId = tx.categoryId
            _type.value = tx.type
            // 金额文本：分 → 十进制字符串（BigDecimal 精确换算，parseToMinor 可无损往返）
            _amount.value = BigDecimal(tx.amountMinor).movePointLeft(Money.decimalsOf(tx.currency)).toPlainString()
            _edit.value = EditSelection(
                loaded = true,
                currency = tx.currency,
                date = tx.date,
                categoryId = tx.categoryId,
                accountId = tx.accountId,
                targetAccountId = tx.targetAccountId,
                note = tx.note,
            )
        }
    }

    /** 切换类型并恢复该类型上次的分类选择（与 RecordViewModel 同款）。 */
    fun setType(t: Int) {
        _type.value = t
        _edit.update { it.copy(categoryId = if (t == TxType.INCOME) lastIncomeCategoryId else lastExpenseCategoryId) }
    }

    fun setAmount(text: String) { _amount.value = text }

    fun selectCategory(id: String) {
        _edit.update { it.copy(categoryId = id) }
        if (_type.value == TxType.INCOME) lastIncomeCategoryId = id else lastExpenseCategoryId = id
    }

    fun selectAccount(id: String) { _edit.update { it.copy(accountId = id) } }

    fun selectTargetAccount(id: String) { _edit.update { it.copy(targetAccountId = id) } }

    fun setNote(s: String) { _edit.update { it.copy(note = s) } }

    /**
     * 保存编辑：走仓库 update（保持 id/createdAt，只 bump updatedAt），成功后 [onDone] 回退。
     * 校验不过直接忽略（UI 由 canSave 兜底，此处防程序化调用）。
     */
    fun save(onDone: () -> Unit) {
        val id = editingId ?: return
        val e = _edit.value
        val amount = Money.parseToMinor(_amount.value, Money.decimalsOf(e.currency)) ?: return
        if (amount <= 0) return
        val accId = e.accountId ?: return
        val type = _type.value
        val target = e.targetAccountId
        if (type == TxType.TRANSFER && (target == null || target == accId)) return
        viewModelScope.launch {
            // v0.1 单本位币：原币金额 = 本位币金额（与 RecordViewModel 口径一致）
            txRepo.update(id, type, amount, e.currency, amount, accId, if (type == TxType.TRANSFER) target else null, if (type == TxType.TRANSFER) null else e.categoryId, e.date, e.note)
            onDone()
        }
    }

    /** 软删除当前交易后 [onDone] 回退。 */
    fun delete(onDone: () -> Unit) {
        val id = editingId ?: return
        viewModelScope.launch {
            txRepo.softDelete(id)
            onDone()
        }
    }
}
