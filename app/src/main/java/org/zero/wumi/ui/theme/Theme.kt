package org.zero.wumi.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(primary = WumiSeed)
private val DarkColors = darkColorScheme(primary = WumiSeed)

/**
 * 吾米应用主题：品牌米金为准，深色模式独立调校。
 *
 * @param dynamicColor 动态取色开关（spec §7.1），默认关以保品牌色；
 *   v1.0 才从设置页接入，Android 12+ 生效。
 */
@Composable
fun WumiTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, typography = WumiTypography, content = content)
}
