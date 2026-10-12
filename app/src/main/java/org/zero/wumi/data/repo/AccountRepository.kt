package org.zero.wumi.data.repo

import androidx.room.withTransaction
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.OutboxEntity
import org.zero.wumi.di.DeviceId
import org.zero.wumi.util.uuid7
import javax.inject.Inject
import javax.inject.Singleton

/** 账户仓库：业务表写入与 outbox 入队始终同一 Room 事务（spec §6.3）。 */
@Singleton
class AccountRepository @Inject constructor(
    private val db: WumiDatabase,
    @DeviceId private val deviceId: String,
) {
    fun observeAll() = db.accountDao().observeAll()

    /** 新建或更新（id 非空即更新），写账户表并入队 outbox，同一事务。 */
    suspend fun save(
        id: String?,
        name: String,
        emoji: String,
        currency: String,
        initialBalanceMinor: Long,
        type: Int,
        creditLimitMinor: Long? = null,
        billingDay: Int? = null,
        includeInNet: Boolean = true,
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val old = id?.let { db.accountDao().getById(it) }
            val e = (old ?: AccountEntity(uuid7(now), name, emoji, currency, initialBalanceMinor, type, creditLimitMinor, billingDay, includeInNet, 0, null, now, now, null, deviceId))
                .copy(name = name, emoji = emoji, currency = currency, initialBalanceMinor = initialBalanceMinor, type = type, creditLimitMinor = creditLimitMinor, billingDay = billingDay, includeInNet = includeInNet, updatedAt = now, deviceId = deviceId)
            db.accountDao().upsert(e)
            db.outboxDao().enqueue(OutboxEntity(entityType = "account", recordId = e.id, queuedAt = now))
        }
    }

    /**
     * 删除保护（spec §9.2）：账户下有交易→只能归档（写 archivedAt）；
     * 无交易→软删除（写墓碑）。入队 outbox 同事务。返回实际操作："archived" / "deleted"。
     */
    suspend fun archiveOrDelete(id: String): String {
        val now = System.currentTimeMillis()
        var archived = false
        db.withTransaction {
            // 计数与写入同事务，避免检查-执行间隙被并发写入穿透
            if (db.transactionDao().countByAccount(id) > 0) {
                db.accountDao().upsert(db.accountDao().getById(id)!!.copy(archivedAt = now, updatedAt = now, deviceId = deviceId))
                archived = true
            } else {
                db.accountDao().softDelete(id, now, deviceId)
            }
            db.outboxDao().enqueue(OutboxEntity(entityType = "account", recordId = id, queuedAt = now))
        }
        return if (archived) "archived" else "deleted"
    }
}
