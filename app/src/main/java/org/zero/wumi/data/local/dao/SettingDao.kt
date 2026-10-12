package org.zero.wumi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.zero.wumi.data.local.entity.SettingEntity

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM settings WHERE key = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(e: SettingEntity)

    // 备份全量查询（spec §9.2 备份必须全量）
    @Query("SELECT * FROM settings")
    suspend fun allForBackup(): List<SettingEntity>
}
