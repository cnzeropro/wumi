package org.zero.wumi.data.fx

import org.junit.Assert.assertEquals
import org.junit.Test

class ErApiParserTest {
    private val sample = """{"result":"success","rates":{"USD":7.12,"JPY":0.048,"BAD":-1,"XXX":0,"EUR":"7.85","CNY":8.9}}"""

    @Test fun parse_success_filters_allowed_and_invalid() {
        val m = ErApiParser.parse(sample, setOf("USD", "JPY", "BAD", "XXX", "EUR", "GBP"))
        assertEquals(mapOf("USD" to "7.12", "JPY" to "0.048", "EUR" to "7.85"), m)
    }

    @Test fun parse_excludes_non_whitelisted_codes() {
        // CNY 数值合法但不在白名单 → 必须被过滤（防止删除过滤行后测试仍绿的覆盖缺口）
        val m = ErApiParser.parse(sample, setOf("USD", "JPY", "EUR"))
        assertEquals(mapOf("USD" to "7.12", "JPY" to "0.048", "EUR" to "7.85"), m)
    }

    @Test fun parse_failure_returns_empty() {
        // 计划原稿 emptyMap() 无法推断 K/V，显式标注类型参数（语义不变）
        assertEquals(emptyMap<String, String>(), ErApiParser.parse("""{"result":"error"}""", setOf("USD")))
    }

    @Test fun parse_garbage_returns_empty() {
        assertEquals(emptyMap<String, String>(), ErApiParser.parse("not json", setOf("USD")))
    }
}
