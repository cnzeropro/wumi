package org.zero.wumi.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.TransactionEntity

/** 交易及其关联引用（账户/对方账户/分类），供明细页一次取全。 */
data class TxWithRefs(
    @Embedded val tx: TransactionEntity,
    @Relation(parentColumn = "accountId", entityColumn = "id") val account: AccountEntity?,
    @Relation(parentColumn = "targetAccountId", entityColumn = "id") val targetAccount: AccountEntity?,
    @Relation(parentColumn = "categoryId", entityColumn = "id") val category: CategoryEntity?,
)

/** 月度汇总（本位币口径，单位分）。 */
data class MonthSums(val incomeMinor: Long, val expenseMinor: Long)

@Dao
interface TransactionDao {
    @Transaction
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND date >= :from AND date < :to ORDER BY date DESC, createdAt DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TxWithRefs>>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 1 THEN baseAmountMinor ELSE 0 END),0) AS incomeMinor, " +
            "COALESCE(SUM(CASE WHEN type = 0 THEN baseAmountMinor ELSE 0 END),0) AS expenseMinor " +
            "FROM transactions WHERE deletedAt IS NULL AND date >= :from AND date < :to",
    )
    fun sumsInRange(from: Long, to: Long): Flow<MonthSums>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Upsert
    suspend fun insert(e: TransactionEntity)

    @Query("UPDATE transactions SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    // 转账计入双方流水（in/out 各算一次）：对方账户侧按 targetAccountId 命中，
    // 供「账户下有交易只能归档」（spec §9.2）等口径使用
    @Query("SELECT COUNT(*) FROM transactions WHERE (accountId = :accountId OR targetAccountId = :accountId) AND deletedAt IS NULL")
    suspend fun countByAccount(accountId: String): Int
}
