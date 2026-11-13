package np.com.siriri.keyboard

import android.content.Context
import android.content.res.Configuration

/** Colour tokens. Follows the OS dark/light setting; nothing is configurable inside the app. */
class KeyTheme private constructor(val dark: Boolean) {
    val container: Int
    val charKey: Int
    val modKey: Int
    val charPressed: Int
    val modPressed: Int
    val shadow: Int
    val label: Int
    val shiftActive: Int
    val shiftActiveGlyph: Int
    val suggestion: Int

    val badge = 0xFFAEAEB2.toInt()
    val hint = 0xFF8E8E93.toInt()
    val returnBlue = 0xFF0A84FF.toInt()
    val returnBluePressed = 0xFF0A6BD6.toInt()
    val returnGray = 0xFFB8BAC0.toInt()
    val returnGrayPressed = 0xFFA4A6AC.toInt()
    val returnGrayLabel = 0xFF1C1C1E.toInt()
    val white = 0xFFFFFFFF.toInt()
    val popupBg: Int

    init {
        if (dark) {
            container = 0xFF1C1C1E.toInt()
            charKey = 0xFF3A3A3C.toInt()
            modKey = 0xFF2C2C2E.toInt() // recessed
            charPressed = 0xFF58585C.toInt()
            modPressed = 0xFF48484A.toInt()
            shadow = 0xFF111111.toInt()
            label = 0xFFFFFFFF.toInt()
            shiftActive = 0xFFE5E5EA.toInt()
            shiftActiveGlyph = 0xFF1C1C1E.toInt()
            suggestion = 0xFFFFFFFF.toInt()
            popupBg = 0xFF48484A.toInt()
        } else {
            container = 0xFFD8DADF.toInt()
            charKey = 0xFFFFFFFF.toInt()
            modKey = 0xFFCFD3DC.toInt() // distinct recessed modifier keycap in light mode
            charPressed = 0xFFE6E7EB.toInt()
            modPressed = 0xFFB8BDC7.toInt()
            shadow = 0xFFB0B3B8.toInt()
            label = 0xFF000000.toInt()
            shiftActive = 0xFF1C1C1E.toInt() // high-contrast active shift cap
            shiftActiveGlyph = 0xFFFFFFFF.toInt()
            suggestion = 0xFF000000.toInt()
            popupBg = 0xFFFFFFFF.toInt()
        }
    }

    companion object {
        fun forContext(context: Context): KeyTheme {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return KeyTheme(night == Configuration.UI_MODE_NIGHT_YES)
        }
    }
}
