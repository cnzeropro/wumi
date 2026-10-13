package org.zero.wumi.data.fx

/** open.er-api.com 拉取（HttpURLConnection，不引 OkHttp——留给 v0.3 同步层）。 */
object FxRateFetcher {
    /** 拉取 CNY 基准汇率并经 [ErApiParser] 白名单过滤，任何网络/解析异常折成 Result.failure。 */
    fun fetch(allowed: Set<String>): Result<Map<String, String>> = runCatching {
        val conn = java.net.URI("https://open.er-api.com/v6/latest/CNY").toURL()
            .openConnection() as javax.net.ssl.HttpsURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.requestMethod = "GET"
        if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        ErApiParser.parse(body, allowed)
    }
}
