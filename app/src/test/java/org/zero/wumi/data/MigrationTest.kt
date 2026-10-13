package org.zero.wumi.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.AccountType
import java.io.File

/**
 * 迁移回归：version 1→2（新增 currencies 表）。
 *
 * 不用 room-testing 的 MigrationTestHelper（schemas 目录未挂 test assets），而是手工按
 * app/schemas/org.zero.wumi.data.local.WumiDatabase/1.json 的 createSql 逐字建一个
 * version=1 的真实旧库，插入一行 v0.1 业务数据，再用与生产完全相同的开库路径
 * （Room.databaseBuilder + addMigrations）触发升库。断言必须真实：
 *  1. v0.1 账户行跨迁移全字段保全（Room 迁移后 onValidateSchema 校验整库 schema 等价，
 *     迁移 SQL 列序/类型与 v2 实体不符则开库即抛 IllegalStateException）；
 *  2. currencies 表存在且为空（getByCode 可查返回 null）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    /** 旧库中的 v0.1 样例账户，迁移后逐字段断言（数据类等值 = 全列保全）。 */
    private val seedAccount = AccountEntity(
        id = "acc-fix-1",
        name = "现金钱包",
        emoji = "💴",
        currency = "CNY",
        initialBalanceMinor = 100_00L,
        type = AccountType.CASH,
        creditLimitMinor = null,
        billingDay = null,
        includeInNet = true,
        sortOrder = 0,
        archivedAt = null,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_001L,
        deletedAt = null,
        deviceId = "dev-fix-01",
    )

    /** v1 建表与 room_master_table 语句：逐字取自 schemas/…/1.json（勿手改，升级 1.json 时同步）。 */
    private val v1SchemaSql = listOf(
        "CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `currency` TEXT NOT NULL, `initialBalanceMinor` INTEGER NOT NULL, `type` INTEGER NOT NULL, `creditLimitMinor` INTEGER, `billingDay` INTEGER, `includeInNet` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `archivedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `parentId` TEXT, `type` INTEGER NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `archivedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `type` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` TEXT, `baseAmountMinor` INTEGER NOT NULL, `accountId` TEXT NOT NULL, `targetAccountId` TEXT, `categoryId` TEXT, `date` INTEGER NOT NULL, `note` TEXT NOT NULL, `source` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `deviceId` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `outbox` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entityType` TEXT NOT NULL, `recordId` TEXT NOT NULL, `queuedAt` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e6d466a07a6640c1101b6951473ca757')",
    )

    @Test
    fun migrate_1_to_2_preservesV01Data_andCreatesEmptyCurrencies() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // 纳米时间戳命名，避免用例间或本地残留库文件互相污染
        val name = "migration-test-${System.nanoTime()}.db"
        try {
            buildV1Database(ctx, name)

            val db = Room.databaseBuilder(ctx, WumiDatabase::class.java, name)
                .addMigrations(WumiDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
            try {
                // 升库成功且版本落位 2（开库本身即含 Room 的迁移后整库 schema 校验）
                assertEquals(2, db.openHelper.writableDatabase.version)
                // v0.1 数据保全：旧账户行原样查回（数据类等值 = 全部 15 列一致）
                assertEquals(seedAccount, db.accountDao().getById(seedAccount.id))
                // currencies 表已建且为空：可查询、无行
                assertNull(db.currencyDao().getByCode("USD"))
                assertTrue(db.currencyDao().observeAll().first().isEmpty())
            } finally {
                runCatching { db.close() }
            }
        } finally {
            dbFiles(ctx, name).forEach { it.delete() }
        }
    }

    /** 用 FrameworkSQLiteOpenHelperFactory 以 version=1 手工建真实旧库文件并种入样例账户。 */
    private fun buildV1Database(ctx: Context, name: String) {
        ctx.getDatabasePath(name).parentFile?.mkdirs()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        v1SchemaSql.forEach(db::execSQL)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            helper.writableDatabase.execSQL(
                // 列顺序与 v1 accounts 表严格一致
                "INSERT INTO accounts (id, name, emoji, currency, initialBalanceMinor, type, creditLimitMinor, billingDay, includeInNet, sortOrder, archivedAt, createdAt, updatedAt, deletedAt, deviceId) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(
                    seedAccount.id, seedAccount.name, seedAccount.emoji, seedAccount.currency,
                    seedAccount.initialBalanceMinor, seedAccount.type, seedAccount.creditLimitMinor,
                    seedAccount.billingDay, if (seedAccount.includeInNet) 1 else 0, seedAccount.sortOrder,
                    seedAccount.archivedAt, seedAccount.createdAt, seedAccount.updatedAt,
                    seedAccount.deletedAt, seedAccount.deviceId,
                ),
            )
        } finally {
            runCatching { helper.close() }
        }
    }

    /** 库主文件及可能的 WAL/journal 伴生文件。 */
    private fun dbFiles(ctx: Context, name: String): List<File> =
        listOf("", "-wal", "-shm", "-journal").map { File(ctx.getDatabasePath(name).absolutePath + it) }
}
