package org.zero.wumi.util

import java.math.BigDecimal
import java.math.RoundingMode

/** 外币→本位币唯一换算出口（金额铁律 spec §5.3）。 */
object FxConverter {
    /**
     * 外币最小单位金额乘汇率换算为本位币最小单位，四舍五入（HALF_UP）取整。
     *
     * @param amountMinor 外币最小单位金额
     * @param rateToBase 汇率文本（BigDecimal 解析），历史账单用当笔快照，不随汇率波动重算
     * @return 本位币最小单位金额
     * @throws IllegalArgumentException 汇率非法（含无法解析或非正数）
     */
    fun toBaseMinor(amountMinor: Long, rateToBase: String): Long {
        val rate = BigDecimal(rateToBase)
        require(rate > BigDecimal.ZERO) { "invalid fx rate: $rateToBase" }
        return BigDecimal(amountMinor).multiply(rate)
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
}
