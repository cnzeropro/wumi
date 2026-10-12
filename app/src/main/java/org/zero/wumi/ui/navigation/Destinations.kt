package org.zero.wumi.ui.navigation

/** 底部导航四个一级页 + 二级页的路由常量（spec §7.2）。 */
object Routes {
    const val OVERVIEW = "overview"
    const val STATS = "stats"
    const val BUDGET = "budget"
    const val MINE = "mine"

    /** 二级路由：账户管理（从「我的」进入，Task 6）。 */
    const val ACCOUNTS = "accounts"
}
