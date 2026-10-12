package org.zero.wumi.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.zero.wumi.data.backup.BackupManager
import org.zero.wumi.data.local.WumiDatabase
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    @Provides
    @Singleton
    fun backupManager(@ApplicationContext ctx: Context, db: WumiDatabase): BackupManager =
        BackupManager(db, File(ctx.filesDir, "backups"))
}
