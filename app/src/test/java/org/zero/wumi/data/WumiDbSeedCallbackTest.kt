package org.zero.wumi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.WumiDbCallback
import java.io.File

/**
 * onOpen 种子回归测试：必须挂 [WumiDbCallback] 的 file-based 库，才能覆盖 Room 真实的
 * 首开回调路径。防回归点：种子事务若漏 `db.setTransactionSuccessful()`，
 * `endTransaction()` 必然回滚、categories 永远为 0，且无任何异常抛出——
 * 该缺陷曾因旧套件从未断言过首开种子存在而成为假阴性。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WumiDbSeedCallbackTest {

    /** 打开指定名称的 file-based 库；fresh=true 时先清掉库文件（含 WAL/journal 残留），保证走首开建库路径。 */
    private fun openDb(name: String, fresh: Boolean): WumiDatabase {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getDatabasePath(name).parentFile?.mkdirs()
        if (fresh) {
            dbFiles(ctx, name).forEach { it.delete() }
        }
        return Room.databaseBuilder(ctx, WumiDatabase::class.java, name)
            .addCallback(WumiDbCallback("seed-test-device"))
            .allowMainThreadQueries()
            .build()
    }

    /** 库主文件及可能的 WAL/journal 伴生文件。 */
    private fun dbFiles(ctx: Context, name: String): List<File> =
        listOf("", "-wal", "-shm", "-journal").map { File(ctx.getDatabasePath(name).absolutePath + it) }

    @Test
    fun seedInsertedOnFirstOpen_andIdempotent() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // 纳米时间戳命名，避免用例间或本地残留库文件互相污染
        val name = "seed-test-${System.nanoTime()}.db"
        try {
            // 场景一：首开，onOpen 种子事务须把 14 条分类真实落盘（9 支出 + 5 收入）
            val db1 = openDb(name, fresh = true)
            try {
                assertEquals(14, db1.categoryDao().observeAll().first().size)
                // 币种种子同次落盘：21 条（CNY + 20 外币）；抽查 CNY 全字段验证 INSERT 列序
                assertEquals(21, db1.currencyDao().observeAll().first().size)
                val cny = db1.currencyDao().getByCode("CNY")!!
                assertEquals("人民币", cny.name)
                assertEquals("1", cny.rateToBase)
            } finally {
                runCatching { db1.close() }
            }
            // 场景二：同名库文件重开，COUNT 守卫命中「已种过」，不得重复插入（幂等）
            val db2 = openDb(name, fresh = false)
            try {
                assertEquals(14, db2.categoryDao().observeAll().first().size)
                assertEquals(21, db2.currencyDao().observeAll().first().size)
            } finally {
                runCatching { db2.close() }
            }
        } finally {
            dbFiles(ctx, name).forEach { it.delete() }
        }
    }

    @Test
    fun upgradePath_seedsCurrencies_whenCategoriesExist() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // 纳米时间戳命名，避免用例间或本地残留库文件互相污染
        val name = "seed-upgrade-test-${System.nanoTime()}.db"
        try {
            // 首开：两套种子齐全，构成「升级前」库（categories 与 currencies 均已种）
            val db1 = openDb(name, fresh = true)
            try {
                assertEquals(14, db1.categoryDao().observeAll().first().size)
                assertEquals(21, db1.currencyDao().observeAll().first().size)
                // 模拟 v0.1→v0.2 升级库：经 openHelper 直连清空 currencies，
                // 制造「categories 有、currencies 空」——守卫若不独立，币种将永远缺失
                db1.openHelper.writableDatabase.execSQL("DELETE FROM currencies")
            } finally {
                runCatching { db1.close() }
            }
            // 同名重开触发 onOpen：币种守卫独立命中空表 → 重新落种 21 条；
            // 分类守卫命中「已种」→ 不得重复插入（两守卫互不影响）
            val db2 = openDb(name, fresh = false)
            try {
                assertEquals(21, db2.currencyDao().observeAll().first().size)
                assertEquals(14, db2.categoryDao().observeAll().first().size)
            } finally {
                runCatching { db2.close() }
            }
        } finally {
            dbFiles(ctx, name).forEach { it.delete() }
        }
    }
}
