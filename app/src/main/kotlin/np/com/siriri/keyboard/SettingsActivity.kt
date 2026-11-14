package np.com.siriri.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Minimal settings screen built in code (no layouts, no AppCompat). Follows OS dark/light. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var count: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 15f, bold: Boolean = false, topMargin: Int = 0) =
        TextView(this).apply {
            this.text = text
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(topMargin), 0, dp(4)) }
        }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        this.text = text
        isChecked = checked
        textSize = 16f
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Native.init(filesDir.absolutePath)
        prefs = Prefs(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        col.addView(label("Siriri Nepali Keyboard", 24f, bold = true))
        col.addView(label("English (En) · नेपाली (ने) · Hindi (Hi) · Roman Nepali (Np)", 14f))

        // --- setup ---
        col.addView(label("Setup", 18f, bold = true, topMargin = 24))
        status = label("", 14f)
        col.addView(status)
        col.addView(button("1. Enable Siriri Keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        col.addView(button("2. Switch to Siriri Keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        // --- privacy ---
        col.addView(label("Privacy", 18f, bold = true, topMargin = 24))
        col.addView(label(permissionLine(), 14f))
        col.addView(
            label(
                "No internet access, no analytics, no cloud backup. Words you type are kept only in " +
                    "this app's private storage on this phone; Android prevents other apps from reading them. " +
                    "Nothing is learned in password, e-mail, URL or number fields, or in incognito fields.",
                14f,
            ),
        )

        col.addView(toggle("Learn words as I type", prefs.learn) { prefs.learn = it })
        col.addView(toggle("Vibrate on key press", prefs.haptic) { prefs.haptic = it })
        col.addView(toggle("Sound on key press", prefs.sound) { prefs.sound = it })

        // --- languages ---
        col.addView(label("Languages (En / ने / Hi / Np key)", 18f, bold = true, topMargin = 24))
        val modeSwitches = HashMap<Mode, Switch>()
        for (m in Mode.CYCLE_ORDER) {
            val sw = toggle(m.title, prefs.isModeEnabled(m)) { enabled ->
                prefs.setModeEnabled(m, enabled)
                for ((mode, view) in modeSwitches) {
                    val actual = prefs.isModeEnabled(mode)
                    if (view.isChecked != actual) view.isChecked = actual
                }
            }
            modeSwitches[m] = sw
            col.addView(sw)
        }
        col.addView(
            label(
                "Tip: hold a top-row key for numbers 1–0, or slide horizontally across the spacebar to move the cursor.",
                13f,
            ),
        )

        count = label("", 16f, topMargin = 16)
        col.addView(count)
        col.addView(button("Clear all saved words") { confirmClear() })
        col.addView(label("Tip: long-press a suggestion on the keyboard to forget just that word.", 13f))

        // --- try it ---
        col.addView(label("Try it", 18f, bold = true, topMargin = 24))
        col.addView(
            EditText(this).apply {
                hint = "Type here to test the keyboard"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 3
                gravity = Gravity.TOP
            },
        )

        val maxW = resources.displayMetrics.widthPixels.coerceAtMost(dp(600))
        val container = FrameLayout(this).apply {
            addView(
                col,
                FrameLayout.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL),
            )
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            isFillViewport = true
            addView(container)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        status.text = when {
            current -> "✓ Siriri Keyboard is your active keyboard."
            enabled -> "Siriri Keyboard is enabled. Now switch to it (step 2)."
            else -> "Siriri Keyboard is not enabled yet."
        }
        count.text = "Saved words: ${Native.wordCount()}"
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear saved words?")
            .setMessage("This permanently deletes every word Siriri Keyboard has learned from your typing.")
            .setPositiveButton("Clear") { _, _ ->
                Native.clearAll()
                Toast.makeText(this, "Saved words cleared", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Reads this app's own manifest at runtime so the claim is verifiable on-device. */
    @Suppress("DEPRECATION")
    private fun permissionLine(): String {
        val n = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.size ?: 0
        } catch (_: Exception) {
            -1
        }
        return when (n) {
            0 -> "✓ Verified: this app requests 0 permissions (no internet)."
            -1 -> "Could not read permissions."
            else -> "This app requests $n permissions."
        }
    }
}
