package org.zero.wumi.data.export

import org.zero.wumi.data.local.dao.TxWithRefs
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.util.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** RFC 4180 CSV 导出（spec §8 v0.1 交付项；对齐钱迹"导出永远免费"口碑基线）。 */
object CsvExporter {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 表头固定 7 列；金额列符号口径：支出 -、收入 +、转账空（对账软件通用惯例）。 */
    fun export(rows: List<TxWithRefs>): String = buildString {
        appendLine("日期,类型,分类,账户,金额,币种,备注")
        rows.forEach { r ->
            appendLine(
                listOf(
                    Instant.ofEpochMilli(r.tx.date).atZone(ZoneId.systemDefault()).format(dateFmt),
                    when (r.tx.type) {
                        TxType.EXPENSE -> "支出"
                        TxType.INCOME -> "收入"
                        else -> "转账"
                    },
                    r.category?.name ?: "",
                    // 转账对手方不能丢：转账行显示 "源 → 目标"，其余行只显示源账户
                    when (r.tx.type) {
                        TxType.TRANSFER -> "${r.account?.name ?: ""} → ${r.targetAccount?.name ?: ""}"
                        else -> r.account?.name ?: ""
                    },
                    formatAmount(r),
                    r.tx.currency,
                    r.tx.note,
                ).joinToString(",") { escape(it) },
            )
        }
    }

    /** 金额列 = 符号 + [Money.formatNumeric]（纯数字，无货币符号前缀，避免 "USD " 带空格剥不准）。 */
    private fun formatAmount(r: TxWithRefs): String =
        when (r.tx.type) {
            TxType.EXPENSE -> "-"
            TxType.INCOME -> "+"
            else -> ""
        } + Money.formatNumeric(r.tx.amountMinor, r.tx.currency)

    /** RFC 4180：含逗号/引号/换行的字段加引号，内部引号翻倍。 */
    internal fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + field.replace("\"", "\"\"") + "\""
        else field
}
