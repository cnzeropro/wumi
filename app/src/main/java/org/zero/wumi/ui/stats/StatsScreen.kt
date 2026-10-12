package org.zero.wumi.ui.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 图表页占位：图表不在 v0.1 范围（spec §8）。 */
@Composable
fun StatsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("图表") }
}
