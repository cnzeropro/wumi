package org.zero.wumi.ui.navigation

/** 底部导航四个一级页 + 二级页的路由常量（spec §7.2）。 */
object Routes {
    const val OVERVIEW = "overview"
    const val STATS = "stats"
    const val BUDGET = "budget"
    const val MINE = "mine"

    /** 二级路由：账户管理（Task 6）与分类管理（Task 7），均从「我的」进入。 */
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"

    /** 二级路由：交易编辑（Task 10），从明细页点交易行进入，{id} 为交易 id。 */
    const val TX_EDIT = "tx/{id}"

    /** 交易编辑路由实例：TX_EDIT 模板填入具体交易 id。 */
    fun txEdit(id: String) = "tx/$id"
}
