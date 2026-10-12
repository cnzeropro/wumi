package org.zero.wumi.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import org.zero.wumi.data.backup.BackupManager

/** 每日滚动备份任务（spec §9.2）：备份一次 + 滚动保留 7 份；失败退避重试。 */
@HiltWorker
class DailyBackupWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = try {
        backups.backupNow()
        backups.rotate()
        Result.success()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "daily backup failed, attempt $runAttemptCount", e)
        Result.retry()
    }

    companion object {
        /** 唯一周期任务名：重复注册以 KEEP 策略幂等。 */
        const val NAME = "daily-backup"

        private const val TAG = "DailyBackupWorker"
    }
}
