package org.zero.wumi.data.fx

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.repo.CurrencyRepository

/**
 * CurrencyRepository 测试（v0.2 多币种数据层）。
 *
 * in-memory db + allowMainThreadQueries，不挂 WumiDbCallback（无种子干扰，seedIfEmpty 显式调）；
 * fetch 注入假实现，不触网。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CurrencyRepositoryTest {
    private lateinit var db: WumiDatabase
    private lateinit var repo: CurrencyRepository
    private var fakeFetch: () -> Result<Map<String, String>> = { Result.success(mapOf("USD" to "7.20")) }

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = CurrencyRepository(db, db.currencyDao(), db.outboxDao(), db.settingDao(),
            deviceId = "dev", fetch = { fakeFetch() })
        runTest { repo.seedIfEmpty() }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun updateRate_rejects_zero_and_queues_outbox() = runTest {
        assertFalse(repo.updateRate("USD", "0"))
        assertFalse(repo.updateRate("USD", "not-a-number"))
        assertTrue(repo.updateRate("USD", "7.5"))
        val entry = db.outboxDao().pending().last { it.entityType == "currency" }
        assertEquals("USD", entry.recordId)
    }

    @Test
    fun refreshAll_updates_rates_and_counts() = runTest {
        val n = repo.refreshAll().getOrThrow()
        assertEquals(1, n)
        assertEquals("7.20", repo.getByCode("USD")?.rateToBase)
    }

    @Test
    fun refreshAll_failure_returns_failure_and_touches_nothing() = runTest {
        fakeFetch = { Result.failure(java.io.IOException("offline")) }
        assertTrue(repo.refreshAll().isFailure)
        assertEquals("7.12", repo.getByCode("USD")?.rateToBase)
    }

    @Test
    fun autoSync_default_on_and_toggle() = runTest {
        assertTrue(repo.autoSyncEnabled())
        repo.setAutoSync(false)
        assertFalse(repo.autoSyncEnabled())
        repo.setAutoSync(true)
        assertTrue(repo.autoSyncEnabled())
    }

    @Test
    fun seedIfEmpty_seeds_21_currencies_and_is_idempotent() = runTest {
        // setup 里的 seedIfEmpty 已从空表种入全部 21 币种，CNY 恒为基准 1
        val all = db.currencyDao().observeAll().first()
        assertEquals(21, all.size)
        assertEquals("1", all.first { it.code == "CNY" }.rateToBase)
        // 幂等：再次调用不得覆盖已有种子（USD 保持缺省 7.12，而非 fetch 假值）
        repo.seedIfEmpty()
        assertEquals("7.12", repo.getByCode("USD")?.rateToBase)
        assertEquals(21, db.currencyDao().observeAll().first().size)
    }
}
