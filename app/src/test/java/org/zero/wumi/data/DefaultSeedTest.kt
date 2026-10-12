package org.zero.wumi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zero.wumi.data.local.DefaultSeed
import org.zero.wumi.data.local.entity.CategoryType

class DefaultSeedTest {
    @Test
    fun seed_contains_expense_and_income() {
        val cs = DefaultSeed.categories(1L, "dev")
        assertEquals(DefaultSeed.expense.size + DefaultSeed.income.size, cs.size)
        assertTrue(cs.any { it.type == CategoryType.EXPENSE && it.name == "餐饮" })
        assertTrue(cs.any { it.type == CategoryType.INCOME && it.name == "工资" })
    }

    @Test
    fun all_ids_unique() {
        val cs = DefaultSeed.categories(1L, "dev")
        assertEquals(cs.size, cs.map { it.id }.toSet().size)
    }
}
