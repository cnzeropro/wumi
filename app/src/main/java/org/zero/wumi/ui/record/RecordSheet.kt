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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.util.Money
import java.math.BigDecimal

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
    val symbols by vm.currencySymbols.collectAsStateWithLifecycle()
    // 金额符号按账户币种取 currencies 表，无符号币种回退代码
    val currencySymbol = symbols[s.currency] ?: s.currency
    var showFxDialog by remember { mutableStateOf(false) }

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
            // 金额大字（符号随账户币种）
            Text(
                currencySymbol + s.amountText.ifEmpty { "0" },
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
            // 转账：目标账户必须 ≠ 源账户，且限同币种（跨币种置灰）
            if (s.type == TxType.TRANSFER) {
                val source = s.accounts.find { it.id == s.selectedAccountId }
                val candidates = s.accounts.filter { it.id != s.selectedAccountId }
                val hasCrossCurrency = source != null && candidates.any { it.currency != source.currency }
                Text("转入账户", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    candidates.forEach { a ->
                        FilterChip(
                            selected = s.selectedTargetAccountId == a.id,
                            enabled = source == null || a.currency == source.currency,
                            onClick = { vm.selectTargetAccount(a.id) },
                            label = { Text("${a.emoji}${a.name}") },
                        )
                    }
                }
                if (hasCrossCurrency) {
                    Text(
                        "暂不支持跨币种转账",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 折算条（外币账户）：实时折算本位币 + 点按改当笔汇率（转账恒 1:1，不显示）
            if (s.fxRate != null && s.type != TxType.TRANSFER) {
                AssistChip(
                    onClick = { showFxDialog = true },
                    label = {
                        Text(
                            "≈ " + Money.format(s.basePreviewMinor ?: 0L, "CNY") +
                                " · 汇率 ${s.fxRate}（点按修改）",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showFxDialog) {
                // remember 在对话框进入组合时创建——预填当笔现值
                var rateText by remember { mutableStateOf(s.fxRate ?: "") }
                // 正数且有上界（十万倍，覆盖现实币种对）——防 "1e18" 等超大值在 save 换算时溢出崩溃
                val valid = rateText.trim().toBigDecimalOrNull()?.let { it > BigDecimal.ZERO && it <= BigDecimal("100000") } == true
                AlertDialog(
                    onDismissRequest = { showFxDialog = false },
                    title = { Text("修改当笔汇率") },
                    text = {
                        OutlinedTextField(
                            value = rateText,
                            onValueChange = { rateText = it },
                            label = { Text("1 ${s.currency} = ? CNY") },
                            isError = rateText.isNotBlank() && !valid,
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = valid,
                            onClick = {
                                vm.setFxRate(rateText.trim())
                                showFxDialog = false
                            },
                        ) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFxDialog = false }) { Text("取消") }
                    },
                )
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
