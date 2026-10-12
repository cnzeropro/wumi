package org.zero.wumi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.zero.wumi.ui.budget.BudgetScreen
import org.zero.wumi.ui.mine.MineScreen
import org.zero.wumi.ui.overview.OverviewScreen
import org.zero.wumi.ui.stats.StatsScreen

/** 应用主导航：明细为起始页，图表/预算/我的并列一级路由（v0.1 均为占位页）；[nav] 由 WumiApp 创建传入，保证全局单一 NavController。 */
@Composable
fun WumiNavHost(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.OVERVIEW) {
        composable(Routes.OVERVIEW) { OverviewScreen() }
        composable(Routes.STATS) { StatsScreen() }
        composable(Routes.BUDGET) { BudgetScreen() }
        composable(Routes.MINE) { MineScreen() }
    }
}
