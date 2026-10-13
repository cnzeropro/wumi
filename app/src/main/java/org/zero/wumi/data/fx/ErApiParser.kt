package org.zero.wumi.data.fx

import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** open.er-api.com 响应解析：只取白名单币种，非法值跳过。 */
object ErApiParser {
    fun parse(json: String, allowed: Set<String>): Map<String, String> = runCatching {
        val root = Json.parseToJsonElement(json).jsonObject
        if (root["result"]?.jsonPrimitive?.contentOrNull != "success") return emptyMap()
        val rates = root["rates"]?.jsonObject ?: return emptyMap()
        rates.mapNotNull { (code, v) ->
            if (code !in allowed) return@mapNotNull null
            val s = runCatching { v.jsonPrimitive.content }.getOrNull() ?: return@mapNotNull null
            val d = runCatching { BigDecimal(s) }.getOrNull() ?: return@mapNotNull null
            if (d <= BigDecimal.ZERO) return@mapNotNull null
            code to d.stripTrailingZeros().toPlainString()
        }.toMap()
    }.getOrDefault(emptyMap())
}
