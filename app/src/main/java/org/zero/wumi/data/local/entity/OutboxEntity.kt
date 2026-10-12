package org.zero.wumi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 待推送队列（spec §6.3 outbox 模式；v0.1 只写入不消费，同步引擎 v0.3 接管）。 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val entityType: String,   // "account" | "category" | "transaction"
    val recordId: String,
    val queuedAt: Long,
)
