package np.com.siriri.keyboard

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Minimal, calm Settings screen built with pure Android views and a custom
 * animated toggle — zero third-party dependencies.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private var isDark = false

    // Refined neutral + calm accent palette
    private var colorBg = 0
    private var colorSurface = 0
    private var colorHairline = 0
    private var colorInputBg = 0
    private var colorTextPrimary = 0
    private var colorTextSecondary = 0
    private var colorTextMuted = 0
    private var colorAccent = 0
    private var colorSuccess = 0
    private var colorSuccessTint = 0
    private var colorWarning = 0
    private var colorWarningTint = 0
    private var colorDanger = 0
    private var colorBadgeBg = 0
    private var colorBadgeText = 0
    private var colorToggleOff = 0
    private var colorToggleOn = 0

    private val fontMedium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val fontRegular: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }

    // Dynamic views
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var statusPill: LinearLayout
    private lateinit var setupContainer: LinearLayout
    private lateinit var wordCountLabel: TextView
    private lateinit var clearWordsBtn: TextView
    private val modeToggles = HashMap<Mode, MinimalToggle>()

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun dpInt(v: Float): Int = dp(v).toInt()

    private fun initColors() {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDark = night == Configuration.UI_MODE_NIGHT_YES

        if (isDark) {
            colorBg = 0xFF0E0F12.toInt()
            colorSurface = 0xFF17181C.toInt()
            colorHairline = 0xFF25272E.toInt()
            colorInputBg = 0xFF121317.toInt()
            colorTextPrimary = 0xFFF4F5F7.toInt()
            colorTextSecondary = 0xFF9CA3AF.toInt()
            colorTextMuted = 0xFF6B7280.toInt()
            colorAccent = 0xFF3B82F6.toInt()
            colorSuccess = 0xFF34D399.toInt()
            colorSuccessTint = 0xFF122922.toInt()
            colorWarning = 0xFFFBBF24.toInt()
            colorWarningTint = 0xFF292112.toInt()
            colorDanger = 0xFFF87171.toInt()
            colorBadgeBg = 0xFF23252C.toInt()
            colorBadgeText = 0xFFD1D5DB.toInt()
            colorToggleOff = 0xFF2E313A.toInt()
            colorToggleOn = 0xFF3B82F6.toInt()
        } else {
            colorBg = 0xFFF6F6F8.toInt()
            colorSurface = 0xFFFFFFFF.toInt()
            colorHairline = 0xFFECEEF2.toInt()
            colorInputBg = 0xFFF7F8FA.toInt()
            colorTextPrimary = 0xFF111318.toInt()
            colorTextSecondary = 0xFF6B7280.toInt()
            colorTextMuted = 0xFF9CA3AF.toInt()
            colorAccent = 0xFF111318.toInt()
            colorSuccess = 0xFF059669.toInt()
            colorSuccessTint = 0xFFECFDF5.toInt()
            colorWarning = 0xFFD97706.toInt()
            colorWarningTint = 0xFFFFFBEB.toInt()
            colorDanger = 0xFFDC2626.toInt()
            colorBadgeBg = 0xFFF3F4F6.toInt()
            colorBadgeText = 0xFF374151.toInt()
            colorToggleOff = 0xFFDCDFE6.toInt()
            colorToggleOn = 0xFF111318.toInt()
        }
    }

    private fun applySystemBars() {
        try {
            @Suppress("DEPRECATION")
            window.statusBarColor = colorBg
            @Suppress("DEPRECATION")
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
                @Suppress("DEPRECATION")
                window.decorView?.systemUiVisibility = flags
            }
        } catch (_: Throwable) {
        }
    }

    private fun rowRipple(): RippleDrawable {
        val rippleColor = ColorStateList.valueOf(if (isDark) 0x18FFFFFF else 0x0E000000)
        val mask = GradientDrawable().apply { setColor(Color.WHITE) }
        return RippleDrawable(rippleColor, null, mask)
    }

    private fun pillRipple(bgColor: Int, radiusDp: Float, strokeColor: Int? = null): RippleDrawable {
        val content = GradientDrawable().apply {
            cornerRadius = dp(radiusDp)
            setColor(bgColor)
            if (strokeColor != null) setStroke(dpInt(1f).coerceAtLeast(1), strokeColor)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = dp(radiusDp)
            setColor(Color.WHITE)
        }
        val rippleColor = ColorStateList.valueOf(if (isDark) 0x26FFFFFF else 0x16000000)
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
            val hPad = dpInt(20f)
            setPadding(hPad, dpInt(24f), hPad, dpInt(36f))
        }

        buildHeader(root)
        buildSetupSection(root)
        buildLanguagesSection(root)
        buildPreferencesSection(root)
        buildPrivacySection(root)
        buildPlaygroundSection(root)
        buildFooter(root)

        val maxW = resources.displayMetrics.widthPixels.coerceAtMost(dpInt(560f))
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
                isVerticalScrollBarEnabled = false
                addView(container)
            },
        )
        applySystemBars()
    }

    // ---- Sections ----------------------------------------------------------------

    private fun buildHeader(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpInt(2f), dpInt(4f), dpInt(2f), dpInt(20f))
        }

        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            val s = dpInt(48f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                rightMargin = dpInt(14f)
            }
        }
        row.addView(icon)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@SettingsActivity).apply {
                text = "Siriri Keyboard"
                textSize = 20f
                typeface = fontMedium
                letterSpacing = -0.02f
                setTextColor(colorTextPrimary)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "Nepali · Hindi · English"
                textSize = 13f
                typeface = fontRegular
                setTextColor(colorTextSecondary)
                setPadding(0, dpInt(1f), 0, 0)
            })
        }
        row.addView(textCol)

        // Minimal status badge with dot
        statusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpInt(10f), dpInt(5f), dpInt(11f), dpInt(5f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        statusDot = View(this).apply {
            val d = dpInt(7f)
            layoutParams = LinearLayout.LayoutParams(d, d).apply {
                rightMargin = dpInt(6f)
            }
        }
        statusLabel = TextView(this).apply {
            textSize = 12f
            typeface = fontMedium
        }
        statusPill.addView(statusDot)
        statusPill.addView(statusLabel)
        row.addView(statusPill)

        parent.addView(row)
    }

    private fun buildSetupSection(parent: LinearLayout) {
        setupContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dpInt(8f)
            }
        }

        val cardView = card(padded = true) {
            addView(TextView(this@SettingsActivity).apply {
                text = "Get started"
                textSize = 15.5f
                typeface = fontMedium
                setTextColor(colorTextPrimary)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "Enable Siriri in system settings, then switch to it."
                textSize = 13f
                typeface = fontRegular
                setTextColor(colorTextSecondary)
                setPadding(0, dpInt(3f), 0, dpInt(14f))
            })

            addView(primaryButton("Enable Keyboard") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            })

            addView(secondaryButton("Switch to Siriri") {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            })
        }
        setupContainer.addView(cardView)
        parent.addView(setupContainer)
    }

    private fun buildLanguagesSection(parent: LinearLayout) {
        parent.addView(sectionLabel("Languages"))
        parent.addView(card(padded = false) {
            val modes = listOf(
                Triple(Mode.ENGLISH, "English", "QWERTY layout"),
                Triple(Mode.NEPALI, "Nepali", "Traditional Devanagari"),
                Triple(Mode.HINDI_ROMAN, "Hindi", "Roman transliteration"),
                Triple(Mode.NEPALI_ROMAN, "Roman Nepali", "Phonetic Devanagari"),
            )

            for ((i, item) in modes.withIndex()) {
                val (m, name, desc) = item
                if (i > 0) addView(insetDivider(dpInt(68f)))
                val (row, toggle) = toggleRow(
                    title = name,
                    subtitle = desc,
                    badge = m.badge,
                    checked = prefs.isModeEnabled(m),
                ) { enabled ->
                    prefs.setModeEnabled(m, enabled)
                    updateModeToggles()
                }
                modeToggles[m] = toggle
                addView(row)
            }
        })
        parent.addView(sectionFootnote("Tap the language key to cycle enabled modes. Slide the spacebar to move the cursor."))
    }

    private fun buildPreferencesSection(parent: LinearLayout) {
        parent.addView(sectionLabel("Typing"))
        parent.addView(card(padded = false) {
            addView(toggleRow(
                title = "Haptic feedback",
                subtitle = "Soft vibration on key press",
                checked = prefs.haptic,
            ) { prefs.haptic = it }.first)

            addView(insetDivider(dpInt(18f)))

            addView(toggleRow(
                title = "Key press sound",
                subtitle = "Subtle tap click",
                checked = prefs.sound,
            ) { prefs.sound = it }.first)

            addView(insetDivider(dpInt(18f)))

            addView(toggleRow(
                title = "Personal vocabulary",
                subtitle = "Learn words as you type",
                checked = prefs.learn,
            ) { prefs.learn = it }.first)
        })
    }

    private fun buildPrivacySection(parent: LinearLayout) {
        parent.addView(sectionLabel("Privacy & Data"))
        parent.addView(card(padded = false) {
            // Offline guarantee row
            val privacyRow = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpInt(18f), dpInt(14f), dpInt(18f), dpInt(14f))
            }
            val privacyText = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dpInt(12f)
                }
                addView(TextView(this@SettingsActivity).apply {
                    text = "Offline by design"
                    textSize = 15f
                    typeface = fontMedium
                    setTextColor(colorTextPrimary)
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = "Runs locally on-device. Passwords and incognito fields are never learned."
                    textSize = 12.5f
                    typeface = fontRegular
                    setTextColor(colorTextSecondary)
                    setLineSpacing(dp(2f), 1f)
                    setPadding(0, dpInt(2f), 0, 0)
                })
            }
            privacyRow.addView(privacyText)

            val permBadge = TextView(this@SettingsActivity).apply {
                text = permissionTag()
                textSize = 11.5f
                typeface = fontMedium
                setTextColor(colorSuccess)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12f)
                    setColor(colorSuccessTint)
                }
                setPadding(dpInt(9f), dpInt(4f), dpInt(9f), dpInt(4f))
            }
            privacyRow.addView(permBadge)
            addView(privacyRow)

            addView(insetDivider(dpInt(18f)))

            // Learned words inline row with minimal Clear action
            val vocabRow = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpInt(18f), dpInt(14f), dpInt(14f), dpInt(14f))
            }
            val vocabText = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@SettingsActivity).apply {
                    text = "Learned words"
                    textSize = 15f
                    typeface = fontMedium
                    setTextColor(colorTextPrimary)
                })
                wordCountLabel = TextView(this@SettingsActivity).apply {
                    text = "${Native.wordCount()} saved on this device"
                    textSize = 12.5f
                    typeface = fontRegular
                    setTextColor(colorTextSecondary)
                    setPadding(0, dpInt(2f), 0, 0)
                }
                addView(wordCountLabel)
            }
            vocabRow.addView(vocabText)

            clearWordsBtn = TextView(this@SettingsActivity).apply {
                text = "Clear"
                textSize = 13.5f
                typeface = fontMedium
                setTextColor(colorDanger)
                background = pillRipple(Color.TRANSPARENT, 8f)
                setPadding(dpInt(12f), dpInt(7f), dpInt(12f), dpInt(7f))
                setOnClickListener { confirmClear() }
            }
            vocabRow.addView(clearWordsBtn)
            addView(vocabRow)
        })
    }

    private fun buildPlaygroundSection(parent: LinearLayout) {
        parent.addView(sectionLabel("Try Keyboard"))
        parent.addView(card(padded = true) {
            val edit = EditText(this@SettingsActivity).apply {
                hint = "Type here to test..."
                textSize = 15f
                typeface = fontRegular
                setTextColor(colorTextPrimary)
                setHintTextColor(colorTextMuted)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 3
                gravity = Gravity.TOP
                background = GradientDrawable().apply {
                    cornerRadius = dp(12f)
                    setColor(colorInputBg)
                    setStroke(dpInt(1f).coerceAtLeast(1), colorHairline)
                }
                val p = dpInt(14f)
                setPadding(p, p, p, p)
            }
            addView(edit)
        })
    }

    private fun buildFooter(parent: LinearLayout) {
        val verName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.4"
        } catch (_: Throwable) {
            "1.0.4"
        }
        val footer = TextView(this).apply {
            text = "Siriri Keyboard · v$verName · Open Source"
            textSize = 12f
            typeface = fontRegular
            setTextColor(colorTextMuted)
            gravity = Gravity.CENTER
            setPadding(0, dpInt(22f), 0, dpInt(8f))
        }
        parent.addView(footer)
    }

    // ---- UI Component Helpers ----------------------------------------------------

    private fun sectionLabel(title: String): TextView = TextView(this).apply {
        text = title.uppercase()
        textSize = 11.5f
        typeface = fontMedium
        letterSpacing = 0.08f
        setTextColor(colorTextSecondary)
        setPadding(dpInt(4f), dpInt(20f), dpInt(4f), dpInt(8f))
    }

    private fun sectionFootnote(textStr: String): TextView = TextView(this).apply {
        text = textStr
        textSize = 12f
        typeface = fontRegular
        setTextColor(colorTextMuted)
        setLineSpacing(dp(2f), 1f)
        setPadding(dpInt(4f), dpInt(8f), dpInt(4f), dpInt(2f))
    }

    private fun card(padded: Boolean, block: LinearLayout.() -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16f)
            setColor(colorSurface)
            setStroke(dpInt(0.8f).coerceAtLeast(1), colorHairline)
        }
        clipToOutline = true
        if (padded) {
            val p = dpInt(16f)
            setPadding(p, p, p, p)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        block()
    }

    private fun insetDivider(startMarginPx: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dpInt(0.75f).coerceAtLeast(1),
        ).apply {
            leftMargin = startMarginPx
            rightMargin = dpInt(18f)
        }
        setBackgroundColor(colorHairline)
    }

    private fun toggleRow(
        title: String,
        subtitle: String? = null,
        badge: String? = null,
        checked: Boolean,
        onChecked: (Boolean) -> Unit,
    ): Pair<LinearLayout, MinimalToggle> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rowRipple()
            setPadding(dpInt(18f), dpInt(14f), dpInt(18f), dpInt(14f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        if (badge != null) {
            val bView = TextView(this).apply {
                text = badge
                textSize = 12.5f
                typeface = fontMedium
                setTextColor(colorBadgeText)
                background = GradientDrawable().apply {
                    cornerRadius = dp(8f)
                    setColor(colorBadgeBg)
                }
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dpInt(36f), dpInt(28f)).apply {
                    rightMargin = dpInt(14f)
                }
            }
            row.addView(bView)
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dpInt(12f)
            }
            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 15f
                typeface = fontMedium
                setTextColor(colorTextPrimary)
            })
            if (subtitle != null) {
                addView(TextView(this@SettingsActivity).apply {
                    text = subtitle
                    textSize = 12.5f
                    typeface = fontRegular
                    setTextColor(colorTextSecondary)
                    setPadding(0, dpInt(1.5f), 0, 0)
                })
            }
        }
        row.addView(textCol)

        val toggle = MinimalToggle(this, colorToggleOff, colorToggleOn).apply {
            setChecked(checked, animate = false)
            onChanged = onChecked
        }
        row.addView(toggle)

        row.setOnClickListener { toggle.toggle() }
        return Pair(row, toggle)
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14.5f
        typeface = fontMedium
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = pillRipple(colorAccent, 12f)
        setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(12f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dpInt(8f)
        }
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 14.5f
        typeface = fontMedium
        setTextColor(colorTextPrimary)
        gravity = Gravity.CENTER
        background = pillRipple(colorSurface, 12f, strokeColor = colorHairline)
        setPadding(dpInt(16f), dpInt(12f), dpInt(16f), dpInt(12f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setOnClickListener { onClick() }
    }

    // ---- Custom Minimal Animated Toggle ------------------------------------------

    private class MinimalToggle(
        context: Context,
        private val offColor: Int,
        private val onColor: Int,
    ) : View(context) {

        private val d = resources.displayMetrics.density
        private val wPx = (44f * d).toInt()
        private val hPx = (26f * d).toInt()
        private val pad = 3f * d
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val trackRect = RectF()

        var isChecked: Boolean = false
            private set
        private var progress: Float = 0f
        private var anim: ValueAnimator? = null
        var onChanged: ((Boolean) -> Unit)? = null

        init {
            isClickable = true
            setOnClickListener { toggle() }
        }

        fun toggle() {
            setChecked(!isChecked, animate = true)
            onChanged?.invoke(isChecked)
        }

        fun setChecked(checked: Boolean, animate: Boolean) {
            if (isChecked == checked && anim == null) {
                progress = if (checked) 1f else 0f
                invalidate()
                return
            }
            isChecked = checked
            val target = if (checked) 1f else 0f
            anim?.cancel()
            if (animate) {
                anim = ValueAnimator.ofFloat(progress, target).apply {
                    duration = 180L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        progress = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            } else {
                progress = target
                invalidate()
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(wPx, hPx)
        }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            trackRect.set(0f, 0f, width.toFloat(), height.toFloat())
            trackPaint.color = blendColor(offColor, onColor, progress)
            canvas.drawRoundRect(trackRect, r, r, trackPaint)

            val thumbR = r - pad
            val minX = pad + thumbR
            val maxX = width - pad - thumbR
            val cx = minX + (maxX - minX) * progress
            canvas.drawCircle(cx, r, thumbR, thumbPaint)
        }

        private fun blendColor(a: Int, b: Int, t: Float): Int {
            val inv = 1f - t
            val r = (Color.red(a) * inv + Color.red(b) * t).toInt()
            val g = (Color.green(a) * inv + Color.green(b) * t).toInt()
            val bl = (Color.blue(a) * inv + Color.blue(b) * t).toInt()
            return Color.rgb(r, g, bl)
        }
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

        val dotColor: Int
        val pillBg: Int
        val textColor: Int
        val label: String

        if (current) {
            label = "Active"
            dotColor = colorSuccess
            textColor = colorSuccess
            pillBg = colorSuccessTint
            setupContainer.visibility = View.GONE
        } else if (enabled) {
            label = "Not selected"
            dotColor = colorWarning
            textColor = colorWarning
            pillBg = colorWarningTint
            setupContainer.visibility = View.VISIBLE
        } else {
            label = "Setup needed"
            dotColor = colorWarning
            textColor = colorWarning
            pillBg = colorWarningTint
            setupContainer.visibility = View.VISIBLE
        }

        statusLabel.text = label
        statusLabel.setTextColor(textColor)
        statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }
        statusPill.background = GradientDrawable().apply {
            cornerRadius = dp(14f)
            setColor(pillBg)
        }

        val count = Native.wordCount()
        wordCountLabel.text = if (count == 1) "1 word saved on this device" else "$count words saved on this device"
        clearWordsBtn.alpha = if (count > 0) 1f else 0.4f
        clearWordsBtn.isEnabled = count > 0
    }

    private fun updateModeToggles() {
        for ((mode, toggle) in modeToggles) {
            val actual = prefs.isModeEnabled(mode)
            if (toggle.isChecked != actual) toggle.setChecked(actual, animate = true)
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear learned words?")
            .setMessage("This removes all words Siriri has learned from your typing on this device.")
            .setPositiveButton("Clear") { _, _ ->
                Native.clearAll()
                Toast.makeText(this, "Learned words cleared", Toast.LENGTH_SHORT).show()
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
        return if (n == 0) "0 permissions" else "$n permissions"
    }
}
