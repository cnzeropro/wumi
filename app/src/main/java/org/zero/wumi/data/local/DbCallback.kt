package org.zero.wumi.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.CategoryType
import org.zero.wumi.util.uuid7

/** 默认一级分类种子（spec 调研结论：冷启动无分类是记账 App 大忌）。 */
object DefaultSeed {
    val expense = listOf(
        "餐饮" to "🍜", "交通" to "🚌", "购物" to "🛍️", "居住" to "🏠", "娱乐" to "🎮",
        "医疗" to "💊", "教育" to "📚", "人情" to "🧧", "其他" to "📦",
    )
    val income = listOf("工资" to "💰", "奖金" to "🎁", "理财" to "📈", "红包" to "🧧", "其他" to "💵")

    /** 生成全部种子分类（收入 + 支出），id 用 UUIDv7 保证同毫秒唯一。 */
    fun categories(now: Long, deviceId: String): List<CategoryEntity> =
        (expense.mapIndexed { i, (n, e) -> CategoryEntity(uuid7(now), null, CategoryType.EXPENSE, n, e, i, null, now, now, null, deviceId) } +
            income.mapIndexed { i, (n, e) -> CategoryEntity(uuid7(now), null, CategoryType.INCOME, n, e, i, null, now, now, null, deviceId) })
}

/**
 * 预置默认分类的数据库回调，供 Hilt 模块以 addCallback 挂载。
 *
 * 用 [onOpen] 而非 onCreate 做幂等种子：deviceId 读取会先用无 callback 的独立连接
 * 建库，主库打开时文件已存在，onCreate 不再触发；改为「表空才插入」，与建库顺序无关。
 * 以全表 COUNT（含已软删）判断是否种过，避免用户删光分类后重启被重新种回。
 */
class WumiDbCallback(private val deviceId: String) : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        val seeded = db.query("SELECT COUNT(*) FROM categories").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0) > 0L
        }
        if (seeded) return
        val now = System.currentTimeMillis()
        DefaultSeed.categories(now, deviceId).forEach { c ->
            db.execSQL(
                // VALUES 顺序须与 categories 列顺序严格一致：
                // id, parentId, type, name, emoji, sortOrder, archivedAt, createdAt, updatedAt, deletedAt, deviceId
                "INSERT INTO categories VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(c.id, c.parentId, c.type, c.name, c.emoji, c.sortOrder, c.archivedAt, c.createdAt, c.updatedAt, c.deletedAt, c.deviceId),
            )
        }
    }
}
