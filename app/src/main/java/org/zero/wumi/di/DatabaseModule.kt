package org.zero.wumi.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.WumiDbCallback
import org.zero.wumi.data.local.dao.AccountDao
import org.zero.wumi.data.local.dao.CategoryDao
import org.zero.wumi.data.local.dao.OutboxDao
import org.zero.wumi.data.local.dao.SettingDao
import org.zero.wumi.data.local.dao.TransactionDao
import org.zero.wumi.data.local.entity.SettingEntity
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_NAME = "wumi.db"
    private const val DEVICE_ID_KEY = "deviceId"

    /**
     * deviceId：首启生成 UUIDv4 持久化到 settings 表（spec §5.1）。
     *
     * 用无 callback 的独立连接读写后即关闭——callback 需要 deviceId，而 deviceId 恰在库中，
     * 必须先于主库建库读取；Dagger 依赖顺序（[db] 注入 @DeviceId）保证先 deviceId 后主库。
     */
    @Provides
    @Singleton
    @DeviceId
    fun deviceId(@ApplicationContext ctx: Context): String {
        // bootstrap 连接与主库打开同一个库文件，必须同样挂迁移：
        // 老用户升级后此处先开 version=1 的库，缺迁移会直接抛 IllegalStateException 崩在首启
        val bootstrap = Room.databaseBuilder(ctx, WumiDatabase::class.java, DB_NAME)
            .addMigrations(WumiDatabase.MIGRATION_1_2)
            .build()
        return try {
            runBlocking {
                val dao = bootstrap.settingDao()
                dao.get(DEVICE_ID_KEY) ?: UUID.randomUUID().toString().also { dao.put(SettingEntity(DEVICE_ID_KEY, it)) }
            }
        } finally {
            bootstrap.close()
        }
    }

    /** 主库：依赖顺序保证已持有 deviceId，再建库并挂上种子 callback。 */
    @Provides
    @Singleton
    fun db(@ApplicationContext ctx: Context, @DeviceId deviceId: String): WumiDatabase =
        Room.databaseBuilder(ctx, WumiDatabase::class.java, DB_NAME)
            .addMigrations(WumiDatabase.MIGRATION_1_2)
            .addCallback(WumiDbCallback(deviceId))
            .build()

    @Provides
    fun accountDao(db: WumiDatabase): AccountDao = db.accountDao()

    @Provides
    fun categoryDao(db: WumiDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun transactionDao(db: WumiDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun outboxDao(db: WumiDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun settingDao(db: WumiDatabase): SettingDao = db.settingDao()
}
