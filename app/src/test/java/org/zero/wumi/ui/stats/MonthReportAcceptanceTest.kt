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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.AccountType
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.CategoryType
import org.zero.wumi.data.repo.TransactionRepository
import org.zero.wumi.util.uuid7
import java.time.YearMonth
import java.time.ZoneId

/**
 * v0.2 验收（spec §8）：月报数字与手算一致。三币种真实 repo 链路造数
 * （addExpense/addIncome/addTransfer，外币走当笔汇率快照），对账 StatsViewModel 聚合：
 *
 * 支出 = CNY(100+200+300) + USD(100+50)×7.12 + JPY 1000×0.048 = 600+712+356+48 = **1716 分**
 * 收入 = CNY 500 + USD 20×7.12 = 500+142 = **642 分**
 * 转账（CNY 内 50）与软删支出（999）均不计收支、不入分类聚合。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MonthReportAcceptanceTest {
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

    /** 普通 suspend 辅助函数：种一个指定币种账户，返回 id。 */
    private suspend fun seedAccount(name: String, emoji: String, currency: String): String =
        AccountEntity(uuid7(), name, emoji, currency, 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = "dev-test")
            .also { db.accountDao().upsert(it) }.id

    /** 普通 suspend 辅助函数：种一个支出分类，返回 id。 */
    private suspend fun seedCategory(name: String, emoji: String): String =
        CategoryEntity(id = uuid7(), type = CategoryType.EXPENSE, name = name, emoji = emoji, createdAt = 1, updatedAt = 1, deviceId = "dev-test")
            .also { db.categoryDao().upsert(it) }.id

    @Test
    fun monthly_report_matches_hand_calculation() = runTest(dispatcher) {
        val repo = TransactionRepository(db, db.transactionDao(), db.outboxDao(), "dev-test")
        val cny = seedAccount("现金", "💵", "CNY")
        val usd = seedAccount("美元卡", "💳", "USD")
        val jpy = seedAccount("日元钱包", "💴", "JPY")
        val food = seedCategory("餐饮", "🍜")
        val zone = ZoneId.systemDefault()
        val ym = YearMonth.now(zone)                     // 固定「当前月」造数，避免跨月边界竞态
        fun at(day: Int) = ym.atDay(day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        // 支出 6 笔：CNY 三笔同挂「餐饮」验证分类合计（100+200+300=600）；USD 快照 7.12（100→712、50→356）；JPY 快照 0.048（1000→48）
        repo.addExpense(100, "CNY", cny, food, at(3), "")
        repo.addExpense(200, "CNY", cny, food, at(4), "")
        repo.addExpense(300, "CNY", cny, food, at(5), "")
        repo.addExpense(100, "USD", usd, null, at(6), "", "7.12")
        repo.addExpense(50, "USD", usd, null, at(7), "", "7.12")
        repo.addExpense(1000, "JPY", jpy, null, at(8), "", "0.048")
        // 收入 2 笔：CNY 500；USD 快照 7.12（20→142）
        repo.addIncome(500, "CNY", cny, null, at(10), "")
        repo.addIncome(20, "USD", usd, null, at(11), "", "7.12")
        // 转账 1 笔：CNY 内 50，独立成日（15 日），不计收支
        repo.addTransfer(50, "CNY", 50, cny, usd, at(15), "")
        // 软删 1 笔：支出 999 挂「餐饮」，软删后不计收支也不入分类聚合
        val deleted = repo.addExpense(999, "CNY", cny, food, at(20), "")
        repo.softDelete(deleted)

        val s = vm.uiState.first { it.monthBars != null }
        assertEquals(ym, s.month)
        // 手算对账：支出 = 600+712+356+48 = 1716；收入 = 500+142 = 642（转账与软删不计）
        assertEquals(1716L, s.expenseMinor)
        assertEquals(642L, s.incomeMinor)
        // 分类聚合：CNY 三笔同分类「餐饮」合计 600 为最大扇区（软删 999 不入聚合，故仅此一个切片）
        val slices = s.expenseSlices!!
        assertEquals(1, slices.size)
        assertEquals("餐饮", slices[0].name)
        assertEquals(600L, slices[0].total)
        // 转账不计收支：纯转账日（15 日）不出现在日柱
        assertTrue(s.dayBars!!.none { it.date == ym.atDay(15) })
        // 月柱当前月与汇总卡同口径
        val current = s.monthBars!!.last()
        assertEquals(ym, current.month)
        assertEquals(642L, current.incomeMinor)
        assertEquals(1716L, current.expenseMinor)
    }
}
