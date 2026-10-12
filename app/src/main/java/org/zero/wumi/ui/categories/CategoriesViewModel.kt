package org.zero.wumi.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.CategoryType
import org.zero.wumi.data.repo.CategoryRepository
import javax.inject.Inject

/** 分类管理页 UI 状态：支出/收入两组可用分类（已归档不展示）。 */
data class CategoriesUiState(
    val expense: List<CategoryEntity> = emptyList(),
    val income: List<CategoryEntity> = emptyList(),
)

/**
 * 分类管理 ViewModel：订阅两个方向的可用分类流（Room 唯一真相源），代理保存与归档。
 * 列表取 observeActive（DAO 过滤软删 + 归档）——长按归档后分类即从列表消失。
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repo: CategoryRepository,
) : ViewModel() {

    /** 按 type 分组的实时分类列表；[CategoryEntity.parentId] 二级分类 v0.1 暂不开放。 */
    val state: StateFlow<CategoriesUiState> =
        combine(
            repo.observeActive(CategoryType.EXPENSE),
            repo.observeActive(CategoryType.INCOME),
        ) { expense, income -> CategoriesUiState(expense, income) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    /** 新建或更新分类（id 为空即新建）；业务表 + outbox 同事务由仓库保证。 */
    fun save(id: String?, parentId: String?, type: Int, name: String, emoji: String, sortOrder: Int) =
        viewModelScope.launch { repo.save(id, parentId, type, name, emoji, sortOrder) }

    /** 归档分类（写 archivedAt，保留历史交易引用），列表随 Room Flow 自动刷新。 */
    fun archive(id: String) = viewModelScope.launch { repo.archive(id) }
}
