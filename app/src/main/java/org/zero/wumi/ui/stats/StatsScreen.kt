package org.zero.wumi.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import org.zero.wumi.data.local.dao.CategorySum
import org.zero.wumi.ui.theme.WumiSeed
import org.zero.wumi.util.Money
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 图表页（spec §8 五图）：月切换 → 汇总卡 → ①环形+②分类排行（联动）→ ③每日收支柱状 →
 * ④近 6 月对比柱状 → ⑤近 6 月趋势折线。柱/折线为双系列：收入米金 [WumiSeed]、支出红 error。
 * 加载态（state 各字段为 null）与空态（选中月无收支且无分类切片）分开呈现。
 */
@Composable
fun StatsScreen(vm: StatsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    if (state.month == null) {
        // 加载态：Room 首个 combine 发射前（uiState 全字段 null，原子发射，month != null 即全量就绪）
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        StatsContent(
            s = state,
            onPrevMonth = vm::prevMonth,
            onNextMonth = vm::nextMonth,
        )
    }
}

/** 图表页主体：此处各字段已随首个发射就绪，直接解包（month == null 已在上层拦截）。 */
@Composable
private fun StatsContent(s: StatsUiState, onPrevMonth: () -> Unit, onNextMonth: () -> Unit) {
    val month = s.month!!
    val incomeMinor = s.incomeMinor!!
    val expenseMinor = s.expenseMinor!!
    val expenseSlices = s.expenseSlices!!
    val incomeSlices = s.incomeSlices!!
    val dayBars = s.dayBars!!
    val monthBars = s.monthBars!!

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { MonthHeader(month, onPrevMonth, onNextMonth) }
        item { SummaryCard(incomeMinor, expenseMinor) }
        if (incomeMinor == 0L && expenseMinor == 0L && expenseSlices.isEmpty() && incomeSlices.isEmpty()) {
            // 空态：选中月既无收支也无分类切片，图表区显示占位卡（近 6 月图不随选中月换窗，保留展示）
            item {
                Card(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("本月暂无收支记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item { DonutSection(expenseSlices, incomeSlices) }
            item {
                ChartCard("每日收支") {
                    Legend()
                    DualColumnChart(
                        x = dayBars.map { it.date.dayOfMonth.toFloat() },
                        income = dayBars.map { it.incomeMinor.toFloat() },
                        expense = dayBars.map { it.expenseMinor.toFloat() },
                        columnWidth = 6.dp,
                        bottomFormatter = CartesianValueFormatter { _, x, _ -> "${x.roundToInt()}日" },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
            }
        }
        item {
            ChartCard("近 6 月对比") {
                Legend()
                DualColumnChart(
                    x = monthBars.indices.map { it.toFloat() },
                    income = monthBars.map { it.incomeMinor.toFloat() },
                    expense = monthBars.map { it.expenseMinor.toFloat() },
                    columnWidth = 14.dp,
                    bottomFormatter = monthAxisFormatter(monthBars),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
        }
        item {
            ChartCard("近 6 月趋势") {
                Legend()
                DualLineChart(
                    x = monthBars.indices.map { it.toFloat() },
                    income = monthBars.map { it.incomeMinor.toFloat() },
                    expense = monthBars.map { it.expenseMinor.toFloat() },
                    bottomFormatter = monthAxisFormatter(monthBars),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
        }
    }
}

/** 月切换行：上月 / 月份标题 / 下月（复用明细页 OverviewScreen 模式）。 */
@Composable
private fun MonthHeader(month: YearMonth, prevMonth: () -> Unit, nextMonth: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = prevMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上月") }
        Text(month.format(DateTimeFormatter.ofPattern("yyyy年M月")), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = nextMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下月") }
    }
}

/** 本月汇总卡：收入米金 / 支出红（本位币，转账不计口径由 VM 保证）。 */
@Composable
private fun SummaryCard(incomeMinor: Long, expenseMinor: Long) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("本月收入", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(incomeMinor, "CNY"), color = WumiSeed, style = MaterialTheme.typography.titleLarge)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("本月支出", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(expenseMinor, "CNY"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/** ①②环形 + 分类排行联动卡：支出/收入 chips 切换切片，环形与排行共用选中态双向高亮。 */
@Composable
private fun DonutSection(expenseSlices: List<CategorySum>, incomeSlices: List<CategorySum>) {
    var isExpense by rememberSaveable { mutableStateOf(true) }
    val slices = if (isExpense) expenseSlices else incomeSlices
    // 切片列表随 Room 发射更新（含 chips 切换）时重置选中，避免选中索引错位到新列表
    var selected by remember(slices) { mutableStateOf<Int?>(null) }
    val colors = WumiPalette.slices()
    val centerText = Money.format(slices.sumOf { it.total }, "CNY")

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = isExpense, onClick = { isExpense = true }, label = { Text("支出") })
                FilterChip(selected = !isExpense, onClick = { isExpense = false }, label = { Text("收入") })
            }
            DonutChart(slices, colors, centerText, selected, onSelect = { selected = it })
            CategoryRankList(slices, selected, onSelect = { selected = it })
        }
    }
}

/** 图表卡通用容器：标题 + 内容（柱状/折线共用）。 */
@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** 双系列图例：收入米金 / 支出红，与柱/折线用色一致。 */
@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(WumiSeed, "收入")
        LegendDot(MaterialTheme.colorScheme.error, "支出")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** 近 6 月横轴标签：柱/折线共用「M 月」（x 为 0..5 轴下标，映射回 monthBars 取月值）。 */
private fun monthAxisFormatter(monthBars: List<MonthBar>): CartesianValueFormatter {
    val labels = monthBars.map { "${it.month.monthValue}月" }
    return CartesianValueFormatter { _, x, _ -> labels.getOrNull(x.roundToInt()).orEmpty() }
}

/**
 * 双系列柱状图（Vico 2.x）：收入米金 / 支出红分组并排；起始轴以人民币紧凑刻度显示。
 * [x] 与收入/支出 y 等长；[bottomFormatter] 生成「d 日」「M 月」等横轴标签。
 */
@Composable
private fun DualColumnChart(
    x: List<Float>,
    income: List<Float>,
    expense: List<Float>,
    columnWidth: Dp,
    bottomFormatter: CartesianValueFormatter,
    modifier: Modifier = Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(x, income, expense) {
        producer.runTransaction {
            columnSeries {
                series(x, income)
                series(x, expense)
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill(WumiSeed), columnWidth),
                    rememberLineComponent(fill(MaterialTheme.colorScheme.error), columnWidth),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = startAxisFormatter()),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
        modelProducer = producer,
        modifier = modifier,
    )
}

/** 双系列折线图（Vico 2.x）：收入米金 / 支出红两条趋势线。 */
@Composable
private fun DualLineChart(
    x: List<Float>,
    income: List<Float>,
    expense: List<Float>,
    bottomFormatter: CartesianValueFormatter,
    modifier: Modifier = Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(x, income, expense) {
        producer.runTransaction {
            lineSeries {
                series(x, income)
                series(x, expense)
            }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(WumiSeed))),
                    LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(MaterialTheme.colorScheme.error))),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = startAxisFormatter()),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
        ),
        modelProducer = producer,
        modifier = modifier,
    )
}

/**
 * 起始轴刻度：本位币分为轴值，万元及以上折「万」紧凑显示，其余按 Money 全额格式化。
 * 轴刻度仅作读数参考，非金额存储口径（存储仍为 Long 分，Float 仅 Vico 2.x 绘制层内部使用）。
 */
private fun startAxisFormatter(): CartesianValueFormatter =
    CartesianValueFormatter { _, v, _ ->
        val yuan = v.toDouble() / 100.0
        if (yuan >= 10000.0) String.format(Locale.CHINA, "¥%.1f万", yuan / 10000.0)
        else Money.format(v.roundToLong(), "CNY")
    }
