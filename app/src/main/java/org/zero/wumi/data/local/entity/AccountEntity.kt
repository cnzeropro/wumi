package org.zero.wumi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 账户类型：现金/储蓄卡/支付宝/微信/信用卡/其他。 */
object AccountType {
    const val CASH = 0
    const val BANK = 1
    const val ALIPAY = 2
    const val WECHAT = 3
    const val CREDIT = 4
    const val OTHER = 5
}

// @Serializable 供本地备份 JSON 导出/导入（spec §9.2），与 Room 注解共存无冲突
@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val emoji: String,
    val currency: String,                 // ISO 4217
    val initialBalanceMinor: Long,        // 期初余额（分）
    val type: Int,
    val creditLimitMinor: Long? = null,   // 信用卡额度
    val billingDay: Int? = null,          // 账单日 1-31
    val includeInNet: Boolean = true,     // 是否计入净资产
    val sortOrder: Int = 0,
    val archivedAt: Long? = null,         // 归档（删除保护：有交易只能归档）
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
    override val deviceId: String,
) : SyncMeta
