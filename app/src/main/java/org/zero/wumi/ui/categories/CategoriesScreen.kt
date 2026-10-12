package org.zero.wumi.ui.categories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.CategoryType

/**
 * 分类管理页（spec §7.4）：支出/收入两个分组列表，点击编辑、长按归档；
 * 新建经两个 FAB 预选方向。归档为软操作（保留历史交易引用），归档后从列表消失并以 Snackbar 提示。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoriesScreen(vm: CategoriesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showNewType by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Row {
                ExtendedFloatingActionButton(
                    onClick = { showNewType = CategoryType.EXPENSE },
                    text = { Text("支出分类") },
                    icon = { Icon(Icons.Filled.Add, "新建支出分类") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                ExtendedFloatingActionButton(
                    onClick = { showNewType = CategoryType.INCOME },
                    text = { Text("收入分类") },
                    icon = { Icon(Icons.Filled.Add, "新建收入分类") },
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionHeader("支出") }
            items(state.expense, key = { it.id }) { c ->
                CategoryRow(c, onClick = { editing = c }, onArchive = {
                    vm.archive(c.id)
                    scope.launch { snackbar.showSnackbar("已归档「${c.name}」") }
                })
            }
            item { SectionHeader("收入") }
            items(state.income, key = { it.id }) { c ->
                CategoryRow(c, onClick = { editing = c }, onArchive = {
                    vm.archive(c.id)
                    scope.launch { snackbar.showSnackbar("已归档「${c.name}」") }
                })
            }
        }
    }
    showNewType?.let { t ->
        CategoryEditSheet(
            existing = null,
            defaultType = t,
            onDismiss = { showNewType = null },
            // 新建固定 sortOrder = 99：DAO 按 sortOrder 排序，大值即排组末尾
            onSave = { name, emoji, type -> vm.save(null, null, type, name, emoji, 99) },
        )
    }
    editing?.let { c ->
        CategoryEditSheet(
            existing = c,
            defaultType = c.type,
            onDismiss = { editing = null },
            onSave = { name, emoji, type -> vm.save(c.id, c.parentId, type, name, emoji, c.sortOrder) },
        )
    }
}

/** 分组小标题：material3 无现成 ListSubheader，按其视觉规范用 labelLarge Text 实现。 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** 分类行：点击编辑、长按归档（无二次确认，Snackbar 兜底反馈）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(c: CategoryEntity, onClick: () -> Unit, onArchive: () -> Unit) {
    ListItem(
        headlineContent = { Text(c.name) },
        leadingContent = { Text(c.emoji, style = MaterialTheme.typography.headlineSmall) },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onArchive),
    )
    HorizontalDivider()
}
