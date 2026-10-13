package org.zero.wumi.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.zero.wumi.data.local.entity.CurrencyEntity

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies WHERE deletedAt IS NULL ORDER BY code")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE code = :code AND deletedAt IS NULL")
    suspend fun getByCode(code: String): CurrencyEntity?

    @Upsert
    suspend fun upsertAll(list: List<CurrencyEntity>)

    @Query("UPDATE currencies SET rateToBase = :rate, rateUpdatedAt = :at, updatedAt = :at WHERE code = :code")
    suspend fun updateRate(code: String, rate: String, at: Long)
}
