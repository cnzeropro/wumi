package org.zero.wumi.ui.fx

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.repo.CurrencyRepository

/**
 * 汇率管理 ViewModel 测试（v0.2 Task 8）：手改非法拒 / 自动拉取开关切换。
 *
 * in-memory db + allowMainThreadQueries，不挂 WumiDbCallback（无种子干扰，seedIfEmpty 显式调）；
 * fetch 注入假实现不触网；WorkManager 走 test-utils（setAutoSync 联动 applyAutoSync 需初始化，
 * 故屏蔽 WumiApplication 换普通 Application，与 FxAutoSyncTest 同法）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FxViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var db: WumiDatabase
    private lateinit var repo: CurrencyRepository
    private lateinit var vm: FxViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, WumiDatabase::class.java).allowMainThreadQueries().build()
        repo = CurrencyRepository(db, db.currencyDao(), db.outboxDao(), db.settingDao(),
            deviceId = "dev", fetch = { Result.success(mapOf("USD" to "7.20")) })
        runTest { repo.seedIfEmpty() }
        vm = FxViewModel(repo, context)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun updateRate_rejects_invalid_and_accepts_valid() = runTest(dispatcher) {
        advanceUntilIdle()
        // 非法（非数字 / 非正数）拒绝：返回 false，列表保持种子值 7.12
        assertFalse(vm.updateRate("USD", "not-a-number"))
        assertFalse(vm.updateRate("USD", "0"))
        val before = vm.state.first { s -> s.currencies.isNotEmpty() }
        assertEquals("7.12", before.currencies.first { it.code == "USD" }.rateToBase)
        // 合法改率：返回 true，列表随 Room Flow 刷新（后台线程重查询，挂起等 state 真正发出数据）
        assertTrue(vm.updateRate("USD", "7.5"))
        val after = vm.state.first { s -> s.currencies.first { it.code == "USD" }.rateToBase == "7.5" }
        assertEquals("7.5", after.currencies.first { it.code == "USD" }.rateToBase)
    }

    @Test
    fun autoSync_toggle_updates_local_state_and_persists() = runTest(dispatcher) {
        assertTrue(vm.autoSync.value) // 缺省开（init 的真实读另有兜底，缺省即一致）
        // join 等 setAutoSync 写库真正提交——advanceUntilIdle 推不动 Room 真实线程池，直接断言会与写库竞速
        vm.setAutoSync(false).join()
        assertFalse(vm.autoSync.value)
        assertFalse(repo.autoSyncEnabled())
        vm.setAutoSync(true).join()
        assertTrue(vm.autoSync.value)
        assertTrue(repo.autoSyncEnabled())
    }
}
