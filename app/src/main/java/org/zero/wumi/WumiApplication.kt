package org.zero.wumi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import org.zero.wumi.work.DailyBackupWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class WumiApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

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
    }
}
