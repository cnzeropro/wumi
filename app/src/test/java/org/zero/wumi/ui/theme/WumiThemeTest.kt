package org.zero.wumi.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class WumiThemeTest {
    @Test fun seedColor_isRiceGold() {
        assertEquals(Color(0xFFC8A24B), WumiSeed)
    }
}
