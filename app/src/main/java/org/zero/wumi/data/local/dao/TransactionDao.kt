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

/**
 * 分类聚合行（本位币口径，单位分），供环形图切片。
 * LEFT JOIN 分类表且不过滤 c.deletedAt：软删/缺失分类的历史交易仍入图，此时 emoji/name 可能为 null，由 UI 兜底显示。
 */
data class CategorySum(
    val emoji: String?,
    val name: String?,
    val categoryId: String?,
    val total: Long,
)

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

    /** 分类聚合（环形图切片）：按 categoryId 汇总 baseAmountMinor 降序；软删分类不过滤（历史交易仍入图）。 */
    @Query(
        "SELECT c.emoji AS emoji, c.name AS name, t.categoryId AS categoryId, SUM(t.baseAmountMinor) AS total " +
            "FROM transactions t LEFT JOIN categories c ON c.id = t.categoryId " +
            "WHERE t.date >= :from AND t.date < :to AND t.deletedAt IS NULL AND t.type = :type AND t.categoryId IS NOT NULL " +
            "GROUP BY t.categoryId ORDER BY total DESC",
    )
    fun sumByCategory(from: Long, to: Long, type: Int): Flow<List<CategorySum>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    /** 单笔交易连同账户/分类引用一次取全（含已软删，由调用方过滤）。 */
    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getByIdWithRefs(id: String): TxWithRefs?

    // 备份全量查询：含软删墓碑（spec §9.2 备份必须全量）
    @Query("SELECT * FROM transactions")
    suspend fun allForBackup(): List<TransactionEntity>

    @Upsert
    suspend fun insert(e: TransactionEntity)

    @Query("UPDATE transactions SET deletedAt = :at, updatedAt = :at, deviceId = :device WHERE id = :id")
    suspend fun softDelete(id: String, at: Long, device: String)

    // 转账计入双方流水（in/out 各算一次）：对方账户侧按 targetAccountId 命中，
    // 供「账户下有交易只能归档」（spec §9.2）等口径使用
    @Query("SELECT COUNT(*) FROM transactions WHERE (accountId = :accountId OR targetAccountId = :accountId) AND deletedAt IS NULL")
    suspend fun countByAccount(accountId: String): Int
}
