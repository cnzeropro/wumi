package org.zero.wumi.ui.accounts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.AccountType
import org.zero.wumi.util.Money

/** 表单提交载荷：字段与 [AccountEntity] 业务字段一一对应（币种 v0.1 固定 CNY）。 */
data class AccountForm(
    val name: String,
    val emoji: String,
    val currency: String,
    val initialBalanceMinor: Long,
    val type: Int,
    val creditLimitMinor: Long?,
    val billingDay: Int?,
    val includeInNet: Boolean,
)

/**
 * 新建/编辑账户底部面板（spec §7.3）。信用卡额度与账单日为 v0.1 占位输入框，
 * 不入库（保存传 null，v0.2 完善）；[onArchive] 非空时展示删除入口，
 * 删除保护（有交易归档/无交易软删）由仓库层判定。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditSheet(
    onDismiss: () -> Unit,
    onSave: (AccountForm) -> Unit,
    existing: AccountEntity?,
    onArchive: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "💵") }
    var initial by remember { mutableStateOf(existing?.let { Money.format(it.initialBalanceMinor, it.currency) } ?: "") }
    var type by remember { mutableIntStateOf(existing?.type ?: AccountType.CASH) }
    val isCredit = type == AccountType.CREDIT
    val initialValid = initial.isBlank() || Money.parseToMinor(initial, 2) != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (existing == null) "新建账户" else "编辑账户", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(emoji, { emoji = it }, label = { Text("图标（emoji）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            // 类型选择
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                listOf(
                    AccountType.CASH to "现金",
                    AccountType.BANK to "储蓄卡",
                    AccountType.ALIPAY to "支付宝",
                    AccountType.WECHAT to "微信",
                    AccountType.CREDIT to "信用卡",
                    AccountType.OTHER to "其他",
                ).forEach { (t, label) ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(label) })
                }
            }
            OutlinedTextField(
                initial,
                { initial = it },
                label = { Text("期初余额") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (!initialValid) Text("金额格式不正确")
                },
            )
            if (isCredit) {
                // v0.1 占位：额度/账单日不入库，v0.2 随多币种一并完善
                OutlinedTextField("", {}, label = { Text("信用额度") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField("", {}, label = { Text("账单日（1-31）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Button(
                onClick = {
                    onSave(AccountForm(name, emoji, "CNY", Money.parseToMinor(initial, 2) ?: 0L, type, null, null, true))
                    onDismiss()
                },
                enabled = name.isNotBlank() && initialValid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
            if (existing != null && onArchive != null) {
                TextButton(
                    onClick = {
                        onArchive.invoke()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("删除账户（有交易时将归档）") }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
