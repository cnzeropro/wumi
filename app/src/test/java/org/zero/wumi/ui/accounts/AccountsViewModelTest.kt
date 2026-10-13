package org.zero.wumi.ui.accounts

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.AccountType
import org.zero.wumi.data.repo.AccountRepository
import org.zero.wumi.data.repo.CurrencyRepository
import org.zero.wumi.data.repo.TransactionRepository

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: WumiDatabase
    private lateinit var repo: AccountRepository
    private lateinit var vm: AccountsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
        repo = AccountRepository(db, "dev-test")
        vm = AccountsViewModel(repo, CurrencyRepository(db, db.currencyDao(), db.outboxDao(), db.settingDao(), "dev-test"))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun save_then_list_shows_account() = runTest(dispatcher) {
        vm.save(
            id = null, name = "招行卡", emoji = "💳", currency = "CNY",
            initialBalanceMinor = 123_4500, type = AccountType.BANK,
            creditLimitMinor = null, billingDay = null, includeInNet = true,
        )
        advanceUntilIdle()
        // Room Flow 在自身线程池重查询，advanceUntilIdle 推不动后台线程——
        // 挂起等 state 真正发出数据，避免竞态假失败
        val accounts = vm.state.first { it.accounts.isNotEmpty() }.accounts
        assertEquals("招行卡", accounts.first().name)
    }

    @Test
    fun account_with_tx_archives_not_deletes() = runTest(dispatcher) {
        // 先建账户并记一笔，再走删除保护 → 应归档而非墓碑删除
        vm.save(null, "现金", "💵", "CNY", 0, AccountType.CASH, null, null, true)
        advanceUntilIdle()
        val accId = vm.state.first { it.accounts.isNotEmpty() }.accounts.first().id
        TransactionRepository(db, db.transactionDao(), db.outboxDao(), "dev-test")
            .addExpense(100, "CNY", 100, accId, null, 1_700_000_000_000, "")
        // 归档判定在仓库层（跨协程取 ViewModel 结果不可靠），直接断言仓库返回值
        val result = repo.archiveOrDelete(accId)
        advanceUntilIdle()
        assertEquals("archived", result)
        // 列表隐藏归档账户
        val accounts = vm.state.first { s -> s.accounts.none { it.id == accId } }.accounts
        assertTrue(accounts.none { it.id == accId })
    }
}
