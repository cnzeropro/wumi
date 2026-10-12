package org.zero.wumi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.zero.wumi.data.local.entity.CategoryEntity

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY type, sortOrder, createdAt")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL AND archivedAt IS NULL AND type = :type ORDER BY sortOrder, createdAt")
    fun observeActive(type: Int): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    // 备份全量查询：含软删墓碑与归档行（spec §9.2 备份必须全量）
    @Query("SELECT * FROM categories")
    suspend fun allForBackup(): List<CategoryEntity>

    @Upsert
    suspend fun upsert(e: CategoryEntity)

    @Query("UPDATE categories SET deletedAt = :at, updatedAt = :at, deviceId = :device WHERE id = :id")
    suspend fun softDelete(id: String, at: Long, device: String)
}
