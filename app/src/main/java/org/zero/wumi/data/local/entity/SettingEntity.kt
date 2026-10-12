package org.zero.wumi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 键值设置（deviceId 等持久化于此，spec §5.1）。 */
// @Serializable 供本地备份 JSON 导出/导入（spec §9.2），与 Room 注解共存无冲突
@Serializable
@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)
