package org.zero.wumi.ui.stats

import org.zero.wumi.data.local.dao.CategorySum
import java.time.LocalDate
import java.time.YearMonth

/** 单日柱：当日收入/支出（本位币分；转账不计；无交易的日期不产生条目）。 */
data class DayBar(val date: LocalDate, val incomeMinor: Long, val expenseMinor: Long)

/** 单月柱：当月收入/支出（本位币分；跨币种已按当笔汇率快照折算合计）。 */
data class MonthBar(val month: YearMonth, val incomeMinor: Long, val expenseMinor: Long)

/** 图表页 UI 状态：各字段 null=加载中（Room 首个发射未到）。 */
data class StatsUiState(
    val month: YearMonth? = null,                 // 当前查看的月份
    val incomeMinor: Long? = null,                // 本月收入（汇总卡）
    val expenseMinor: Long? = null,               // 本月支出（汇总卡）
    val expenseSlices: List<CategorySum>? = null, // 支出分类切片（total 降序，随月切换）
    val incomeSlices: List<CategorySum>? = null,  // 收入分类切片（total 降序，随月切换）
    val dayBars: List<DayBar>? = null,            // 当月日柱（时间升序，随月切换）
    val monthBars: List<MonthBar>? = null,        // 近 6 月月柱（时间升序，固定轴不随月切换）
)
