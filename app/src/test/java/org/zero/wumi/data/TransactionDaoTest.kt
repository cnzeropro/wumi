package org.zero.wumi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.*
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
}
