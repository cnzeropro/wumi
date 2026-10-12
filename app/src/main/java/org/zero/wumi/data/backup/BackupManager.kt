package org.zero.wumi.data.backup

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.SettingEntity
import org.zero.wumi.data.local.entity.TransactionEntity
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 备份文件顶层结构；formatVersion 用于未来格式演进（spec §9.2）。 */
@Serializable
data class BackupFile(
    val formatVersion: Int = 1,
    val exportedAt: String,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val settings: List<SettingEntity>,
)

/**
 * 本地备份（spec §9.2）。
 *
 * 写入策略：先写 `.json.tmp` 临时文件再原子 `renameTo` 最终名——防止写一半崩溃留下残缺备份；
 * 保留策略：滚动保留最近 [rotate] 份（默认 7），按文件名（内嵌时间戳）倒序判定新旧。
 */
class BackupManager(private val db: WumiDatabase, private val dir: File) {
    private val json = Json { prettyPrint = true }
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    init {
        dir.mkdirs()
    }

    /** 全量导出为单 JSON 文件（含软删墓碑），返回原子 rename 后的最终文件。 */
    suspend fun backupNow(): File = withContext(Dispatchers.IO) {
        val snapshot = BackupFile(
            exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            accounts = db.accountDao().allForBackup(),
            categories = db.categoryDao().allForBackup(),
            transactions = db.transactionDao().allForBackup(),
            settings = db.settingDao().allForBackup(),
        )
        val tmp = File(dir, "wumi-backup-${LocalDateTime.now().format(stamp)}.json.tmp")
        tmp.writeText(json.encodeToString(snapshot))
        val final = File(dir, tmp.name.removeSuffix(".tmp"))
        if (!tmp.renameTo(final)) {
            tmp.delete()
            error("backup rename failed: ${tmp.path}")
        }
        final
    }

    /** 滚动保留最近 [keep] 份备份，删除其余最旧的。 */
    suspend fun rotate(keep: Int = 7) = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.startsWith("wumi-backup-") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?.drop(keep)
            ?.forEach { it.delete() }
        Unit
    }

    /**
     * 从备份文件恢复：先清空全库再整表导入（恢复场景专用）。
     *
     * 注意 `clearAllTables()` 禁止在事务内调用（Room 限制），故先清库，
     * 再单独事务批量 upsert 以提高写入效率。
     */
    suspend fun restoreFrom(file: File) = withContext(Dispatchers.IO) {
        val data = json.decodeFromString<BackupFile>(file.readText())
        db.clearAllTables()
        db.withTransaction {
            data.accounts.forEach { db.accountDao().upsert(it) }
            data.categories.forEach { db.categoryDao().upsert(it) }
            data.transactions.forEach { db.transactionDao().insert(it) }
            data.settings.forEach { db.settingDao().put(it) }
        }
    }
}
