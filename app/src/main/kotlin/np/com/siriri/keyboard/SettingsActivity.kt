package np.com.siriri.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Minimal & Premium Settings Activity.
 * Designed with modern inset grouped cards, responsive typography, subtle elevation,
 * and seamless light/dark system integration — with zero third-party dependencies.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private var isDark = false

    // Color tokens
    private var colorBg = 0
    private var colorSurface = 0
    private var colorBorder = 0
    private var colorTextPrimary = 0
    private var colorTextSecondary = 0
    private var colorAccent = 0
    private var colorAccentTint = 0
    private var colorSuccess = 0
    private var colorSuccessTint = 0
    private var colorDanger = 0
    private var colorDangerTint = 0
    private var colorBadgeBg = 0
    private var colorBadgeText = 0

    // Dynamic views
    private lateinit var statusPill: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var wordCountLabel: TextView
    private val modeSwitches = HashMap<Mode, Switch>()

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun initColors() {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDark = night == Configuration.UI_MODE_NIGHT_YES

        if (isDark) {
            colorBg = 0xFF101114.toInt()
            colorSurface = 0xFF1A1B1F.toInt()
            colorBorder = 0xFF2A2C33.toInt()
            colorTextPrimary = 0xFFF9FAFB.toInt()
            colorTextSecondary = 0xFF94A3B8.toInt()
            colorAccent = 0xFF3B82F6.toInt()
            colorAccentTint = 0xFF1E293B.toInt()
            colorSuccess = 0xFF22C55E.toInt()
            colorSuccessTint = 0xFF143521.toInt()
            colorDanger = 0xFFEF4444.toInt()
            colorDangerTint = 0xFF2D1616.toInt()
            colorBadgeBg = 0xFF262830.toInt()
            colorBadgeText = 0xFFCBD5E1.toInt()
        } else {
            colorBg = 0xFFF5F6F9.toInt()
            colorSurface = 0xFFFFFFFF.toInt()
            colorBorder = 0xFFE5E7EB.toInt()
            colorTextPrimary = 0xFF0F172A.toInt()
            colorTextSecondary = 0xFF64748B.toInt()
            colorAccent = 0xFF2563EB.toInt()
            colorAccentTint = 0xFFEFF6FF.toInt()
            colorSuccess = 0xFF16A34A.toInt()
            colorSuccessTint = 0xFFDCFCE7.toInt()
            colorDanger = 0xFFDC2626.toInt()
            colorDangerTint = 0xFFFEE2E2.toInt()
            colorBadgeBg = 0xFFF1F5F9.toInt()
            colorBadgeText = 0xFF334155.toInt()
        }
    }

    private fun applySystemBars() {
        try {
            window.statusBarColor = colorBg
            window.navigationBarColor = colorBg
            if (Build.VERSION.SDK_INT >= 30) {
                val flags = if (isDark) 0 else (
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
                window.insetsController?.setSystemBarsAppearance(
                    flags,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                )
            } else {
                @Suppress("DEPRECATION")
                var flags = 0
                if (!isDark) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (Build.VERSION.SDK_INT >= 26) {
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                }
                window.decorView?.systemUiVisibility = flags
            }
        } catch (_: Throwable) {
        }
    }

    private fun rippleDrawable(bgColor: Int, radiusDp: Float): RippleDrawable {
        val content = GradientDrawable().apply {
            cornerRadius = dp(radiusDp)
            setColor(bgColor)
            setStroke(dp(1f).toInt().coerceAtLeast(1), colorBorder)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = dp(radiusDp)
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(if (isDark) 0x24FFFFFF else 0x1A000000)
        return RippleDrawable(rippleColor, content, mask)
    }

    private fun buttonRipple(bgColor: Int, radiusDp: Float): RippleDrawable {
        val content = GradientDrawable().apply {
            cornerRadius = dp(radiusDp)
            setColor(bgColor)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = dp(radiusDp)
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(if (isDark) 0x30FFFFFF else 0x22000000)
        return RippleDrawable(rippleColor, content, mask)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Native.init(filesDir.absolutePath)
        prefs = Prefs(this)

        initColors()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            val hPad = dp(16f).toInt()
            val vPad = dp(20f).toInt()
            setPadding(hPad, vPad, hPad, dp(40f).toInt())
        }

        buildHero(root)
        buildSetupSection(root)
        buildPrivacySection(root)
        buildFeedbackSection(root)
        buildLanguagesSection(root)
        buildVocabularySection(root)
        buildPlaygroundSection(root)
        buildFooter(root)

        val maxW = resources.displayMetrics.widthPixels.coerceAtMost(dp(580f).toInt())
        val container = FrameLayout(this).apply {
            setBackgroundColor(colorBg)
            addView(
                root,
                FrameLayout.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL),
            )
        }

        setContentView(
            ScrollView(this).apply {
                fitsSystemWindows = true
                isFillViewport = true
                setBackgroundColor(colorBg)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(container)
            },
        )
        applySystemBars()
    }

    private fun buildHero(parent: LinearLayout) {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8f).toInt(), 0, dp(22f).toInt())
        }

        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            val s = dp(64f).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                bottomMargin = dp(12f).toInt()
            }
        }
        hero.addView(icon)

        val title = TextView(this).apply {
            text = "Siriri Nepali Keyboard"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colorTextPrimary)
            gravity = Gravity.CENTER
        }
        hero.addView(title)

        val verName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.4"
        } catch (_: Throwable) {
            "1.0.4"
        }
        val subtitle = TextView(this).apply {
            text = "Privacy-First · Offline · v$verName"
            textSize = 13.5f
            setTextColor(colorTextSecondary)
            gravity = Gravity.CENTER
            setPadding(0, dp(4f).toInt(), 0, dp(12f).toInt())
        }
        hero.addView(subtitle)

        statusPill = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val hp = dp(14f).toInt()
            val vp = dp(6f).toInt()
            setPadding(hp, vp, hp, vp)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        hero.addView(statusPill)

        parent.addView(hero)
    }

    private fun buildSetupSection(parent: LinearLayout) {
        setupCard = card {
            addView(TextView(this@SettingsActivity).apply {
                text = "Setup Siriri Keyboard"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextPrimary)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "Complete these 2 quick steps to start typing:"
                textSize = 13.5f
                setTextColor(colorTextSecondary)
                setPadding(0, dp(2f).toInt(), 0, dp(12f).toInt())
            })

            addView(primaryButton("1. Enable in System Settings") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            })

            addView(outlinedButton("2. Select Siriri as Active Keyboard") {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            })
        }
        parent.addView(setupCard)
    }

    private fun buildPrivacySection(parent: LinearLayout) {
        parent.addView(sectionTitle("Privacy & Security"))
        parent.addView(card {
            val header = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val shield = TextView(this@SettingsActivity).apply {
                text = "🛡️"
                textSize = 22f
                setPadding(0, 0, dp(12f).toInt(), 0)
            }
            header.addView(shield)

            val titles = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@SettingsActivity).apply {
                    text = "100% Offline & On-Device"
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colorTextPrimary)
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = permissionTag()
                    textSize = 13f
                    setTextColor(colorSuccess)
                })
            }
            header.addView(titles)
            addView(header)

            addView(divider())

            addView(TextView(this@SettingsActivity).apply {
                text = "• Zero internet permissions: cannot connect to the web or send data.\n" +
                    "• Transliteration and word suggestions run 100% locally in Rust.\n" +
                    "• Passwords, numeric pins, and incognito fields are never learned."
                textSize = 13f
                setTextColor(colorTextSecondary)
                setLineSpacing(dp(3f), 1f)
            })

            addView(divider())

            addView(switchRow(
                title = "Learn words as I type",
                subtitle = "Tailors suggestions to your personal vocabulary",
                checked = prefs.learn,
            ) { prefs.learn = it })
        })
    }

    private fun buildFeedbackSection(parent: LinearLayout) {
        parent.addView(sectionTitle("Keyboard Feedback"))
        parent.addView(card {
            addView(switchRow(
                title = "Haptic feedback",
                subtitle = "Vibrate softly on key tap",
                checked = prefs.haptic,
            ) { prefs.haptic = it })

            addView(divider())

            addView(switchRow(
                title = "Sound on key press",
                subtitle = "Subtle click sound when pressing keys",
                checked = prefs.sound,
            ) { prefs.sound = it })
        })
    }

    private fun buildLanguagesSection(parent: LinearLayout) {
        parent.addView(sectionTitle("Typing Modes (En / ने / Hi / Np Key)"))
        parent.addView(card {
            val modes = listOf(
                Triple(Mode.ENGLISH, "English", "Standard Latin QWERTY layout"),
                Triple(Mode.NEPALI, "Traditional Nepali", "Devanagari layout with matras"),
                Triple(Mode.HINDI_ROMAN, "Hindi (Roman)", "Phonetic transliteration with nuktas"),
                Triple(Mode.NEPALI_ROMAN, "Roman Nepali", "Phonetic Roman to Devanagari"),
            )

            for ((i, item) in modes.withIndex()) {
                val (m, name, desc) = item
                if (i > 0) addView(divider())
                val sw = switchRow(
                    title = name,
                    subtitle = desc,
                    badge = m.badge,
                    checked = prefs.isModeEnabled(m),
                ) { enabled ->
                    prefs.setModeEnabled(m, enabled)
                    updateModeSwitches()
                }
                modeSwitches[m] = sw.findViewById(R_ID_SWITCH)
                addView(sw)
            }

            addView(divider())

            val tip = TextView(this@SettingsActivity).apply {
                text = "💡 Tip: Tap the language key (En/ने/Hi/Np) to cycle modes. Slide across the spacebar to move cursor."
                textSize = 12.5f
                setTextColor(colorTextSecondary)
            }
            addView(tip)
        })
    }

    private fun buildVocabularySection(parent: LinearLayout) {
        parent.addView(sectionTitle("Vocabulary & Learning"))
        parent.addView(card {
            val row = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val textCol = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@SettingsActivity).apply {
                    text = "Learned Words"
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colorTextPrimary)
                })
                wordCountLabel = TextView(this@SettingsActivity).apply {
                    text = "Saved words: ${Native.wordCount()}"
                    textSize = 13f
                    setTextColor(colorTextSecondary)
                    setPadding(0, dp(2f).toInt(), 0, 0)
                }
                addView(wordCountLabel)
            }
            row.addView(textCol)
            addView(row)

            addView(divider())

            addView(dangerButton("Clear Saved Words") {
                confirmClear()
            })
        })
    }

    private fun buildPlaygroundSection(parent: LinearLayout) {
        parent.addView(sectionTitle("Test Keyboard"))
        parent.addView(card {
            addView(TextView(this@SettingsActivity).apply {
                text = "Tap below to test typing, emoji panel, and suggestions:"
                textSize = 13.5f
                setTextColor(colorTextSecondary)
                setPadding(0, 0, 0, dp(10f).toInt())
            })

            val edit = EditText(this@SettingsActivity).apply {
                hint = "Type here to test..."
                textSize = 15.5f
                setTextColor(colorTextPrimary)
                setHintTextColor(colorTextSecondary)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 3
                gravity = Gravity.TOP
                background = GradientDrawable().apply {
                    cornerRadius = dp(12f)
                    setColor(if (isDark) 0xFF141519.toInt() else 0xFFF1F3F7.toInt())
                    setStroke(dp(1f).toInt().coerceAtLeast(1), colorBorder)
                }
                val p = dp(14f).toInt()
                setPadding(p, p, p, p)
            }
            addView(edit)
        })
    }

    private fun buildFooter(parent: LinearLayout) {
        val footer = TextView(this).apply {
            text = "Made with ❤️ for Nepal · Free & Open Source"
            textSize = 12f
            setTextColor(colorTextSecondary)
            gravity = Gravity.CENTER
            setPadding(0, dp(18f).toInt(), 0, dp(12f).toInt())
        }
        parent.addView(footer)
    }

    // ---- UI Component Helpers ----------------------------------------------------

    private fun sectionTitle(title: String): TextView = TextView(this).apply {
        text = title.uppercase()
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.07f
        setTextColor(colorTextSecondary)
        val l = dp(6f).toInt()
        val t = dp(18f).toInt()
        val b = dp(8f).toInt()
        setPadding(l, t, l, b)
    }

    private fun card(block: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16f)
            setColor(colorSurface)
            setStroke(dp(1f).toInt().coerceAtLeast(1), colorBorder)
        }
        val p = dp(16f).toInt()
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dp(6f).toInt()
        }
        block()
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(0.75f).toInt().coerceAtLeast(1),
        ).apply {
            setMargins(0, dp(12f).toInt(), 0, dp(12f).toInt())
        }
        setBackgroundColor(colorBorder)
    }

    private fun switchRow(
        title: String,
        subtitle: String? = null,
        badge: String? = null,
        checked: Boolean,
        onChecked: (Boolean) -> Unit,
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rippleDrawable(colorSurface, 10f)
            val hp = dp(4f).toInt()
            val vp = dp(4f).toInt()
            setPadding(hp, vp, hp, vp)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        if (badge != null) {
            val bView = TextView(this).apply {
                text = badge
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorBadgeText)
                background = GradientDrawable().apply {
                    cornerRadius = dp(6f)
                    setColor(colorBadgeBg)
                }
                gravity = Gravity.CENTER
                val bp = dp(6f).toInt()
                val bvp = dp(3f).toInt()
                setPadding(bp, bvp, bp, bvp)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    rightMargin = dp(12f).toInt()
                }
            }
            row.addView(bView)
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorTextPrimary)
            })
            if (subtitle != null) {
                addView(TextView(this@SettingsActivity).apply {
                    text = subtitle
                    textSize = 12.5f
                    setTextColor(colorTextSecondary)
                    setPadding(0, dp(2f).toInt(), 0, 0)
                })
            }
        }
        row.addView(textCol)

        val sw = Switch(this).apply {
            id = R_ID_SWITCH
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChecked(isChecked) }
        }
        row.addView(sw)

        row.setOnClickListener { sw.toggle() }
        return row
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        background = buttonRipple(colorAccent, 12f)
        val hp = dp(16f).toInt()
        val vp = dp(12f).toInt()
        setPadding(hp, vp, hp, vp)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dp(8f).toInt()
        }
        setOnClickListener { onClick() }
    }

    private fun outlinedButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(colorTextPrimary)
        gravity = Gravity.CENTER
        background = rippleDrawable(colorSurface, 12f)
        val hp = dp(16f).toInt()
        val vp = dp(12f).toInt()
        setPadding(hp, vp, hp, vp)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setOnClickListener { onClick() }
    }

    private fun dangerButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(colorDanger)
        gravity = Gravity.CENTER
        background = buttonRipple(colorDangerTint, 12f)
        val hp = dp(16f).toInt()
        val vp = dp(11f).toInt()
        setPadding(hp, vp, hp, vp)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setOnClickListener { onClick() }
    }

    // ---- Lifecycle & State -------------------------------------------------------

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true

        if (current) {
            statusPill.text = "● Active Keyboard"
            statusPill.setTextColor(colorSuccess)
            statusPill.background = GradientDrawable().apply {
                cornerRadius = dp(16f)
                setColor(colorSuccessTint)
            }
            setupCard.visibility = View.GONE
        } else if (enabled) {
            statusPill.text = "⚠️ Enabled · Switch Required"
            statusPill.setTextColor(colorAccent)
            statusPill.background = GradientDrawable().apply {
                cornerRadius = dp(16f)
                setColor(colorAccentTint)
            }
            setupCard.visibility = View.VISIBLE
        } else {
            statusPill.text = "⚠️ Setup Incomplete"
            statusPill.setTextColor(0xFFD97706.toInt())
            statusPill.background = GradientDrawable().apply {
                cornerRadius = dp(16f)
                setColor(if (isDark) 0xFF2B2010.toInt() else 0xFFFEF3C7.toInt())
            }
            setupCard.visibility = View.VISIBLE
        }

        wordCountLabel.text = "Saved words: ${Native.wordCount()}"
    }

    private fun updateModeSwitches() {
        for ((mode, sw) in modeSwitches) {
            val actual = prefs.isModeEnabled(mode)
            if (sw.isChecked != actual) sw.isChecked = actual
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear Learned Words?")
            .setMessage("This permanently removes all words Siriri has learned from your typing on this device.")
            .setPositiveButton("Clear") { _, _ ->
                Native.clearAll()
                Toast.makeText(this, "Learned vocabulary cleared", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun permissionTag(): String {
        val n = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.size ?: 0
        } catch (_: Exception) {
            -1
        }
        return if (n == 0) "✓ Verified: 0 permissions requested" else "Requests $n permissions"
    }

    companion object {
        private const val R_ID_SWITCH = 0x7F0A0001
    }
}
