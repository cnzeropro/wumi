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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.zero.wumi.ui.navigation.Routes
import org.zero.wumi.ui.navigation.WumiNavHost

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** 应用骨架：底部 4 tab + 中央 ⊕ FAB 记一笔（spec §7.2），FAB 点击由 Task 8 接入。 */
@Composable
fun WumiApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val tabs = listOf(
        Tab(Routes.OVERVIEW, "明细", Icons.Filled.Savings),
        Tab(Routes.STATS, "图表", Icons.Filled.PieChart),
        Tab(Routes.BUDGET, "预算", Icons.Filled.AccountBalanceWallet),
        Tab(Routes.MINE, "我的", Icons.Filled.Person),
    )
    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = { /* Task 8 接入记一笔 */ }) { Icon(Icons.Filled.Add, "记一笔") } },
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
}
