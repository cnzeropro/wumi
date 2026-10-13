package org.zero.wumi.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.zero.wumi.data.repo.CurrencyRepository

/** 每周自动拉取汇率（v0.2，可关）：刷新失败不重试堆积，等下个周期。 */
@HiltWorker
class FxSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val currencyRepo: CurrencyRepository,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = try {
        currencyRepo.refreshAll()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "fx refresh failed, attempt $runAttemptCount", e)
        Result.success()
    }

    companion object {
        /** 唯一周期任务名：开关切换以 KEEP 策略幂等。 */
        const val WORK_NAME = "fx-weekly"

        private const val TAG = "FxSyncWorker"
    }
}

/** fx 每周任务请求：7 天周期 + 需网络。 */
private fun fxWeeklyRequest(): PeriodicWorkRequest =
    PeriodicWorkRequestBuilder<FxSyncWorker>(7, TimeUnit.DAYS)
        .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        )
        .build()

/** 按开关状态入队/取消每周汇率任务。须在协程内调用（读开关是 suspend）。 */
suspend fun applyAutoSync(context: Context, repo: CurrencyRepository) {
    val wm = WorkManager.getInstance(context)
    if (repo.autoSyncEnabled()) {
        wm.enqueueUniquePeriodicWork(FxSyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, fxWeeklyRequest())
    } else {
        wm.cancelUniqueWork(FxSyncWorker.WORK_NAME)
    }
}
