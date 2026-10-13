package org.zero.wumi.ui.stats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.AccountType
import org.zero.wumi.data.repo.TransactionRepository
import org.zero.wumi.util.uuid7
import java.time.YearMonth
import java.time.ZoneId

/** 图表页 ViewModel 测试：3 币种 6 笔跨两月，按当笔汇率快照折算 baseAmountMinor 后聚合（验收核心）。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: WumiDatabase
    private lateinit var vm: StatsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
        vm = StatsViewModel(TransactionRepository(db, db.transactionDao(), db.outboxDao(), "dev-test"))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    /** 普通 suspend 辅助函数（T5 模式）：种一个本位币账户，返回 id。 */
    private suspend fun seedAccount(): String =
        AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = "dev-test")
            .also { db.accountDao().upsert(it) }.id

    @Test
    fun month_bars_aggregate_multi_currency_across_two_months() = runTest(dispatcher) {
        val repo = TransactionRepository(db, db.transactionDao(), db.outboxDao(), "dev-test")
        val acc = seedAccount()
        val zone = ZoneId.systemDefault()
        val ym = YearMonth.now(zone)                     // 固定「当前月」造数，避免跨月边界竞态
        val prev = ym.minusMonths(1)
        fun at(m: YearMonth, day: Int) = m.atDay(day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        // 上月 3 笔：收入 5000（CNY）；支出 1000（CNY）+ 100¢×7.12=712（USD）→ 收入 5000 / 支出 1712
        repo.addIncome(5000, "CNY", acc, null, at(prev, 5), "")
        repo.addExpense(1000, "CNY", acc, null, at(prev, 6), "", "1")
        repo.addExpense(100, "USD", acc, null, at(prev, 7), "", "7.12")
        // 本月 3 笔：收入 1000¢×7.5=7500（USD）；支出 1000×0.05=50（JPY）+ 200（CNY）→ 收入 7500 / 支出 250
        repo.addIncome(1000, "USD", acc, null, at(ym, 5), "", "7.5")
        repo.addExpense(1000, "JPY", acc, null, at(ym, 6), "", "0.05")
        repo.addExpense(200, "CNY", acc, null, at(ym, 7), "")

        val s = vm.uiState.first { it.monthBars != null }
        assertEquals(ym, s.month)
        assertEquals(7500L, s.incomeMinor)               // 汇总卡口径 = 选中月（本月）
        assertEquals(250L, s.expenseMinor)
        // 月柱：近 6 月固定轴（含当前月、时间升序），空月补 0；两月合计各自手算核对
        val bars = s.monthBars!!
        assertEquals(6, bars.size)
        repeat(4) { i ->
            assertEquals(prev.minusMonths((4 - i).toLong()), bars[i].month)
            assertEquals(0L, bars[i].incomeMinor)
            assertEquals(0L, bars[i].expenseMinor)
        }
        assertEquals(prev, bars[4].month)
        assertEquals(5000L, bars[4].incomeMinor)
        assertEquals(1712L, bars[4].expenseMinor)
        assertEquals(ym, bars[5].month)
        assertEquals(7500L, bars[5].incomeMinor)
        assertEquals(250L, bars[5].expenseMinor)
    }
}
