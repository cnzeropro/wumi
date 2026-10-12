package org.zero.wumi.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.zero.wumi.data.local.dao.MonthSums
import org.zero.wumi.data.local.dao.TxWithRefs
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.data.repo.TransactionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** 单日分组：日期 + 日小计（收入为正、支出为负、转账不计）+ 当日交易列表。 */
data class DayGroup(val date: LocalDate, val dayTotalMinor: Long, val items: List<TxWithRefs>)

/**
 * 明细页 ViewModel：按月订阅交易明细与收支汇总（Room 唯一真相源），
 * 按本地时区将交易时刻归到 LocalDate 分组；月切换即切换订阅区间。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val txRepo: TransactionRepository,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val _month = MutableStateFlow(YearMonth.now(zone))

    /** 当前查看的月份。 */
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** [m] 的半开区间 [月初 0 点, 次月初 0 点)，与 DAO 的 date >= from AND date < to 口径一致。 */
    private fun monthRange(m: YearMonth): Pair<Long, Long> =
        m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * 按日分组列表：DAO 按 date DESC 排序，groupBy 保序，故分组即日期倒序（最新在上）。
     * 日小计 = 收入 +（支出取负），转账不计入（else -> 0L）。
     */
    val days: StateFlow<List<DayGroup>> = _month.flatMapLatest { m ->
        val (from, to) = monthRange(m)
        txRepo.observeRange(from, to)
    }.map { list ->
        list.groupBy { Instant.ofEpochMilli(it.tx.date).atZone(zone).toLocalDate() }
            .map { (date, items) ->
                val total = items.sumOf { ref ->
                    when (ref.tx.type) {
                        TxType.EXPENSE -> -ref.tx.baseAmountMinor   // 支出
                        TxType.INCOME -> ref.tx.baseAmountMinor     // 收入
                        else -> 0L                                  // 转账不计日小计
                    }
                }
                DayGroup(date, total, items)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 本月收支汇总（本位币口径，单位分）。 */
    val sums: StateFlow<MonthSums> = _month.flatMapLatest { m ->
        val (from, to) = monthRange(m)
        txRepo.observeMonthSums(from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSums(0, 0))

    fun prevMonth() { _month.value = _month.value.minusMonths(1) }

    fun nextMonth() { _month.value = _month.value.plusMonths(1) }
}
