package org.zero.wumi.ui.record

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.CategoryType
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.data.repo.AccountRepository
import org.zero.wumi.data.repo.CategoryRepository
import org.zero.wumi.data.repo.TransactionRepository
import org.zero.wumi.util.uuid7

/** 交易编辑（Task 10）：update 保持 id/createdAt 只 bump updatedAt（LWW 语义）+ outbox 入队 + 软删后不可再取 + canSave 门禁。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionEditTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: WumiDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var vm: TransactionEditViewModel
    private val dev = "dev-test"

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db, db.transactionDao(), db.outboxDao(), dev)
        vm = TransactionEditViewModel(repo, AccountRepository(db, dev), CategoryRepository(db, dev))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun update_keeps_id_and_createdAt_bumps_updatedAt() = runTest {
        val a = AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.accountDao().upsert(it) }
        val id = repo.addExpense(2500, "CNY", a.id, null, 1_700_000_000_000, "午饭")
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
        val id = repo.addExpense(100, "CNY", a.id, null, 1L, "")
        val beforeCount = db.outboxDao().pending().size
        repo.update(id, TxType.EXPENSE, 200, "CNY", 200, a.id, null, null, 1L, "")
        assertEquals(beforeCount + 1, db.outboxDao().pending().size)
    }

    @Test
    fun deleted_tx_not_editable_getWithRefs_returns_null_after_softDelete() = runTest {
        val a = AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.accountDao().upsert(it) }
        val id = repo.addExpense(100, "CNY", a.id, null, 1L, "")
        assertNotNull(repo.getWithRefs(id))
        repo.softDelete(id)
        assertNull(repo.getWithRefs(id))
    }

    @Test
    fun update_transfer_to_expense_clears_target() = runTest {
        val a = AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.accountDao().upsert(it) }
        val b = AccountEntity(uuid7(), "银行卡", "🏦", "CNY", 0, AccountType.BANK, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.accountDao().upsert(it) }
        val cat = CategoryEntity(uuid7(), null, CategoryType.EXPENSE, "餐饮", "🍜", 99, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.categoryDao().upsert(it) }
        val id = repo.addTransfer(1000, "CNY", 1000, a.id, b.id, 1L, "转账")
        assertEquals(b.id, db.transactionDao().getById(id)!!.targetAccountId)
        repo.update(id, TxType.EXPENSE, 1000, "CNY", 1000, a.id, null, cat.id, 1L, "改为支出")
        val after = db.transactionDao().getById(id)!!
        assertNull(after.targetAccountId)                 // 目标账户清空，互斥字段不残留
        assertEquals(cat.id, after.categoryId)
    }

    @Test
    fun canSave_transfer_rejects_cross_currency() = runTest(dispatcher) {
        // uiState 是 WhileSubscribed——挂一个热收集者让上游激活，.value 才会随动作刷新（同 RecordViewModelTest）
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        val cny = AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.accountDao().upsert(it) }
        val usd = AccountEntity(uuid7(), "美元现金", "💵", "USD", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev).also { db.accountDao().upsert(it) }
        val id = repo.addExpense(2500, "CNY", cny.id, null, 1_700_000_000_000, "午饭")
        vm.load(id, onMissing = {})
        vm.uiState.first { it.loaded }                    // 等预填完成（Room 后台线程，advanceUntilIdle 推不动）
        vm.setType(TxType.TRANSFER)
        vm.selectTargetAccount(usd.id)                    // 编辑态切转账并选跨币种目标
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSave)
    }
}
