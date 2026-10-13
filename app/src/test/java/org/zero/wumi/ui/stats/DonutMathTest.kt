package org.zero.wumi.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** DonutMath 纯函数测试：扇区角度分配（0 值跳过、覆盖 360°）与点击命中反解。 */
class DonutMathTest {
    @Test
    fun angles_skip_zero_and_cover_360() {
        val a = DonutMath.angles(100, listOf(50, 0, 50))
        assertEquals(2, a.size)
        assertEquals(180f, a[1].second, 0.01f)
        assertEquals(360f, a.sumOf { it.second.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun slice_at_hits_expected_slice() {
        val idx = DonutMath.sliceAt(90f, listOf(50, 50))   // 90° 落第二扇
        assertEquals(1, idx)
        assertEquals(null, DonutMath.sliceAt(90f, listOf(100)))
    }

    @Test
    fun angles_total_zero_returns_empty() {
        assertEquals(emptyList<Pair<Float, Float>>(), DonutMath.angles(0, listOf(0, 0)))
    }
}
