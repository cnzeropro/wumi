package org.zero.wumi.ui.record

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.AccountType
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.data.repo.TransactionRepository
import org.zero.wumi.util.uuid7

/** 交易编辑（Task 10）：update 保持 id/createdAt 只 bump updatedAt（LWW 语义）+ outbox 入队 + 软删后不可再取。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionEditTest {
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

    @Test
    fun update_keeps_id_and_createdAt_bumps_updatedAt() = runTest {
        val a = AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.accountDao().upsert(it) }
        val id = repo.addExpense(2500, "CNY", 2500, a.id, null, 1_700_000_000_000, "午饭")
        val before = db.transactionDao().getById(id)!!
        repo.update(id, TxType.EXPENSE, 3500, "CNY", 3500, a.id, null, null, before.date, "晚饭加鸡腿")
        val after = db.transactionDao().getById(id)!!
        assertEquals(id, after.id)                    // id 不变
        assertEquals(before.createdAt, after.createdAt)
        assertEquals(3500L, after.amountMinor)
        assertTrue(after.updatedAt > before.updatedAt)
    }

    @Test
    fun update_enqueues_outbox() = runTest {
        val a = AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.accountDao().upsert(it) }
        val id = repo.addExpense(100, "CNY", 100, a.id, null, 1L, "")
        val beforeCount = db.outboxDao().pending().size
        repo.update(id, TxType.EXPENSE, 200, "CNY", 200, a.id, null, null, 1L, "")
        assertEquals(beforeCount + 1, db.outboxDao().pending().size)
    }

    @Test
    fun deleted_tx_not_editable_getWithRefs_returns_null_after_softDelete() = runTest {
        val a = AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.accountDao().upsert(it) }
        val id = repo.addExpense(100, "CNY", 100, a.id, null, 1L, "")
        assertNotNull(repo.getWithRefs(id))
        repo.softDelete(id)
        assertNull(repo.getWithRefs(id))
    }
}
