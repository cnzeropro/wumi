package org.zero.wumi.ui.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 明细页占位：Task 3 起接入真实交易列表。 */
@Composable
fun OverviewScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("明细") }
}
