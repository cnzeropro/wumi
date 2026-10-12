package org.zero.wumi.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.CategoryType

/**
 * 新建/编辑分类底部面板（spec §7.4）：名称、emoji 与方向（支出/收入 FilterChip），结构与
 * [org.zero.wumi.ui.accounts.AccountEditSheet] 同型。[existing] 为空即新建（排序固定排末尾）；
 * 编辑时排序保持原值由调用方回传，方向同样可改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditSheet(
    existing: CategoryEntity?,
    defaultType: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, type: Int) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "📦") }
    var type by remember { mutableIntStateOf(existing?.type ?: defaultType) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (existing == null) "新建分类" else "编辑分类", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(emoji, { emoji = it }, label = { Text("图标（emoji）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            // 方向选择
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == CategoryType.EXPENSE, onClick = { type = CategoryType.EXPENSE }, label = { Text("支出") })
                FilterChip(selected = type == CategoryType.INCOME, onClick = { type = CategoryType.INCOME }, label = { Text("收入") })
            }
            Button(
                onClick = {
                    onSave(name, emoji, type)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
