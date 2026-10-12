package org.zero.wumi.data.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.zero.wumi.data.backup.BackupFile

/** JSON 全量导出：直接序列化 [BackupFile]（含软删墓碑），与本地备份同构、可互导入。 */
object JsonExporter {
    // encodeDefaults：formatVersion 等默认值字段必须显式落盘（真机验收发现省略会破坏向前可读性）
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun export(snapshot: BackupFile): String = json.encodeToString(snapshot)
}
