package org.zero.wumi.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 我的页：「账户管理」入口（Task 6），其余 v0.1 仍为占位。 */
@Composable
fun MineScreen(onOpenAccounts: () -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text("账户管理") },
            leadingContent = { Text("🏦", style = MaterialTheme.typography.headlineSmall) },
            modifier = Modifier.clickable { onOpenAccounts() },
        )
        HorizontalDivider()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("我的") }
    }
}
