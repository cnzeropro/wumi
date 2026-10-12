package org.zero.wumi.ui.mine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 我的页占位：账户管理等入口由 Task 6 接入。 */
@Composable
fun MineScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("我的") }
}
