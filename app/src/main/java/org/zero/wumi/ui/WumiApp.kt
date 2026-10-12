package org.zero.wumi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.zero.wumi.ui.navigation.Routes
import org.zero.wumi.ui.navigation.WumiNavHost
import org.zero.wumi.ui.record.RecordSheet
import org.zero.wumi.ui.record.UndoViewModel

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/**
 * 应用骨架：底部 4 tab + 右下 FAB 记一笔 + Snackbar 撤销（spec §7.2）。
 * 记一笔面板与撤销均挂在 Activity 作用域（不在 NavHost 内，hiltViewModel/viewModel 即 Activity 级）。
 */
@Composable
fun WumiApp(undoVm: UndoViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showRecord by remember { mutableStateOf(false) }
    val tabs = listOf(
        Tab(Routes.OVERVIEW, "明细", Icons.Filled.Savings),
        Tab(Routes.STATS, "图表", Icons.Filled.PieChart),
        Tab(Routes.BUDGET, "预算", Icons.Filled.AccountBalanceWallet),
        Tab(Routes.MINE, "我的", Icons.Filled.Person),
    )
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // 记一笔入口只在明细页显示——二级页（账户/分类/导出）有自己的操作按钮，
            // 全局 FAB 常驻会遮挡页面按钮并劫持点击（真机验收发现）
            if (current == Routes.OVERVIEW) {
                FloatingActionButton(onClick = { showRecord = true }) { Icon(Icons.Filled.Add, "记一笔") }
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(Routes.OVERVIEW) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) { WumiNavHost(nav) }
    }
    if (showRecord) {
        RecordSheet(
            onDismiss = { showRecord = false },
            onSaved = { txId ->
                showRecord = false
                undoVm.record(txId)
                scope.launch {
                    // Snackbar 撤销（spec §7.2）：点「撤销」软删发出本 Snackbar 的那一笔（捕获 txId，
                    // 连续记账时槽位可能已被覆盖，读槽位会删错笔）
                    val result = snackbar.showSnackbar("已记一笔", actionLabel = "撤销")
                    if (result == SnackbarResult.ActionPerformed) undoVm.undo(txId)
                }
            },
        )
    }
}
