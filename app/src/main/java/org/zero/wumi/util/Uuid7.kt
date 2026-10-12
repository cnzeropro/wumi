package org.zero.wumi.util

import java.security.SecureRandom

private val random = SecureRandom()

/** RFC 9562 UUIDv7，时间有序（spec §5.1：客户端生成、离线可造）。 */
fun uuid7(nowMillis: Long = System.currentTimeMillis()): String {
    val bytes = ByteArray(16)
    // 先随机填充（提供同毫秒唯一的 74bit 熵），时间戳随后覆盖 bytes[0..5]，
    // 保证前 12 个 hex 字符严格随时间递增（字典序有序）。
    random.nextBytes(bytes)
    val ts = nowMillis
    bytes[0] = (ts ushr 40).toByte(); bytes[1] = (ts ushr 32).toByte()
    bytes[2] = (ts ushr 24).toByte(); bytes[3] = (ts ushr 16).toByte()
    bytes[4] = (ts ushr 8).toByte();  bytes[5] = ts.toByte()
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()  // version 7
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()  // RFC variant
    val hex = bytes.joinToString("") { "%02x".format(it) }
    return "${hex.substring(0,8)}-${hex.substring(8,12)}-${hex.substring(12,16)}-${hex.substring(16,20)}-${hex.substring(20)}"
}
