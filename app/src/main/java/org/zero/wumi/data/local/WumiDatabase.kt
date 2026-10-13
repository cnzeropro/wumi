package org.zero.wumi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.zero.wumi.data.local.dao.AccountDao
import org.zero.wumi.data.local.dao.CategoryDao
import org.zero.wumi.data.local.dao.CurrencyDao
import org.zero.wumi.data.local.dao.OutboxDao
import org.zero.wumi.data.local.dao.SettingDao
import org.zero.wumi.data.local.dao.TransactionDao
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.CurrencyEntity
import org.zero.wumi.data.local.entity.OutboxEntity
import org.zero.wumi.data.local.entity.SettingEntity
import org.zero.wumi.data.local.entity.TransactionEntity

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, OutboxEntity::class, SettingEntity::class, CurrencyEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class WumiDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun outboxDao(): OutboxDao
    abstract fun settingDao(): SettingDao
    abstract fun currencyDao(): CurrencyDao

    companion object {
        /** 1→2：新增 currencies 表（v0.2 多币种）。列顺序与 [CurrencyEntity] 声明严格一致。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `currencies` (" +
                        "`code` TEXT NOT NULL, `name` TEXT NOT NULL, `symbol` TEXT NOT NULL, " +
                        "`decimals` INTEGER NOT NULL, `rateToBase` TEXT NOT NULL, `rateUpdatedAt` INTEGER NOT NULL, " +
                        "`id` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`deletedAt` INTEGER, `deviceId` TEXT NOT NULL, PRIMARY KEY(`code`))",
                )
            }
        }
    }
}
