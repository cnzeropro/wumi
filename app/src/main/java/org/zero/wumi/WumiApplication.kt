package org.zero.wumi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.zero.wumi.data.repo.CurrencyRepository
import org.zero.wumi.work.DailyBackupWorker
import org.zero.wumi.work.applyAutoSync
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class WumiApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var currencyRepo: CurrencyRepository

    /** 进程级作用域：启动期一次性注册用，随进程消亡，无需显式取消。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // WorkManager 按需初始化（Manifest 已移除默认 InitializationProvider 注入），
    // 工厂换成 Hilt 提供的 HiltWorkerFactory 以支持 @HiltWorker 注入
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 每日滚动备份（spec §9.2）：KEEP 策略——已有排期则保留，避免重复注册改期
        val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyBackupWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        // 每周汇率拉取（v0.2，可关）：异步读开关后入队/取消，不阻塞 onCreate
        appScope.launch { applyAutoSync(this@WumiApplication, currencyRepo) }
    }
}
