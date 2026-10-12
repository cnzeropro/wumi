package org.zero.wumi.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.zero.wumi.data.local.dao.TxWithRefs
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.util.Money
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 明细页（首页）：月结余卡 + 按日分组交易列表 + 日小计 + 月切换（spec §7）。
 * 金额符号约定：支出 `-`（红 error）、收入 `+`（主色）、转账 `↔`。
 */
@Composable
fun OverviewScreen(onOpenTx: (String) -> Unit = {}, vm: OverviewViewModel = hiltViewModel()) {
    val month by vm.month.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val sums by vm.sums.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // 月切换行：上月 / 月份标题 / 下月
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.prevMonth() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上月") }
            Text(month.format(DateTimeFormatter.ofPattern("yyyy年M月")), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { vm.nextMonth() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下月") }
            Spacer(Modifier.weight(1f))
        }
        // 月结余卡：结余 = 收入 - 支出
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("本月结余", style = MaterialTheme.typography.labelMedium)
                Text(Money.format(sums.incomeMinor - sums.expenseMinor, "CNY"), fontSize = 32.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("收入 ${Money.format(sums.incomeMinor, "CNY")}", color = MaterialTheme.colorScheme.primary)
                    Text("支出 ${Money.format(sums.expenseMinor, "CNY")}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (days.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("本月还没有记录") }
        } else {
            // 按日分组列表：日期小计头 + 当日交易（DAO date DESC，最新在上）
            // weight(1f) 占满剩余高度，否则视口底边超出屏幕，月末条目滚动到底也不可见
            LazyColumn(Modifier.weight(1f)) {
                days.forEach { group ->
                    item(key = "header-${group.date}") {
                        // material3 1.3.2 无 ListSubheader（1.4.0 才有），用 Row 自绘日期小计头
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(group.date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)), style = MaterialTheme.typography.labelLarge)
                            Text(Money.format(group.dayTotalMinor, "CNY"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    items(group.items, key = { it.tx.id }) { ref -> TxRow(ref, onOpenTx) }
                }
            }
        }
    }
}

/** 单笔交易行：分类 emoji/名称（转账显示 源 → 目标）、备注、符号金额；点击进入编辑页（Task 10）。 */
@Composable
private fun TxRow(ref: TxWithRefs, onOpen: (String) -> Unit) {
    val sign = when (ref.tx.type) { TxType.EXPENSE -> "-"; TxType.INCOME -> "+"; else -> "↔" }
    ListItem(
        headlineContent = {
            Text(
                ref.category?.name
                    ?: if (ref.tx.type == TxType.TRANSFER) "${ref.account?.name ?: "未知账户"} → ${ref.targetAccount?.name ?: "未知账户"}" else "未分类",
            )
        },
        supportingContent = { if (ref.tx.note.isNotBlank()) Text(ref.tx.note) },
        leadingContent = { Text(ref.category?.emoji ?: "🔄", style = MaterialTheme.typography.headlineSmall) },
        trailingContent = { Text("$sign${Money.format(ref.tx.amountMinor, ref.tx.currency)}") },
        modifier = Modifier.clickable { onOpen(ref.tx.id) },
    )
    HorizontalDivider()
}
