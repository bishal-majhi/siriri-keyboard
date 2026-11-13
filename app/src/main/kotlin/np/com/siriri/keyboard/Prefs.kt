package np.com.siriri.keyboard

import android.content.Context

/** Three local switches, stored in the app's private SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("siriri_keyboard", Context.MODE_PRIVATE)

    var mode: Mode
        get() = Mode.fromId(sp.getInt("mode", 0))
        set(v) = sp.edit().putInt("mode", v.id).apply()

    /** Learn words from typing (suggestions). On by default. */
    var learn: Boolean
        get() = sp.getBoolean("learn", true)
        set(v) = sp.edit().putBoolean("learn", v).apply()

    /** Haptic tick on key press. Still subject to the system touch-feedback setting. */
    var haptic: Boolean
        get() = sp.getBoolean("haptic", true)
        set(v) = sp.edit().putBoolean("haptic", v).apply()

    /** Key click sound on key press. */
    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    /** Whether [m] is included when cycling with the Globe key. */
    fun isModeEnabled(m: Mode): Boolean = sp.getBoolean("mode_${m.id}", true)

    fun setModeEnabled(m: Mode, enabled: Boolean) {
        sp.edit().putBoolean("mode_${m.id}", enabled).apply()
        if (Mode.entries.none { isModeEnabled(it) }) {
            sp.edit().putBoolean("mode_${Mode.ENGLISH.id}", true).apply()
        }
    }
}
