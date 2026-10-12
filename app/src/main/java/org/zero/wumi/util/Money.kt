package org.zero.wumi.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** 金额工具：一律 Long 最小单位（分），显示层格式化（spec §5.3 金额铁律）。 */
object Money {
    private val symbols = mapOf("CNY" to "¥", "JPY" to "¥", "USD" to "$", "EUR" to "€")

    fun format(minor: Long, currency: String): String {
        val sym = symbols[currency] ?: "$currency "
        val sign = if (minor < 0) "-" else ""
        return "$sign$sym${numericPart(minor, currency)}"
    }

    /** 纯数字形态（千分位 + decimalsOf 小数位，负号保留，无货币符号）——CSV 导出金额列用。 */
    fun formatNumeric(minor: Long, currency: String): String {
        val sign = if (minor < 0) "-" else ""
        return sign + numericPart(minor, currency)
    }

    private fun numericPart(minor: Long, currency: String): String {
        val decimals = decimalsOf(currency)
        val abs = kotlin.math.abs(minor)
        val fmtWhole = DecimalFormat("#,###", DecimalFormatSymbols(Locale.US))
        return if (decimals == 0) fmtWhole.format(abs)
        else {
            val whole = abs / pow10(decimals)
            val frac = abs % pow10(decimals)
            "${fmtWhole.format(whole)}." + frac.toString().padStart(decimals, '0')
        }
    }

    fun parseToMinor(text: String, decimals: Int): Long? {
        val cleaned = text.replace(",", "").trim().removePrefix("¥").removePrefix("$").trim()
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned).setScale(decimals, RoundingMode.HALF_UP)
                .movePointRight(decimals).longValueExact()
        } catch (e: ArithmeticException) { null } catch (e: NumberFormatException) { null }
    }

    fun decimalsOf(currency: String): Int = when (currency) {
        "JPY", "KRW", "VND" -> 0
        "BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND" -> 3
        else -> 2
    }

    private fun pow10(n: Int): Long = BigDecimal.TEN.pow(n).longValueExact()
}
