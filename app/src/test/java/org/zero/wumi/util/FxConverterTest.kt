package org.zero.wumi.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FxConverterTest {
    @Test fun basic_rounding_half_up() {
        assertEquals(712L, FxConverter.toBaseMinor(100, "7.12"))
        assertEquals(5L, FxConverter.toBaseMinor(100, "0.051")) // 5.1 → 5
        assertEquals(6L, FxConverter.toBaseMinor(100, "0.055")) // 5.5 → 6
    }

    @Test fun nine_digits_no_overflow() {
        // 999_999_999 × 9000 = 8_999_999_991_000（计划原稿误写 9_000_000_000_000）
        assertEquals(8_999_999_991_000L, FxConverter.toBaseMinor(999_999_999, "9000"))
    }

    @Test fun invalid_rate_throws() {
        // kotlin-test 未进测试依赖，按任务裁决用 runCatching 断言异常类型：
        // NumberFormatException 是 IllegalArgumentException 子类，语义等价
        listOf("0", "abc").forEach { rate ->
            val result = runCatching { FxConverter.toBaseMinor(100, rate) }
            assertTrue("rate=$rate", result.isFailure)
            assertTrue(
                "rate=$rate expected IllegalArgumentException",
                result.exceptionOrNull() is IllegalArgumentException,
            )
        }
    }

    @Test fun round_trip_error_at_most_one_cent() {
        // 往返性质：外币→本位币→换回，误差 ≤ 1。rate 采样域取 [1.0, 20.0)（计划原稿为
        // [0.001, 20.0)）：往返误差上界为 0.5/rate + 0.5，rate < 1 时数学上必然可 >1
        // （如 amount=13, rate=0.2 → 3 → 15，差 2），属性质本身不成立而非实现缺陷，
        // 故只在性质成立的域内采样；断言与样本量保持计划原样
        repeat(200) {
            val amount = Random.nextLong(1, 99_999_999)
            val rate = BigDecimal(Random.nextDouble(1.0, 20.0)).setScale(6, RoundingMode.HALF_UP)
            val base = FxConverter.toBaseMinor(amount, rate.toPlainString())
            val back = BigDecimal(base).divide(rate, 0, RoundingMode.HALF_UP).longValueExact()
            assertTrue("amount=$amount rate=$rate", abs(back - amount) <= 1)
        }
    }
}
