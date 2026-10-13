package org.zero.wumi.ui.fx

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigDecimal
import kotlinx.coroutines.launch
import org.zero.wumi.data.local.entity.CurrencyEntity

/**
 * 汇率管理页（v0.2 Task 8）：币种列表（1 外币 = ? CNY，相对时间）+ 顶部「立即更新」「自动拉取」。
 * 点击行弹窗手改汇率（实时校验 BigDecimal > 0）；刷新结果与非法改率经 Snackbar 反馈。
 */
@Composable
fun FxScreen(vm: FxViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CurrencyEntity?>(null) }

    // 刷新结果一次性事件 → Snackbar（Channel 缓存不丢，无需复腐组合重放）
    LaunchedEffect(Unit) {
        vm.message.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = { vm.refresh() }, enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("立即更新")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("自动拉取", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.autoSync, onCheckedChange = vm::setAutoSync)
                }
            }
            items(state.currencies, key = { it.code }) { c ->
                FxRow(c, onClick = { editing = c })
            }
        }
    }
    editing?.let { c ->
        RateEditDialog(
            code = c.code,
            current = c.rateToBase,
            onDismiss = { editing = null },
            onSave = { text ->
                editing = null
                scope.launch {
                    // false = 非法（对话框前置校验已拦，双保险兜底提示）
                    if (!vm.updateRate(c.code, text)) snackbar.showSnackbar("汇率格式不正确")
                }
            },
        )
    }
}

/** 币种行：`$ USD 美元`｜`1 USD = 7.12 CNY · 3 天前更新`，点击弹编辑框。 */
@Composable
private fun FxRow(c: CurrencyEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text("${c.symbol} ${c.code} ${c.name}") },
        supportingContent = {
            // 相对时间复用系统 DateUtils（当天/昨天/N 天前等本地化文案）
            Text("1 ${c.code} = ${c.rateToBase} CNY · ${DateUtils.getRelativeTimeSpanString(c.rateUpdatedAt)}")
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

/** 手改汇率弹窗：预填现值，实时校验 BigDecimal 合法且 > 0，保存按钮随校验启用。 */
@Composable
private fun RateEditDialog(code: String, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    val valid = runCatching { BigDecimal(text) > BigDecimal.ZERO }.getOrDefault(false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改 $code 汇率") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("1 $code = ? CNY") },
                singleLine = true,
                isError = !valid,
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(text) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
