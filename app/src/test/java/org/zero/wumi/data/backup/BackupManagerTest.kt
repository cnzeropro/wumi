package org.zero.wumi.data.backup

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
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.AccountType
import org.zero.wumi.util.uuid7
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupManagerTest {
    private lateinit var db: WumiDatabase
    private lateinit var dir: File
    private lateinit var mgr: BackupManager
    private val dev = "dev-test"

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WumiDatabase::class.java).allowMainThreadQueries().build()
        dir = File(ctx.filesDir, "backups").apply { mkdirs() }
        mgr = BackupManager(db, dir)
    }

    @After
    fun teardown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun backup_creates_valid_json_no_tmp_left() = runTest {
        db.accountDao().upsert(
            AccountEntity(
                id = uuid7(), name = "现金", emoji = "💵", currency = "CNY",
                initialBalanceMinor = 500, type = AccountType.CASH,
                createdAt = 1L, updatedAt = 1L, deviceId = dev,
            ),
        )
        val f = mgr.backupNow()
        assertTrue(f.exists())
        assertTrue(f.name.startsWith("wumi-backup-"))
        assertTrue(f.name.endsWith(".json"))
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })   // 原子 rename 完成
        val text = f.readText()
        assertTrue(text.contains("accounts"))
        assertTrue(text.contains("现金"))
    }

    @Test
    fun rotate_keeps_seven() = runTest {
        repeat(10) { i -> File(dir, "wumi-backup-2026010${i}-000000.json").writeText("{}") }
        mgr.rotate()
        assertEquals(7, dir.listFiles()!!.size)
        assertFalse(File(dir, "wumi-backup-20260100-000000.json").exists())  // 最旧的被删
    }

    @Test
    fun restore_recovers_full_state() = runTest {
        db.accountDao().upsert(
            AccountEntity(
                id = uuid7(), name = "恢复我", emoji = "💵", currency = "CNY",
                initialBalanceMinor = 0, type = AccountType.CASH,
                createdAt = 1L, updatedAt = 1L, deviceId = dev,
            ),
        )
        val f = mgr.backupNow()
        db.clearAllTables()
        assertEquals(0, db.accountDao().observeAll().first().size)
        mgr.restoreFrom(f)
        assertEquals(1, db.accountDao().observeAll().first().size)
        assertEquals("恢复我", db.accountDao().observeAll().first()[0].name)
    }
}
