package org.zero.wumi.data.local.entity

/** 同步基因（spec §5.1）。v0.1 落库，v0.3 同步引擎消费。 */
interface SyncMeta {
    /** 业务主键，UUIDv7（时间有序，客户端生成、离线可造）。 */
    val id: String

    /** 创建时刻 epochMillis。 */
    val createdAt: Long

    /** 最近修改时刻，LWW 冲突解决依据。 */
    val updatedAt: Long

    /** 软删除墓碑；null 表示未删除。 */
    val deletedAt: Long?

    /** 最后修改者设备标识。 */
    val deviceId: String
}
