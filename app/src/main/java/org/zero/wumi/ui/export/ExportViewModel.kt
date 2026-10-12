package org.zero.wumi.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.zero.wumi.data.backup.BackupFile
import org.zero.wumi.data.export.CsvExporter
import org.zero.wumi.data.export.JsonExporter
import org.zero.wumi.data.local.WumiDatabase
import org.zero.wumi.data.repo.TransactionRepository
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 导出页 ViewModel：CSV 按月取当期明细（SAF 回调时经 `first()` 取当前快照，Room 唯一真相源，
 * 不新增 DAO 查询）；JSON 全量与 BackupManager.backupNow 同构——同一事务内取 4 张表（含软删墓碑）。
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val txRepo: TransactionRepository,
    private val db: WumiDatabase,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val _month = MutableStateFlow(YearMonth.now(zone))

    /** 当前导出的月份。 */
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    fun prevMonth() { _month.value = _month.value.minusMonths(1) }

    fun nextMonth() { _month.value = _month.value.plusMonths(1) }

    /** [m] 的半开区间 [月初 0 点, 次月初 0 点)，与 DAO 的 date >= from AND date < to 口径一致。 */
    private fun monthRange(m: YearMonth): Pair<Long, Long> =
        m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** [m] 的交易明细 CSV 文本。 */
    suspend fun csvForMonth(m: YearMonth): String {
        val (from, to) = monthRange(m)
        return CsvExporter.export(txRepo.observeRange(from, to).first())
    }

    /** 全量 JSON 文本（账户/分类/交易/设置，含软删墓碑）。 */
    suspend fun jsonAll(): String {
        val snapshot = db.withTransaction {
            BackupFile(
                exportedAt = LocalDateTime.now().toString(),
                accounts = db.accountDao().allForBackup(),
                categories = db.categoryDao().allForBackup(),
                transactions = db.transactionDao().allForBackup(),
                settings = db.settingDao().allForBackup(),
            )
        }
        return JsonExporter.export(snapshot)
    }

    fun csvFileName(m: YearMonth): String =
        "wumi-" + m.format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".csv"

    fun jsonFileName(): String =
        "wumi-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json"
}
