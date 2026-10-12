package org.zero.wumi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import org.zero.wumi.data.local.dao.AccountDao
import org.zero.wumi.data.local.dao.CategoryDao
import org.zero.wumi.data.local.dao.OutboxDao
import org.zero.wumi.data.local.dao.SettingDao
import org.zero.wumi.data.local.dao.TransactionDao
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.OutboxEntity
import org.zero.wumi.data.local.entity.SettingEntity
import org.zero.wumi.data.local.entity.TransactionEntity

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, OutboxEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WumiDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun outboxDao(): OutboxDao
    abstract fun settingDao(): SettingDao
}
