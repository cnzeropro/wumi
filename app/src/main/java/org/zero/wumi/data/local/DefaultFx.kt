package org.zero.wumi.data.local

import org.zero.wumi.data.local.entity.CurrencyEntity
import org.zero.wumi.util.uuid7

/**
 * 预置币种种子（v0.2 多币种）：CNY 基准 + 20 常用外币的缺省汇率。
 *
 * 汇率为硬编码缺省值（1 外币 = ? CNY），仅保证首开可记外币账、金额不离谱；
 * 后续由周期拉取或用户手改维护，种子不做覆盖（INSERT OR IGNORE）。
 * 用 linkedMapOf 保持声明顺序，CNY 恒居首位。
 */
object DefaultFx {
    private val rates = linkedMapOf(
        "CNY" to Triple("人民币", "¥", "1"),
        "USD" to Triple("美元", "$", "7.12"),
        "EUR" to Triple("欧元", "€", "7.85"),
        "JPY" to Triple("日元", "JP¥", "0.048"),
        "HKD" to Triple("港币", "HK$", "0.91"),
        "TWD" to Triple("新台币", "NT$", "0.22"),
        "KRW" to Triple("韩元", "₩", "0.0052"),
        "SGD" to Triple("新加坡元", "S$", "5.32"),
        "THB" to Triple("泰铢", "฿", "0.21"),
        "MYR" to Triple("林吉特", "RM", "1.61"),
        "VND" to Triple("越南盾", "₫", "0.00028"),
        "AUD" to Triple("澳元", "A$", "4.68"),
        "GBP" to Triple("英镑", "£", "9.05"),
        "CHF" to Triple("瑞士法郎", "Fr", "8.21"),
        "CAD" to Triple("加元", "C$", "5.21"),
        "NZD" to Triple("纽元", "NZ$", "4.28"),
        "INR" to Triple("印度卢比", "₹", "0.085"),
        "RUB" to Triple("卢布", "₽", "0.078"),
        "BRL" to Triple("雷亚尔", "R$", "1.32"),
        "EGP" to Triple("埃及镑", "E£", "0.147"),
        "SEK" to Triple("瑞典克朗", "kr", "0.68"),
    )

    /** 无小数位的币种（最小单位即 1 元），缺省 2 位。 */
    private val decimals = mapOf("JPY" to 0, "KRW" to 0, "VND" to 0)

    /** 生成全部 21 条种子币种，id 用 UUIDv7 保证同毫秒唯一（与分类种子同法）。 */
    fun defs(now: Long, deviceId: String): List<CurrencyEntity> = rates.map { (code, def) ->
        CurrencyEntity(
            code, def.first, def.second, decimals[code] ?: 2, def.third,
            rateUpdatedAt = now, id = uuid7(now), createdAt = now, updatedAt = now,
            deletedAt = null, deviceId = deviceId,
        )
    }
}
