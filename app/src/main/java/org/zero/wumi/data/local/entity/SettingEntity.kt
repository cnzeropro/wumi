package org.zero.wumi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 键值设置（deviceId 等持久化于此，spec §5.1）。 */
@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)
