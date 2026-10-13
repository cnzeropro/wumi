package org.zero.wumi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 币种（v0.2 多币种）。汇率字符串存 1 外币 = ? 本位币（BigDecimal 解析，禁浮点）。 */
@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val code: String,         // ISO 4217
    val name: String,
    val symbol: String,
    val decimals: Int,                    // 最小单位位数（USD=2、JPY=0）
    val rateToBase: String,               // 1 外币 = ? CNY
    val rateUpdatedAt: Long,
    override val id: String,              // 同步基因：业务主键 UUIDv7（code 为本地主键）
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
    override val deviceId: String,
) : SyncMeta
