package org.zero.wumi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zero.wumi.data.local.DefaultFx

class DefaultFxTest {
    @Test
    fun defs_contains_21_and_cny_is_one() {
        val defs = DefaultFx.defs(now = 1L, deviceId = "dev")
        assertEquals(21, defs.size)
        val cny = defs.first { it.code == "CNY" }
        assertEquals("1", cny.rateToBase)
        assertTrue(defs.none { it.rateToBase.toDouble() <= 0.0 })
        assertTrue(defs.all { it.deletedAt == null && it.deviceId == "dev" })
    }
}
