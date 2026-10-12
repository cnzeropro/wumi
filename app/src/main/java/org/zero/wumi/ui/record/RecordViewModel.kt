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
import javax.inject.Inject

/**
 * 记账表单的选择类状态（分类/账户/对方账户/备注/日期），合并为单一 MutableStateFlow——
 * coroutines 1.7+ 的 combine 最多 5 流，多字段并入一个流以占满最后一个位置。
 */
data class Selection(
    val categoryId: String? = null,
    val accountId: String? = null,
    val targetAccountId: String? = null,
    val note: String = "",
    val date: Long = 0L,               // 0 = 现在
)

/** 记一笔 UI 状态（spec §7.2）：类型/金额大字/分类宫格/账户/备注/日期 + canSave 门禁。 */
data class RecordUiState(
    val type: Int = TxType.EXPENSE,
    val amountText: String = "",
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedAccountId: String? = null,
    val selectedTargetAccountId: String? = null,
    val note: String = "",
    val date: Long = 0L,               // 0 = 现在
    val canSave: Boolean = false,
)

/**
 * 记一笔 ViewModel（spec §7.2，3 秒记完一笔）：金额输入状态机 + 分类宫格 + 三类型切换。
 * 上游合并为 5 流：_type / _amount / categories / accounts / _selection。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val txRepo: TransactionRepository,
    accountRepo: AccountRepository,
    categoryRepo: CategoryRepository,
) : ViewModel() {
    private val _type = MutableStateFlow(TxType.EXPENSE)
    private val _amount = MutableStateFlow("")
    private val _selection = MutableStateFlow(Selection())

    /** 记住上次分类（spec §7.2 智能预选：切类型时恢复） */
    private var lastExpenseCategoryId: String? = null
    private var lastIncomeCategoryId: String? = null

    /** 分类随类型联动（observeActive 已滤软删与归档）。 */
    private val categories = _type.flatMapLatest { t ->
        categoryRepo.observeActive(if (t == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE)
    }
    private val accounts = accountRepo.observeAll()

    val uiState: StateFlow<RecordUiState> =
        combine(_type, _amount, categories, accounts, _selection) { type, amount, cats, accs, sel ->
            RecordUiState(
                type = type,
                amountText = amount,
                categories = cats,
                accounts = accs,
                selectedCategoryId = sel.categoryId,
                selectedAccountId = sel.accountId,
                selectedTargetAccountId = sel.targetAccountId,
                note = sel.note,
                date = sel.date,
                canSave = Money.parseToMinor(amount, 2)?.let { it > 0 } == true
                        && sel.accountId != null
                        && (type == TxType.TRANSFER || cats.isEmpty() || sel.categoryId != null)
                        && (type != TxType.TRANSFER || (sel.targetAccountId != null && sel.targetAccountId != sel.accountId)),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordUiState())

    /** 切换类型并恢复该类型上次的分类选择（智能预选，spec §7.2）。 */
    fun setType(t: Int) {
        _type.value = t
        _selection.update { it.copy(categoryId = if (t == TxType.INCOME) lastIncomeCategoryId else lastExpenseCategoryId) }
    }

    fun selectCategory(id: String?) {
        _selection.update { it.copy(categoryId = id) }
        if (_type.value == TxType.INCOME) lastIncomeCategoryId = id else lastExpenseCategoryId = id
    }

    fun selectAccount(id: String) { _selection.update { it.copy(accountId = id) } }

    fun selectTargetAccount(id: String) { _selection.update { it.copy(targetAccountId = id) } }

    fun setNote(s: String) { _selection.update { it.copy(note = s) } }

    fun setDate(millis: Long) { _selection.update { it.copy(date = millis) } }

    /** 金额状态机：≤9 位整数 + ≤2 位小数、单小数点、去前导零（spec §5.3 输入层限制）。 */
    fun inputDigit(c: Char) {
        val cur = _amount.value
        when {
            c == '.' -> if (!cur.contains('.')) _amount.value = if (cur.isEmpty()) "0." else "$cur."
            c.isDigit() -> {
                val hasDot = cur.contains('.')
                val intLen = if (hasDot) cur.substringBefore('.').length else cur.length
                val fracLen = if (hasDot) cur.substringAfter('.').length else 0
                when {
                    !hasDot && cur == "0" -> _amount.value = c.toString()   // 去前导零
                    !hasDot && intLen >= 9 -> Unit                          // 9 位整数上限
                    hasDot && fracLen >= 2 -> Unit                          // 2 位小数上限
                    else -> _amount.value = cur + c
                }
            }
        }
    }

    fun backspace() { _amount.value = _amount.value.dropLast(1) }

    fun clearAmount() { _amount.value = "" }

    /**
     * 保存当前笔（金额/账户非法则直接忽略，UI 由 canSave 兜底）；成功后只清金额/备注/日期、
     * 保留账户与分类选择，便于连续记账；经 [onDone] 回传 txId 供 Snackbar 撤销（spec §7.2）。
     */
    fun save(onDone: (String) -> Unit) {
        val amount = Money.parseToMinor(_amount.value, 2) ?: return
        val sel = _selection.value
        val accId = sel.accountId ?: return
        val type = _type.value
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val date = if (sel.date == 0L) now else sel.date
            // v0.1 单本位币：原币金额 = 本位币金额，币种固定 CNY（多币种 v0.2）
            val txId = when (type) {
                TxType.INCOME -> txRepo.addIncome(amount, "CNY", amount, accId, sel.categoryId, date, sel.note)
                TxType.TRANSFER -> txRepo.addTransfer(amount, "CNY", amount, accId, sel.targetAccountId!!, date, sel.note)
                else -> txRepo.addExpense(amount, "CNY", amount, accId, sel.categoryId, date, sel.note)
            }
            _amount.value = ""
            _selection.update { it.copy(note = "", date = 0L) }
            onDone(txId)
        }
    }
}
