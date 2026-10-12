package org.zero.wumi.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 我的页：账户管理/分类管理/数据导出入口 + 关于区（应用名、版本、slogan）。 */
@Composable
fun MineScreen(
    onOpenAccounts: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenExport: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text("账户管理") },
            leadingContent = { Text("🏦", style = MaterialTheme.typography.headlineSmall) },
            modifier = Modifier.clickable { onOpenAccounts() },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("分类管理") },
            leadingContent = { Text("🏷️", style = MaterialTheme.typography.headlineSmall) },
            modifier = Modifier.clickable { onOpenCategories() },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("数据导出") },
            supportingContent = { Text("CSV 明细 / JSON 全量") },
            leadingContent = { Text("📤", style = MaterialTheme.typography.headlineSmall) },
            modifier = Modifier.clickable { onOpenExport() },
        )
        HorizontalDivider()
        // 关于区沉底：静态文本，v0.1 硬编码版本号（与 defaultConfig.versionName 一致）
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Wumi · 吾米记账", style = MaterialTheme.typography.titleMedium)
            Text(
                "版本 0.0.1",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("吾米蓄财，智理未来", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Wealth & Me, Hand in Hand",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
