package org.zero.wumi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 交易类型：支出/收入/转账。 */
object TxType {
    const val EXPENSE = 0
    const val INCOME = 1
    const val TRANSFER = 2
}

/** 交易来源：手动/导入/周期。 */
object TxSource {
    const val MANUAL = 0
    const val IMPORT = 1
    const val RECURRING = 2
}

// @Serializable 供本地备份 JSON 导出/导入（spec §9.2），与 Room 注解共存无冲突
@Serializable
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey override val id: String,
    val type: Int,
    val amountMinor: Long,                // 原币种最小单位
    val currency: String,
    val exchangeRate: String? = null,     // 对本位币汇率快照（Decimal 字符串）；本币记 "1"，历史数据可能为 null
    val baseAmountMinor: Long,            // 本位币金额（统计冗余，spec §5.3）
    val accountId: String,
    val targetAccountId: String? = null,  // 仅转账
    val categoryId: String? = null,       // 仅支出/收入
    val date: Long,                       // 交易发生时刻 epochMillis
    val note: String = "",
    val source: Int = TxSource.MANUAL,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
    override val deviceId: String,
) : SyncMeta
