package org.zero.wumi.ui.overview

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.zero.wumi.data.local.entity.TransactionEntity
import org.zero.wumi.data.local.entity.TxSource
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.data.repo.TransactionRepository
import org.zero.wumi.util.uuid7
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 明细页 ViewModel 测试：按本地日分组与日小计（最新在上）、月汇总、月切换（Task 9）。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: WumiDatabase
    private lateinit var vm: OverviewViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
        vm = OverviewViewModel(TransactionRepository(db, db.transactionDao(), db.outboxDao(), "dev-test"))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    /** 普通 suspend 辅助函数（T5 模式）：取首个账户（无则种「现金」），记一笔本位币交易。 */
    private suspend fun tx(type: Int, base: Long, date: Long) {
        val a = db.accountDao().observeAll().first().firstOrNull()
            ?: AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = "dev-test")
                .also { db.accountDao().upsert(it) }
        TransactionEntity(uuid7(), type, base, "CNY", null, base, a.id, null, null, date, "", TxSource.MANUAL, 1, 1, null, "dev-test")
            .also { db.transactionDao().insert(it) }
    }

    @Test
    fun groups_by_local_day_with_day_totals() = runTest(dispatcher) {
        val ym = YearMonth.now(ZoneId.systemDefault())   // 全程用同一 YearMonth，避免跨月边界竞态
        val zone = ZoneId.systemDefault()
        val d10 = ym.atDay(10).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val d10b = ym.atDay(10).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        val d11 = ym.atDay(11).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        tx(TxType.EXPENSE, 2500, d10)
        tx(TxType.EXPENSE, 1500, d10b)
        tx(TxType.INCOME, 10000, d11)
        advanceUntilIdle()
        val days = vm.days.first { it.isNotEmpty() }
        assertEquals(2, days.size)
        // 最新在上（DAO date DESC + groupBy 保序）：11 日组在前、10 日组在后
        assertEquals(ym.atDay(11), days[0].date)
        assertEquals(10000L, days[0].dayTotalMinor)
        assertEquals(ym.atDay(10), days[1].date)
        assertEquals(-4000L, days[1].dayTotalMinor)     // 10 日两笔支出合计（负号）
    }

    @Test
    fun month_sums_reflect_current_month() = runTest(dispatcher) {
        val now = Instant.now().toEpochMilli()
        tx(TxType.EXPENSE, 3000, now)
        tx(TxType.INCOME, 8000, now)
        advanceUntilIdle()
        val sums = vm.sums.first { it.expenseMinor > 0 }
        assertEquals(3000L, sums.expenseMinor)
        assertEquals(8000L, sums.incomeMinor)
    }

    @Test
    fun prev_next_month_navigates() = runTest(dispatcher) {
        val m0 = vm.month.first()
        vm.prevMonth()
        assertEquals(m0.minusMonths(1), vm.month.first())
        vm.nextMonth()
        assertEquals(m0, vm.month.first())
    }
}
