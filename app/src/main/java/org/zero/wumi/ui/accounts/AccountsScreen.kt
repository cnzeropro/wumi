package org.zero.wumi.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.util.Money

/** 账户管理页：列表（点击编辑）+ 新建 FAB；删除保护入口在编辑面板内。 */
@Composable
fun AccountsScreen(vm: AccountsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var showNew by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) { Icon(Icons.Filled.Add, "新建账户") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(state.accounts, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text(a.name) },
                    supportingContent = { Text(accountTypeLabel(a.type)) },
                    leadingContent = { Text(a.emoji, style = MaterialTheme.typography.headlineSmall) },
                    trailingContent = { Text(Money.format(a.initialBalanceMinor, a.currency)) },
                    modifier = Modifier.clickable { editing = a },
                )
                HorizontalDivider()
            }
        }
    }
    if (showNew) {
        AccountEditSheet(
            onDismiss = { showNew = false },
            onSave = {
                vm.save(null, it.name, it.emoji, it.currency, it.initialBalanceMinor, it.type, it.creditLimitMinor, it.billingDay, it.includeInNet)
            },
            existing = null,
        )
    }
    editing?.let { e ->
        AccountEditSheet(
            onDismiss = { editing = null },
            onSave = {
                vm.save(e.id, it.name, it.emoji, it.currency, it.initialBalanceMinor, it.type, it.creditLimitMinor, it.billingDay, it.includeInNet)
            },
            existing = e,
            onArchive = { vm.archiveOrDelete(e.id) },
        )
    }
}

/** 账户类型的中文展示名（spec §5.1 AccountType）。 */
fun accountTypeLabel(type: Int) = when (type) {
    0 -> "现金"; 1 -> "储蓄卡"; 2 -> "支付宝"; 3 -> "微信"; 4 -> "信用卡"; else -> "其他"
}
