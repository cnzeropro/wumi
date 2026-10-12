package org.zero.wumi.data.repo

import androidx.room.withTransaction
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.OutboxEntity
import org.zero.wumi.di.DeviceId
import org.zero.wumi.util.uuid7
import javax.inject.Inject
import javax.inject.Singleton

/** 分类仓库：业务表写入与 outbox 入队始终同一 Room 事务（spec §6.3）。 */
@Singleton
class CategoryRepository @Inject constructor(
    private val db: WumiDatabase,
    @DeviceId private val deviceId: String,
) {
    fun observeAll() = db.categoryDao().observeAll()

    fun observeActive(type: Int) = db.categoryDao().observeActive(type)

    /** 新建或更新（id 非空即更新），写分类表并入队 outbox，同一事务。 */
    suspend fun save(id: String?, parentId: String?, type: Int, name: String, emoji: String, sortOrder: Int) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val old = id?.let { db.categoryDao().getById(it) }
            val e = (old ?: CategoryEntity(uuid7(now), parentId, type, name, emoji, sortOrder, null, now, now, null, deviceId))
                .copy(parentId = parentId, name = name, emoji = emoji, sortOrder = sortOrder, updatedAt = now, deviceId = deviceId)
            db.categoryDao().upsert(e)
            db.outboxDao().enqueue(OutboxEntity(entityType = "category", recordId = e.id, queuedAt = now))
        }
    }

    /** 归档分类（写 archivedAt，保留历史交易引用），入队 outbox 同事务。 */
    suspend fun archive(id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.categoryDao().upsert(db.categoryDao().getById(id)!!.copy(archivedAt = now, updatedAt = now, deviceId = deviceId))
            db.outboxDao().enqueue(OutboxEntity(entityType = "category", recordId = id, queuedAt = now))
        }
    }
}
