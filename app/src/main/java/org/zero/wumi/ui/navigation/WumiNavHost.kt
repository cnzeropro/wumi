package org.zero.wumi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.zero.wumi.ui.accounts.AccountsScreen
import org.zero.wumi.ui.budget.BudgetScreen
import org.zero.wumi.ui.categories.CategoriesScreen
import org.zero.wumi.ui.export.ExportScreen
import org.zero.wumi.ui.mine.MineScreen
import org.zero.wumi.ui.overview.OverviewScreen
import org.zero.wumi.ui.record.TransactionEditScreen
import org.zero.wumi.ui.stats.StatsScreen

/** 应用主导航：明细为起始页，图表/预算/我的并列一级路由（v0.1 均为占位页）；[nav] 由 WumiApp 创建传入，保证全局单一 NavController。 */
@Composable
fun WumiNavHost(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.OVERVIEW) {
        composable(Routes.OVERVIEW) { OverviewScreen(onOpenTx = { id -> nav.navigate(Routes.txEdit(id)) }) }
        composable(Routes.STATS) { StatsScreen() }
        composable(Routes.BUDGET) { BudgetScreen() }
        composable(Routes.MINE) {
            MineScreen(
                onOpenAccounts = { nav.navigate(Routes.ACCOUNTS) },
                onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                onOpenExport = { nav.navigate(Routes.EXPORT) },
            )
        }
        composable(Routes.ACCOUNTS) { AccountsScreen() }
        composable(Routes.CATEGORIES) { CategoriesScreen() }
        composable(Routes.EXPORT) { ExportScreen() }
        composable(
            route = Routes.TX_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            TransactionEditScreen(
                txId = entry.arguments?.getString("id")!!,
                onDone = { nav.popBackStack() },
            )
        }
    }
}
