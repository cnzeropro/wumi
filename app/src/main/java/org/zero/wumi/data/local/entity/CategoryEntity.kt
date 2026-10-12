package org.zero.wumi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 分类方向：支出/收入。 */
object CategoryType {
    const val EXPENSE = 0
    const val INCOME = 1
}

// @Serializable 供本地备份 JSON 导出/导入（spec §9.2），与 Room 注解共存无冲突
@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey override val id: String,
    val parentId: String? = null,         // 二级分类自引用
    val type: Int,                        // 支出/收入
    val name: String,
    val emoji: String,
    val sortOrder: Int = 0,
    val archivedAt: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
    override val deviceId: String,
) : SyncMeta
