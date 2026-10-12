package org.zero.wumi.data.export

import org.junit.Assert.assertEquals
import org.junit.Test
import org.zero.wumi.data.local.dao.TxWithRefs
import org.zero.wumi.data.local.entity.AccountEntity
import org.zero.wumi.data.local.entity.AccountType
import org.zero.wumi.data.local.entity.CategoryEntity
import org.zero.wumi.data.local.entity.CategoryType
import org.zero.wumi.data.local.entity.TransactionEntity
import org.zero.wumi.data.local.entity.TxSource
import org.zero.wumi.data.local.entity.TxType
import org.zero.wumi.util.uuid7
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** CSV 导出测试（Task 12）：列顺序/金额符号、RFC 4180 转义、空列表仅表头。 */
class CsvExporterTest {
    private val dev = "d"
    private val acc = AccountEntity(uuid7(), "现金", "💵", "CNY", 0, AccountType.CASH, createdAt = 1, updatedAt = 1, deviceId = dev)
    private val cat = CategoryEntity(uuid7(), null, CategoryType.EXPENSE, "餐饮", "🍜", 0, null, 1, 1, null, dev)
    private val date = 1_700_000_000_000L

    private fun tx(note: String) = TransactionEntity(
        uuid7(), TxType.EXPENSE, 2500, "CNY", null, 2500, acc.id, null, cat.id, date, note, TxSource.MANUAL, 1, 1, null, dev,
    )

    /** 期望日期列与实现同口径（本地时区 + 同 pattern），测试与机器时区无关。 */
    private fun expectedDate() = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    @Test fun basic_row() {
        val csv = CsvExporter.export(listOf(TxWithRefs(tx("午饭"), acc, null, cat)))
        val lines = csv.trim().lines()
        assertEquals("日期,类型,分类,账户,金额,币种,备注", lines[0])
        assertEquals("${expectedDate()},支出,餐饮,现金,-25.00,CNY,午饭", lines[1])
    }

    @Test fun escapes_comma_quote_newline() {
        val csv = CsvExporter.export(listOf(TxWithRefs(tx("奶茶, \"半糖\"\n多冰"), acc, null, cat)))
        // 备注含换行且被引号包裹：lines() 会在字段内部断行，先重建完整数据行再断言
        val dataLine = csv.trim().lines().drop(1).joinToString("\n")
        assertEquals("${expectedDate()},支出,餐饮,现金,-25.00,CNY,\"奶茶, \"\"半糖\"\"\n多冰\"", dataLine)
    }

    @Test fun empty_list_header_only() {
        assertEquals("日期,类型,分类,账户,金额,币种,备注", CsvExporter.export(emptyList()).trim())
    }
}
