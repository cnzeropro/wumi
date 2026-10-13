package org.zero.wumi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.dao.CategorySum
import org.zero.wumi.data.local.entity.*
import org.zero.wumi.data.repo.TransactionRepository
import org.zero.wumi.util.uuid7

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionDaoTest {
    private lateinit var db: WumiDatabase
    private val dev = "dev-test"

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun acc(name: String) = AccountEntity(uuid7(), name, "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev)

    private fun tx(type: Int, baseAmount: Long, date: Long, account: String) = TransactionEntity(
        id = uuid7(), type = type, amountMinor = baseAmount, currency = "CNY", baseAmountMinor = baseAmount,
        accountId = account, date = date, createdAt = 1, updatedAt = 1, deviceId = dev,
    )

    @Test
    fun range_and_sums() = runTest {
        val a = acc("支付宝"); db.accountDao().upsert(a)
        db.transactionDao().insert(tx(TxType.EXPENSE, 5000, 1_700_000_000_000, a.id))
        db.transactionDao().insert(tx(TxType.INCOME, 20000, 1_700_000_000_001, a.id))
        val list = db.transactionDao().observeRange(0, 2_000_000_000_000).first()
        assertEquals(2, list.size)
        assertEquals(a.name, list[0].account?.name)             // Relation 生效
        val sums = db.transactionDao().sumsInRange(0, 2_000_000_000_000).first()
        assertEquals(20000L, sums.incomeMinor)
        assertEquals(5000L, sums.expenseMinor)
    }

    @Test
    fun softDeleted_excluded_from_sums() = runTest {
        val a = acc("微信"); db.accountDao().upsert(a)
        val t = tx(TxType.EXPENSE, 9999, 1_700_000_000_000, a.id)
        db.transactionDao().insert(t)
        db.transactionDao().softDelete(t.id, 2L, dev)
        val sums = db.transactionDao().sumsInRange(0, 2_000_000_000_000).first()
        assertEquals(0L, sums.expenseMinor)
    }

    @Test
    fun transfer_needs_target_and_has_no_category() = runTest {
        val a = acc("现金"); val b = acc("银行卡"); db.accountDao().upsert(a); db.accountDao().upsert(b)
        val t = tx(TxType.TRANSFER, 10000, 1_700_000_000_000, a.id).copy(targetAccountId = b.id)
        db.transactionDao().insert(t)
        val loaded = db.transactionDao().getById(t.id)!!
        assertEquals(b.id, loaded.targetAccountId)
        // 转账计入双方流水：in/out 各算一次（count 口径=该账户相关的记录数）
        assertEquals(1, db.transactionDao().countByAccount(a.id))
        assertEquals(1, db.transactionDao().countByAccount(b.id))
    }

    /** 普通 suspend 辅助函数：种一个支出分类，返回 id。 */
    private suspend fun expenseCategory(name: String): String =
        CategoryEntity(uuid7(), null, CategoryType.EXPENSE, name, "🍜", 0, createdAt = 1, updatedAt = 1, deviceId = dev)
            .also { db.categoryDao().upsert(it) }.id

    @Test
    fun sumByCategory_empty_window_returns_empty() = runTest {
        val a = acc("现金"); db.accountDao().upsert(a)
        db.transactionDao().insert(tx(TxType.EXPENSE, 100, 1_700_000_000_000, a.id))   // 故意种在窗口外
        val rows = db.transactionDao().sumByCategory(1_800_000_000_000, 1_900_000_000_000, TxType.EXPENSE).first()
        assertTrue(rows.isEmpty())
    }

    @Test
    fun sumByCategory_softDeleted_row_excluded_but_deleted_category_kept() = runTest {
        val a = acc("现金"); db.accountDao().upsert(a)
        val food = expenseCategory("餐饮")
        val gone = expenseCategory("购物")
        // 分类软删（仅墓碑，行仍在）：其历史交易仍应入图（JOIN 不过滤 c.deletedAt）
        db.categoryDao().upsert(db.categoryDao().getById(gone)!!.copy(deletedAt = 3L, updatedAt = 3L))
        val t1 = tx(TxType.EXPENSE, 2000, 1_700_000_000_000, a.id).copy(categoryId = food)
        val t2 = tx(TxType.EXPENSE, 3000, 1_700_000_000_001, a.id).copy(categoryId = gone)
        db.transactionDao().insert(t1); db.transactionDao().insert(t2)
        assertEquals(2, db.transactionDao().sumByCategory(0, 2_000_000_000_000, TxType.EXPENSE).first().size)
        // 软删交易行不计入
        db.transactionDao().softDelete(t1.id, 2L, dev)
        val after = db.transactionDao().sumByCategory(0, 2_000_000_000_000, TxType.EXPENSE).first()
        assertEquals(1, after.size)
        assertEquals(gone, after[0].categoryId)
        assertEquals(3000L, after[0].total)
    }

    @Test
    fun sumByCategory_sums_base_amount_across_currencies() = runTest {
        val repo = TransactionRepository(db, db.transactionDao(), db.outboxDao(), dev)
        val a = acc("现金"); db.accountDao().upsert(a)
        val cat = expenseCategory("餐饮")
        // CNY 1000（汇率 "1"）+ USD 1000¢ × 7.12 = 7120 → 同分类按 baseAmountMinor 合计 8120
        repo.addExpense(1000, "CNY", a.id, cat, 1_700_000_000_000, "", "1")
        repo.addExpense(1000, "USD", a.id, cat, 1_700_000_000_001, "", "7.12")
        val rows = db.transactionDao().sumByCategory(0, 2_000_000_000_000, TxType.EXPENSE).first()
        assertEquals(1, rows.size)
        assertEquals(cat, rows[0].categoryId)
        assertEquals("🍜", rows[0].emoji)                       // JOIN categories 带 emoji/name
        assertEquals("餐饮", rows[0].name)
        assertEquals(8120L, rows[0].total)
    }
}
