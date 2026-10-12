package org.zero.wumi.ui.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.zero.wumi.data.local.entity.TxType

/**
 * 交易编辑页（Task 10）：从明细页点交易行进入，按原值预填，保存走 update（保持
 * id/createdAt 只 bump updatedAt）、底部删除按钮确认后 softDelete，二者完成即回退。
 * 表单为轻量独立实现（金额 OutlinedTextField + parseToMinor 校验），与记一笔面板
 * 共享语义构件而非抽共享组合函数——记账/编辑两套状态机语义不同，强行合并需大改
 * RecordViewModel，威胁其既有测试与行为。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditScreen(
    txId: String,
    onDone: () -> Unit,
    vm: TransactionEditViewModel = hiltViewModel(),
) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    // 预填一次；已软删/不存在（getWithRefs 为 null）时立即回退
    LaunchedEffect(txId) { vm.load(txId, onMissing = onDone) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("编辑交易", style = MaterialTheme.typography.titleLarge)
        // 三类型切换（与记一笔一致；转账无分类）
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = s.type == TxType.EXPENSE,
                onClick = { vm.setType(TxType.EXPENSE) },
                shape = SegmentedButtonDefaults.itemShape(0, 3),
            ) { Text("支出") }
            SegmentedButton(
                selected = s.type == TxType.INCOME,
                onClick = { vm.setType(TxType.INCOME) },
                shape = SegmentedButtonDefaults.itemShape(1, 3),
            ) { Text("收入") }
            SegmentedButton(
                selected = s.type == TxType.TRANSFER,
                onClick = { vm.setType(TxType.TRANSFER) },
                shape = SegmentedButtonDefaults.itemShape(2, 3),
            ) { Text("转账") }
        }
        // 金额：自由文本 + parseToMinor 校验（canSave 门禁）
        OutlinedTextField(
            s.amountText,
            { vm.setAmount(it) },
            label = { Text("金额（${s.currency}）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // 分类宫格（转账时隐藏）；行数 × 72dp 固定高度，规避 LazyGrid 嵌套滚动约束（同 RecordSheet）
        if (s.type != TxType.TRANSFER) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height((((s.categories.size + 3) / 4) * 72).dp),
            ) {
                items(s.categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = s.selectedCategoryId == c.id,
                        onClick = { vm.selectCategory(c.id) },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(c.emoji)
                                Text(c.name, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
        // 账户选择
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            s.accounts.forEach { a ->
                FilterChip(
                    selected = s.selectedAccountId == a.id,
                    onClick = { vm.selectAccount(a.id) },
                    label = { Text("${a.emoji}${a.name}") },
                )
            }
        }
        // 转账：目标账户必须 ≠ 源账户（与记一笔一致）
        if (s.type == TxType.TRANSFER) {
            Text("转入账户", style = MaterialTheme.typography.labelMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                s.accounts.filter { it.id != s.selectedAccountId }.forEach { a ->
                    FilterChip(
                        selected = s.selectedTargetAccountId == a.id,
                        onClick = { vm.selectTargetAccount(a.id) },
                        label = { Text("${a.emoji}${a.name}") },
                    )
                }
            }
        }
        OutlinedTextField(
            s.note,
            { vm.setNote(it) },
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.save(onDone) },
            enabled = s.canSave,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("保存", fontSize = 18.sp) }
        TextButton(
            onClick = { showDeleteConfirm = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("删除交易") }
        Spacer(Modifier.navigationBarsPadding())
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这笔交易？") },
            text = { Text("删除后不再出现在明细中。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.delete(onDone)
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}
