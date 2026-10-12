package org.zero.wumi.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.zero.wumi.data.local.entity.OutboxEntity

@Dao
interface OutboxDao {
    @Insert
    suspend fun enqueue(e: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY seq")
    suspend fun pending(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE seq IN (:seqs)")
    suspend fun deleteBySeqs(seqs: List<Long>)
}
