package org.zero.wumi.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.repo.AccountRepository
import javax.inject.Inject

/** 账户列表页 UI 状态。 */
data class AccountsUiState(val accounts: List<AccountEntity> = emptyList())

/** 账户管理 ViewModel：订阅账户列表（Room 唯一真相源），代理保存与删除保护操作。 */
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repo: AccountRepository,
) : ViewModel() {

    /** 实时账户列表；归档/墓碑账户已被 DAO 过滤，列表即全部可用账户。 */
    val state: StateFlow<AccountsUiState> = repo.observeAll()
        .map { AccountsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    /** 新建或更新账户（id 为空即新建）；业务表 + outbox 同事务由仓库保证。 */
    fun save(
        id: String?,
        name: String,
        emoji: String,
        currency: String,
        initialBalanceMinor: Long,
        type: Int,
        creditLimitMinor: Long?,
        billingDay: Int?,
        includeInNet: Boolean,
    ) = viewModelScope.launch {
        repo.save(id, name, emoji, currency, initialBalanceMinor, type, creditLimitMinor, billingDay, includeInNet)
    }

    /**
     * 删除保护入口（spec §9.2）：账户下有交易→归档，无交易→软删除。
     * 结果跨协程同步返回不可靠，故不回传；列表随 Room Flow 自动刷新体现结果。
     */
    fun archiveOrDelete(id: String) {
        viewModelScope.launch { repo.archiveOrDelete(id) }
    }
}
