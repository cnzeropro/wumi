package org.zero.wumi.data.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.zero.wumi.data.backup.BackupFile

/** JSON 全量导出：直接序列化 [BackupFile]（含软删墓碑），与本地备份同构、可互导入。 */
object JsonExporter {
    private val json = Json { prettyPrint = true }

    fun export(snapshot: BackupFile): String = json.encodeToString(snapshot)
}
