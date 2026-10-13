package org.zero.wumi.work

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
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
import org.zero.wumi.data.repo.CurrencyRepository
import java.util.concurrent.TimeUnit

/**
 * applyAutoSync 开关联动测试（v0.2 Task 7）：开→入队每周任务；关→取消。
 *
 * 用普通 Application（屏蔽 WumiApplication 的真实注册，避免异步注册竞态干扰断言）；
 * WorkManager 走 test-utils 同步执行器；周期与约束经 WorkSpec 落库值断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FxAutoSyncTest {
    private lateinit var context: Context
    private lateinit var db: WumiDatabase
    private lateinit var repo: CurrencyRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, WumiDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = CurrencyRepository(db, db.currencyDao(), db.outboxDao(), db.settingDao(),
            deviceId = "dev", fetch = { Result.success(mapOf("USD" to "7.20")) })
        runTest { repo.seedIfEmpty() }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun applyAutoSync_enqueues_weekly_connected_work_when_enabled() = runTest {
        repo.setAutoSync(true)
        applyAutoSync(context, repo)

        val wm = WorkManager.getInstance(context)
        val infos = wm.getWorkInfosForUniqueWork(FxSyncWorker.WORK_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)

        // 落库 WorkSpec 断言周期 7 天 + 需网络（unique 周期任务的 spec id 即任务名）
        val specDao = (wm as WorkManagerImpl).workDatabase.workSpecDao()
        val spec = specDao.getWorkSpec(FxSyncWorker.WORK_NAME)
            ?: specDao.getWorkSpec(infos[0].id.toString())!!
        assertEquals(TimeUnit.DAYS.toMillis(7), spec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
    }

    @Test
    fun applyAutoSync_cancels_work_when_disabled() = runTest {
        repo.setAutoSync(true)
        applyAutoSync(context, repo) // 先入队
        repo.setAutoSync(false)
        applyAutoSync(context, repo) // 关→取消

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(FxSyncWorker.WORK_NAME).get()
        assertTrue(infos.isEmpty() || infos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
