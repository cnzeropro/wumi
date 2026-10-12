package org.zero.wumi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.zero.wumi.data.local.entity.AccountEntity

@Dao
interface AccountDao {
    // 归档账户一并隐藏：v0.1 无归档管理页，列表只展示可用账户（spec §9.2 删除保护）
    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL AND archivedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    // 备份全量查询：含软删墓碑与归档行（spec §9.2 备份必须全量）
    @Query("SELECT * FROM accounts")
    suspend fun allForBackup(): List<AccountEntity>

    @Upsert
    suspend fun upsert(e: AccountEntity)

    @Query("UPDATE accounts SET deletedAt = :at, updatedAt = :at, deviceId = :device WHERE id = :id")
    suspend fun softDelete(id: String, at: Long, device: String)
}
