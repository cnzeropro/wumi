package org.zero.wumi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.dao.OutboxDao
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.AccountType
import org.zero.wumi.data.local.entity.OutboxEntity
import org.zero.wumi.data.repo.TransactionRepository
import org.zero.wumi.util.uuid7

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryOutboxTest {
    private lateinit var db: WumiDatabase
    private lateinit var repo: TransactionRepository
    private val dev = "dev-test"

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db, db.transactionDao(), db.outboxDao(), dev)
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun seedAccount(
        name: String = "现金",
        emoji: String = "💵",
        type: Int = AccountType.CASH,
        currency: String = "CNY",
    ): AccountEntity =
        AccountEntity(uuid7(), name, emoji, currency, 0, type, createdAt = 1, updatedAt = 1, deviceId = dev)
            .also { db.accountDao().upsert(it) }

    @Test
    fun add_fx_expense_snapshots_rate_and_base() = runTest {
        // 外币入库：baseAmountMinor 由当笔汇率快照换算（FxConverter 唯一出口），汇率随笔落库
        val usd = seedAccount(currency = "USD")
        repo.addExpense(amountMinor = 100, currency = "USD", accountId = usd.id, categoryId = null, date = 1_700_000_000_000, note = "", fxRate = "7.12")
        val tx = db.transactionDao().observeRange(0, Long.MAX_VALUE).first().first()
        assertEquals(712L, tx.tx.baseAmountMinor)      // 100¢ × 7.12 = 712 分
        assertEquals("7.12", tx.tx.exchangeRate)
    }

    @Test
    fun addExpense_writes_tx_and_outbox_atomically() = runTest {
        val a = seedAccount()
        repo.addExpense(amountMinor = 2500, currency = "CNY", accountId = a.id, categoryId = null, date = 1_700_000_000_000, note = "午饭")
        assertEquals(1, db.transactionDao().observeRange(0, Long.MAX_VALUE).first().size)
        val outbox = db.outboxDao().pending()
        assertEquals(1, outbox.size)
        assertEquals("transaction", outbox[0].entityType)
    }

    @Test
    fun softDelete_also_enqueues_outbox() = runTest {
        val a = seedAccount()
        val id = repo.addExpense(100, "CNY", a.id, null, 1_700_000_000_000, "")
        repo.softDelete(id)
        val tx = db.transactionDao().getById(id)!!
        assertNotNull(tx.deletedAt)
        assertEquals(2, db.outboxDao().pending().size) // add + delete 两条
    }

    @Test
    fun addTransfer_moves_between_accounts() = runTest {
        val a = seedAccount()
        val b = seedAccount(name = "银行卡", emoji = "💳", type = AccountType.BANK)
        repo.addTransfer(amountMinor = 50000, currency = "CNY", baseAmountMinor = 50000, accountId = a.id, targetAccountId = b.id, date = 1_700_000_000_000, note = "转入")
        val txs = db.transactionDao().observeRange(0, Long.MAX_VALUE).first()
        assertEquals(1, txs.size)
        assertEquals(b.id, txs[0].tx.targetAccountId)
    }

    @Test
    fun addTransfer_keeps_source_currency_and_1_to_1_base() = runTest {
        // 转账限同币种（v0.2）：currency 取源账户币种，本位币冗余恒按 1:1 记原币金额
        val usd = seedAccount(name = "美元户", emoji = "💳", type = AccountType.BANK, currency = "USD")
        val usd2 = seedAccount(name = "美元储蓄", emoji = "🏦", type = AccountType.BANK, currency = "USD")
        repo.addTransfer(amountMinor = 1000, currency = "USD", baseAmountMinor = 1000, accountId = usd.id, targetAccountId = usd2.id, date = 1_700_000_000_000, note = "转账")
        val tx = db.transactionDao().observeRange(0, Long.MAX_VALUE).first().first()
        assertEquals("USD", tx.tx.currency)
        assertEquals(tx.tx.amountMinor, tx.tx.baseAmountMinor)
    }

    @Test
    fun addExpense_rolls_back_tx_when_outbox_enqueue_fails() = runTest {
        // spec §6.3 原子性：outbox 入队失败 → 事务回滚 → 业务表不得残留写入
        val failing = object : OutboxDao by db.outboxDao() {
            override suspend fun enqueue(e: OutboxEntity) {
                throw IllegalStateException("simulated outbox enqueue failure")
            }
        }
        val broken = TransactionRepository(db, db.transactionDao(), failing, dev)
        val a = seedAccount()
        val result = runCatching { broken.addExpense(2500, "CNY", a.id, null, 1_700_000_000_000, "午饭") }
        assertTrue(result.isFailure)
        assertEquals(0, db.transactionDao().observeRange(0, Long.MAX_VALUE).first().size)
    }
}
