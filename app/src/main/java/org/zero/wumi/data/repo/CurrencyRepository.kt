package org.zero.wumi.data.repo

import androidx.room.withTransaction
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.zero.wumi.data.fx.FxRateFetcher
import org.zero.wumi.data.local.DefaultFx
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.dao.CurrencyDao
import org.zero.wumi.data.local.dao.OutboxDao
import org.zero.wumi.data.local.dao.SettingDao
import org.zero.wumi.data.local.entity.CurrencyEntity
import org.zero.wumi.data.local.entity.OutboxEntity
import org.zero.wumi.data.local.entity.SettingEntity
import org.zero.wumi.di.DeviceId

/**
 * 币种仓库（v0.2 多币种）。同步基因铁律：每次汇率写入（手改与拉取）都与
 * outbox 入队（entityType "currency"，recordId 为币种 code）在同一 Room 事务内；
 * settings 为本机偏好，不入 outbox。
 *
 * [fetch] 参数化以便测试注入假实现，生产用 [FxRateFetcher]。
 */
class CurrencyRepository(
    private val db: WumiDatabase,
    private val dao: CurrencyDao,
    private val outboxDao: OutboxDao,
    private val settingDao: SettingDao,
    @DeviceId private val deviceId: String,
    private val fetch: (Set<String>) -> Result<Map<String, String>> = FxRateFetcher::fetch,
) {
    fun observeAll(): Flow<List<CurrencyEntity>> = dao.observeAll()

    suspend fun getByCode(code: String): CurrencyEntity? = dao.getByCode(code)

    /** 表空则写入 [DefaultFx] 种子；生产路径由 WumiDbCallback 承担，此处供测试与降级兜底（种子不入 outbox）。 */
    suspend fun seedIfEmpty() {
        if (dao.observeAll().first().isNotEmpty()) return
        dao.upsertAll(DefaultFx.defs(System.currentTimeMillis(), deviceId))
    }

    /** 手改汇率：rate 须为 BigDecimal 合法且 >0；成功则更新并与 outbox 同事务入队。 */
    suspend fun updateRate(code: String, rate: String): Boolean {
        val d = runCatching { BigDecimal(rate) }.getOrNull() ?: return false
        if (d <= BigDecimal.ZERO) return false
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.updateRate(code, d.stripTrailingZeros().toPlainString(), now)
            outboxDao.enqueue(OutboxEntity(entityType = "currency", recordId = code, queuedAt = now))
        }
        return true
    }

    /** 拉取并刷新全部币种（allowed = [DefaultFx] 全部 code），返回成功更新的币种数；拉取失败零写入。 */
    suspend fun refreshAll(): Result<Int> = withContext(Dispatchers.IO) {
        // fetch 为阻塞 IO（HttpURLConnection），整体切 IO 调度器，避免调用方在 Main 卡 UI
        fetch(allCodes()).mapCatching { rates ->
            val now = System.currentTimeMillis()
            db.withTransaction {
                rates.forEach { (code, r) ->
                    dao.updateRate(code, r, now)
                    outboxDao.enqueue(OutboxEntity(entityType = "currency", recordId = code, queuedAt = now))
                }
            }
            rates.size
        }
    }

    /** 自动拉取开关（settings key autoFxRate），缺省开（非 "0" 即开）。 */
    suspend fun autoSyncEnabled(): Boolean = settingDao.get(AUTO_FX_KEY) != "0"

    suspend fun setAutoSync(enabled: Boolean) {
        settingDao.put(SettingEntity(AUTO_FX_KEY, if (enabled) "1" else "0"))
    }

    private fun allCodes(): Set<String> = DefaultFx.defs(0L, deviceId).mapTo(mutableSetOf()) { it.code }

    private companion object {
        const val AUTO_FX_KEY = "autoFxRate"
    }
}
