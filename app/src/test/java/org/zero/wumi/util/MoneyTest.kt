package org.zero.wumi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun format_two_decimals() { assertEquals("¥1,234.56", Money.format(123456, "CNY")) }
    @Test fun format_zero() { assertEquals("¥0.00", Money.format(0, "CNY")) }
    @Test fun format_negative() { assertEquals("-¥1,234.56", Money.format(-123456, "CNY")) }
    @Test fun format_zero_decimals_yen() { assertEquals("¥1,235", Money.format(1235, "JPY")) }
    @Test fun formatNumeric_positive() { assertEquals("1,234.56", Money.formatNumeric(123456, "CNY")) }
    @Test fun formatNumeric_negative() { assertEquals("-25.00", Money.formatNumeric(-2500, "CNY")) }
    @Test fun parse_plain() { assertEquals(123456L, Money.parseToMinor("1234.56", 2)) }
    @Test fun parse_with_thousands_and_symbol() { assertEquals(123456L, Money.parseToMinor("1,234.56", 2)) }
    @Test fun parse_more_decimals_rounds_half_up() { assertEquals(1235L, Money.parseToMinor("12.345", 2)) }
    @Test fun parse_garbage_returns_null() { assertNull(Money.parseToMinor("abc", 2)) }
    @Test fun parse_empty_returns_null() { assertNull(Money.parseToMinor("", 2)) }
    @Test
    fun parse_negative_leading_sign() {
        // 格式输出 -¥1,234.56，粘贴回来也应可解析（v0.1 缺口）
        assertEquals(-123456L, Money.parseToMinor("-¥1,234.56", 2))
        assertEquals(-1235L, Money.parseToMinor("-1235", 0))
    }
}
