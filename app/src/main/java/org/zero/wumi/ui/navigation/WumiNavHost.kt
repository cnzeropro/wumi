package org.zero.wumi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.zero.wumi.ui.budget.BudgetScreen
import org.zero.wumi.ui.mine.MineScreen
import org.zero.wumi.ui.overview.OverviewScreen
import org.zero.wumi.ui.stats.StatsScreen

/** 应用主导航：明细为起始页，图表/预算/我的并列一级路由（v0.1 均为占位页）。 */
@Composable
fun WumiNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.OVERVIEW) {
        composable(Routes.OVERVIEW) { OverviewScreen() }
        composable(Routes.STATS) { StatsScreen() }
        composable(Routes.BUDGET) { BudgetScreen() }
        composable(Routes.MINE) { MineScreen() }
    }
}
