package org.zero.wumi.ui.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.zero.wumi.data.local.entity.TxType

/**
 * 记一笔底部面板（spec §7.2，3 秒记完一笔）：三类型切换 + 金额大字 + 分类宫格 +
 * 账户选择 + 自定义数字键盘 + 保存大按钮；保存成功经 [onSaved] 回传 txId 供宿主做 Snackbar 撤销。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    vm: RecordViewModel = hiltViewModel(),
) {
    val s by vm.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 三类型切换（spec §7.2）
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
            // 金额大字
            Text(
                "¥" + s.amountText.ifEmpty { "0" },
                fontSize = 48.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
            // 分类宫格（转账时隐藏）
            if (s.type != TxType.TRANSFER) {
                // 行数 × 72dp 固定高度：嵌在可滚动 Column 里规避 LazyGrid 嵌套滚动约束
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
            // 转账：目标账户必须 ≠ 源账户
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
            // 自定义数字键盘 3×4：0-9 与小数点、退格
            listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', '⌫')
                .chunked(3)
                .forEach { rowKeys ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowKeys.forEach { k ->
                            OutlinedButton(
                                onClick = { if (k == '⌫') vm.backspace() else vm.inputDigit(k) },
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text(k.toString(), fontSize = 20.sp) }
                        }
                    }
                }
            // 保存大按钮（canSave 门禁）
            Button(
                onClick = { vm.save { txId -> onSaved(txId) } },
                enabled = s.canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("保存", fontSize = 18.sp) }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}
