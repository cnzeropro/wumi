package org.zero.wumi.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.dao.CurrencyDao
import org.zero.wumi.data.local.dao.OutboxDao
import org.zero.wumi.data.local.dao.SettingDao
import org.zero.wumi.data.repo.CurrencyRepository
import javax.inject.Singleton

/** 多币种（v0.2）：CurrencyRepository 构造含 fetch 函数参数，无法 @Inject，改走 @Provides。 */
@Module
@InstallIn(SingletonComponent::class)
object FxModule {
    @Provides
    @Singleton
    fun currencyRepository(
        db: WumiDatabase,
        dao: CurrencyDao,
        outboxDao: OutboxDao,
        settingDao: SettingDao,
        @DeviceId deviceId: String,
    ): CurrencyRepository = CurrencyRepository(db, dao, outboxDao, settingDao, deviceId)
}
