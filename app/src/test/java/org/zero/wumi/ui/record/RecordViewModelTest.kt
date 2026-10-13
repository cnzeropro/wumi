package org.zero.wumi.ui.record

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.zero.wumi.data.repo.CurrencyRepository
import org.zero.wumi.data.repo.TransactionRepository
import org.zero.wumi.util.uuid7

/** 记一笔 ViewModel 测试：金额状态机 + 保存持久化与清零 + canSave 门禁（spec §7.2）。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: WumiDatabase
    private lateinit var vm: RecordViewModel
    private lateinit var txRepo: TransactionRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
        txRepo = TransactionRepository(db, db.transactionDao(), db.outboxDao(), "dev-test")
        vm = RecordViewModel(
            txRepo,
            AccountRepository(db, "dev-test"),
            CategoryRepository(db, "dev-test"),
            CurrencyRepository(db, db.currencyDao(), db.outboxDao(), db.settingDao(), "dev-test"),
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    /** 普通 suspend 辅助函数（T5 模式）：种一个账户（币种默认 CNY），返回 id。 */
    private suspend fun seedAccount(currency: String = "CNY"): String =
        AccountEntity(uuid7(), "现金", "💵", currency, 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = "dev-test")
            .also { db.accountDao().upsert(it) }.id

    /** 普通 suspend 辅助函数：种一个支出分类，返回 id。 */
    private suspend fun seedExpenseCategory(): String =
        CategoryEntity(uuid7(), null, CategoryType.EXPENSE, "餐饮", "🍜", 99, createdAt = 1, updatedAt = 1, deviceId = "dev-test")
            .also { db.categoryDao().upsert(it) }.id

    /** uiState 是 WhileSubscribed——挂一个热收集者让上游激活，.value 才会随动作刷新（仅测试期）。 */
    private fun TestScope.keepUiStateHot() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
    }

    @Test
    fun amount_input_state_machine() = runTest(dispatcher) {
        keepUiStateHot()
        seedExpenseCategory()
        vm.uiState.first { it.categories.isNotEmpty() }   // 等 combine 首个发射（Room 后台线程，advanceUntilIdle 推不动）
        vm.inputDigit('1'); vm.inputDigit('2')
        vm.inputDigit('.'); vm.inputDigit('3'); vm.inputDigit('4')
        advanceUntilIdle()
        assertEquals("12.34", vm.uiState.value.amountText)
        vm.inputDigit('5')                                // 已两位小数，忽略
        advanceUntilIdle()
        assertEquals("12.34", vm.uiState.value.amountText)
        vm.backspace()
        advanceUntilIdle()
        assertEquals("12.3", vm.uiState.value.amountText)
    }

    @Test
    fun double_dot_ignored_and_leading_zeros() = runTest(dispatcher) {
        keepUiStateHot()
        seedExpenseCategory()
        vm.uiState.first { it.categories.isNotEmpty() }
        vm.inputDigit('0'); vm.inputDigit('0')            // 前导 0 只保留一个
        advanceUntilIdle()
        assertEquals("0", vm.uiState.value.amountText)
        vm.inputDigit('.'); vm.inputDigit('.')            // 第二个点忽略
        advanceUntilIdle()
        assertEquals("0.", vm.uiState.value.amountText)
    }

    @Test
    fun max_amount_cap_9_digits() = runTest(dispatcher) {
        keepUiStateHot()
        seedExpenseCategory()
        vm.uiState.first { it.categories.isNotEmpty() }
        repeat(9) { vm.inputDigit('9') }
        vm.inputDigit('9')                                // 第 10 位被忽略
        advanceUntilIdle()
        assertEquals("999999999", vm.uiState.value.amountText)
    }

    @Test
    fun save_expense_persists_and_resets() = runTest(dispatcher) {
        keepUiStateHot()
        val accId = seedAccount()
        vm.uiState.first { it.accounts.isNotEmpty() }
        advanceUntilIdle()
        vm.setType(TxType.EXPENSE)
        vm.inputDigit('2'); vm.inputDigit('5')
        vm.selectAccount(accId)
        val done = CompletableDeferred<String>()
        vm.save { done.complete(it) }
        advanceUntilIdle()
        // Room 事务在自身线程池执行，advanceUntilIdle 推不动——挂起等 onDone 真正回调
        val txId = done.await()
        assertTrue(txId.isNotEmpty())
        // 保存后清空，可直接记下一笔（挂起等 combine 重算真正发出，避免调度顺序竞态）
        assertEquals("", vm.uiState.first { it.amountText.isEmpty() }.amountText)
        assertEquals(1, db.transactionDao().observeRange(0, Long.MAX_VALUE).first().size)
    }

    @Test
    fun canSave_requires_amount_and_account() = runTest(dispatcher) {
        keepUiStateHot()
        vm.inputDigit('1')
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSave)             // 无账户（链路未热时初始值同为 false，语义一致）
        vm.selectAccount(seedAccount())
        val s = vm.uiState.first { it.canSave }           // 挂起等账户发射与重算
        assertTrue(s.canSave)
    }

    @Test
    fun canSave_transfer_rejects_same_target_as_source() = runTest(dispatcher) {
        keepUiStateHot()
        val acc = seedAccount()
        vm.uiState.first { it.accounts.isNotEmpty() }     // 等 combine 首个发射
        vm.setType(TxType.TRANSFER)
        vm.selectAccount(acc)
        vm.selectTargetAccount(acc)                       // 目标与转出相同
        vm.inputDigit('2'); vm.inputDigit('5')
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun canSave_transfer_rejects_cross_currency() = runTest(dispatcher) {
        keepUiStateHot()
        val usd = seedAccount("USD")
        val cny = seedAccount("CNY")
        vm.uiState.first { it.accounts.size >= 2 }        // 等 combine 首个发射
        vm.setType(TxType.TRANSFER)
        vm.selectAccount(usd)
        vm.selectTargetAccount(cny)                       // 目标币种 ≠ 源账户币种
        vm.inputDigit('1')
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun select_fx_account_initializes_rate() = runTest(dispatcher) {
        keepUiStateHot()
        // 汇率表种子（DefaultFx USD=7.12），与生产 WumiDbCallback 同源
        CurrencyRepository(db, db.currencyDao(), db.outboxDao(), db.settingDao(), "dev-test").seedIfEmpty()
        val usd = seedAccount("USD")
        vm.uiState.first { it.accounts.isNotEmpty() }
        vm.selectAccount(usd)
        assertEquals("7.12", vm.uiState.first { it.fxRate != null }.fxRate)
        // 本位币恒 null：切回 CNY 账户后 fxRate 清空
        val cny = seedAccount("CNY")
        vm.selectAccount(cny)
        assertEquals(null, vm.uiState.first { it.fxRate == null }.fxRate)
    }
}
