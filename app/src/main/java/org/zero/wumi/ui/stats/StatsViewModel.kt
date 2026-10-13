package org.zero.wumi.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.zero.wumi.data.local.dao.TxWithRefs
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.data.repo.TransactionRepository
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 图表页 ViewModel：选中月驱动汇总卡/分类切片/日柱；月柱用固定近 6 月轴（含当前月、
 * 锚定构建时刻），不随月切换变化（简化口径）。全部按 baseAmountMinor 本位币聚合，
 * 跨币种经当笔汇率快照折算（历史账单不随汇率波动重算）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val txRepo: TransactionRepository,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val _month = MutableStateFlow(YearMonth.now(zone))

    // 近 6 月轴基准月：构造期捕获。数据窗 monthBarsRange 同样锚定构造期，若发射期重取 now()
    // 会出现「窗不漂而轴漂」，跨月后本月柱恒 0（T11 审查 Minor 1 修复）
    private val anchorMonth: YearMonth = YearMonth.now(zone)

    /** 当前查看的月份。 */
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** [m] 的半开区间 [月初 0 点, 次月初 0 点)，与 DAO 的 date >= from AND date < to 口径一致。 */
    private fun monthRange(m: YearMonth): Pair<Long, Long> =
        m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    // 近 6 月固定窗（含基准月）：月柱专用，切月只重算不换窗，结果不变
    private val monthBarsRange: Pair<Long, Long> =
        monthRange(anchorMonth.minusMonths(5)).first to monthRange(anchorMonth).second

    /** 本组收入小计（转账不计：非收入按 0 汇总）。 */
    private fun List<TxWithRefs>.sumIncome(): Long =
        sumOf { ref -> if (ref.tx.type == TxType.INCOME) ref.tx.baseAmountMinor else 0L }

    /** 本组支出小计（转账不计：非支出按 0 汇总）。 */
    private fun List<TxWithRefs>.sumExpense(): Long =
        sumOf { ref -> if (ref.tx.type == TxType.EXPENSE) ref.tx.baseAmountMinor else 0L }

    /**
     * 日分组（时间升序供图表横轴）：收入/支出分列，转账不计。
     * 全 0 日（纯转账日）过滤掉不渲染 0 高柱（T11 审查 Minor 2 裁决）。
     */
    private fun dayBarsOf(txs: List<TxWithRefs>): List<DayBar> =
        txs.groupBy { Instant.ofEpochMilli(it.tx.date).atZone(zone).toLocalDate() }
            .map { (date, items) -> DayBar(date, items.sumIncome(), items.sumExpense()) }
            .filter { it.incomeMinor != 0L || it.expenseMinor != 0L }
            .sortedBy { it.date }

    /** 月分组（近 6 月固定轴，时间升序）：空月补 0，保证横轴连续；轴基准为构造期 [anchorMonth]。 */
    private fun monthBarsOf(txs: List<TxWithRefs>): List<MonthBar> {
        val sums = txs.groupBy { YearMonth.from(Instant.ofEpochMilli(it.tx.date).atZone(zone)) }
            .mapValues { entry -> Pair(entry.value.sumIncome(), entry.value.sumExpense()) }
        return (5 downTo 0).map { back ->
            val m = anchorMonth.minusMonths(back.toLong())
            val ie = sums[m] ?: Pair(0L, 0L)
            MonthBar(m, ie.first, ie.second)
        }
    }

    /**
     * 图表页全量状态：五个 Room 流 combine 为单一 state，首个发射前各字段为 null（加载中）。
     * 选中月四流走 [flatMapLatest] 切换订阅区间；月柱流窗口固定，随切月重算但结果不变。
     */
    val uiState: StateFlow<StatsUiState> = _month.flatMapLatest { m ->
        val (from, to) = monthRange(m)
        combine(
            txRepo.observeMonthSums(from, to),
            txRepo.observeSumsByCategory(from, to, TxType.EXPENSE),
            txRepo.observeSumsByCategory(from, to, TxType.INCOME),
            txRepo.observeRange(from, to),
            txRepo.observeRange(monthBarsRange.first, monthBarsRange.second),
        ) { sums, expenseSlices, incomeSlices, monthTxs, barsTxs ->
            StatsUiState(
                m,
                sums.incomeMinor,
                sums.expenseMinor,
                expenseSlices,
                incomeSlices,
                dayBarsOf(monthTxs),
                monthBarsOf(barsTxs),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun prevMonth() { _month.value = _month.value.minusMonths(1) }

    fun nextMonth() { _month.value = _month.value.plusMonths(1) }
}
