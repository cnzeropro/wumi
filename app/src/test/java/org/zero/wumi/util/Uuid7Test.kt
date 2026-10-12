package org.zero.wumi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Uuid7Test {
    @Test fun version_and_variant_bits_are_correct() {
        val u = uuid7(1_700_000_000_000)
        assertEquals('7', u[14])                      // version nibble
        assertTrue(u[19] in "89ab")                   // variant
    }
    @Test fun timestamp_ordered_ids_sort_lexicographically() {
        val a = uuid7(1_700_000_000_000)
        val b = uuid7(1_700_000_000_001)
        assertTrue(a < b)
    }
    @Test fun unique_within_same_milli() {
        val set = (1..1000).map { uuid7(1_700_000_000_000) }.toSet()
        assertEquals(1000, set.size)
    }
    @Test fun format_is_standard_uuid() {
        val u = uuid7(1_700_000_000_000)
        assertEquals(36, u.length)
        assertEquals(4, u.count { it == '-' })
    }
    @Test fun different_millis_give_different_ids() {
        assertNotEquals(uuid7(1L), uuid7(2L))
    }
}
