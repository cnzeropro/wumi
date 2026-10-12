package org.zero.wumi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.AccountType
import org.zero.wumi.util.uuid7

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountDaoTest {
    private lateinit var db: WumiDatabase
    private val dev = "dev-test"

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun account(name: String = "现金") = AccountEntity(
        id = uuid7(), name = name, emoji = "💵", currency = "CNY",
        initialBalanceMinor = 10000, type = AccountType.CASH,
        createdAt = 1L, updatedAt = 1L, deviceId = dev,
    )

    @Test
    fun upsert_then_observe() = runTest {
        db.accountDao().upsert(account())
        assertEquals(1, db.accountDao().observeAll().first().size)
    }

    @Test
    fun softDelete_hides_from_observe_but_row_remains() = runTest {
        val a = account(); db.accountDao().upsert(a)
        db.accountDao().softDelete(a.id, 99L, dev)
        assertTrue(db.accountDao().observeAll().first().isEmpty())
        assertNotNull(db.accountDao().getById(a.id))       // 墓碑仍在
        assertEquals(99L, db.accountDao().getById(a.id)?.deletedAt)
    }

    @Test
    fun archived_hides_from_observe_but_row_remains() = runTest {
        // 删除保护：有交易的账户归档（写 archivedAt），主列表不再展示但行保留
        val a = account(); db.accountDao().upsert(a)
        db.accountDao().upsert(a.copy(archivedAt = 99L, updatedAt = 99L))
        assertTrue(db.accountDao().observeAll().first().none { it.id == a.id })
        assertNotNull(db.accountDao().getById(a.id))       // 归档行仍在
        assertEquals(99L, db.accountDao().getById(a.id)?.archivedAt)
    }
}
