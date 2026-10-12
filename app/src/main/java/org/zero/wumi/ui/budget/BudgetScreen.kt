package org.zero.wumi.ui.budget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 预算页占位：预算不在 v0.1 范围（spec §8）。 */
@Composable
fun BudgetScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("预算") }
}
