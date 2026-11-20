package np.com.siriri.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseArray
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.VelocityTracker
import android.view.View
import android.view.WindowInsets
import android.widget.OverScroller

/**
 * The whole keyboard is ONE custom view drawn straight onto a Canvas: no child
 * views, no inflation, no layout passes while typing. Keys fire on touch-down
 * (like iOS) and every pointer is tracked independently for fast two-thumb typing.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(text: String)
        fun onLongChar(text: String)
        fun onShift()
        fun onBackspace()
        fun onPage(page: Int)
        fun onEmojiToggle()
        fun onEmojiPick(emoji: String)
        fun onGlobe()
        fun onGlobeLong()
        fun onSpace()
        fun onCursorMove(delta: Int)
        fun onReturn()
        fun onSuggestion(index: Int)
        fun onSuggestionLong(index: Int)
        fun onBackspaceWord()
        fun onClipboardPaste()
        fun onClipboardDismiss()
    }

    var listener: Listener? = null
    var haptics = true
    var sound = true

    // ---- state pushed in by the service --------------------------------------
    private var theme = KeyTheme.forContext(context)
    private var mode = Mode.ENGLISH
    private var page = 0
    private var layout = Layout.EMPTY
    private var shift = ShiftState.OFF
    private var retStyle = ReturnStyle.NEUTRAL
    private var retGlyph = ReturnGlyph.ENTER
    private var suggestions = arrayOf("", "", "")
    private var barVisible = false
    private var emojiOpen = false
    private var emojiCategoryIndex = 0
    private var recentEmojis = EmojiCatalog.DEFAULT_RECENTS
    private var emojiScrollY = 0f
    private var leftInset = 0
    private var rightInset = 0
    private var bottomInset = 0
    private val layoutCache = HashMap<Int, Layout>()

    private data class Metrics(
        val landscape: Boolean,
        val tablet: Boolean,
        val largeTablet: Boolean,
        val rowH: Float,
        val vGap: Float,
        val hGap: Float,
        val topPad: Float,
        val bottomPad: Float,
        val barHeight: Float,
        val keyRadius: Float,
        val containerRadius: Float,
        val latinTextSp: Float,
        val devaTextSp: Float,
        val modTextSp: Float,
        val hintTextSp: Float,
        val suggestionSp: Float,
        val popupTextSp: Float,
        val emojiCellH: Float,
        val emojiFontSp: Float,
        val contentLeft: Float,
        val contentRight: Float,
    ) {
        val contentWidth: Float get() = (contentRight - contentLeft).coerceAtLeast(1f)
    }

    private data class PopupPreview(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val label: String,
    )

    private var lingerPopup: PopupPreview? = null
    private val clearLingerRunnable = Runnable {
        lingerPopup = null
        invalidate()
    }

    // ---- metrics (px, adapted dynamically to phone/landscape/7"-11" tablets) -
    private val density get() = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private var m = computeMetrics(0)
    private val rowH get() = m.rowH
    private val vGap get() = m.vGap
    private val hGap get() = m.hGap
    private val topPad get() = m.topPad
    private val bottomPad get() = m.bottomPad
    private val keyRadius get() = m.keyRadius
    private val containerRadius get() = m.containerRadius
    private val barHeight get() = m.barHeight
    private val shadowDy get() = dp(1f)

    private fun computeMetrics(viewWidthPx: Int): Metrics {
        val dm = resources.displayMetrics
        val cfg = resources.configuration
        val d = dm.density
        val wPx = (if (viewWidthPx > 0) viewWidthPx else dm.widthPixels).toFloat()
        val hPx = dm.heightPixels.toFloat()
        val wDp = wPx / d
        val swDp = cfg.smallestScreenWidthDp
        val landscape = dm.widthPixels > dm.heightPixels
        val largeTablet = swDp >= 700 && wDp >= 680f
        val tablet = !largeTablet && swDp >= 580 && wDp >= 540f

        var rowH = dp(
            when {
                largeTablet -> if (landscape) 52f else 56f
                tablet -> if (landscape) 46f else 52f
                landscape -> 35f
                else -> 46f
            },
        )
        val vGap = dp(
            when {
                largeTablet -> 10f
                tablet -> if (landscape) 8f else 9f
                landscape -> 5f
                else -> 9f
            },
        )
        val hGap = dp(
            when {
                largeTablet -> 8f
                tablet -> 6.5f
                landscape -> 5.5f
                else -> 5f
            },
        )
        val topPad = dp(if (largeTablet) 10f else if (landscape && !tablet) 5f else 8f)
        val bottomPad = dp(if (largeTablet) 8f else if (landscape && !tablet) 4f else 6f)
        val barH = dp(
            when {
                largeTablet -> if (landscape) 44f else 48f
                tablet -> if (landscape) 40f else 42f
                landscape -> 34f
                else -> 40f
            },
        )

        // Cap vertical keyboard footprint so landscape / split-screen editors stay usable.
        val rows = layout.rows.size.coerceAtLeast(4)
        val maxRatio = if (landscape && !tablet && !largeTablet) 0.50f else 0.46f
        val activeBar = if (barVisible || emojiOpen) barH else 0f
        val nonRowH = activeBar + topPad + (rows - 1) * vGap + bottomPad + bottomInset
        val maxRowsH = (hPx * maxRatio - nonRowH).coerceAtLeast(rows * dp(28f))
        if (rows * rowH > maxRowsH) {
            rowH = (maxRowsH / rows).coerceAtLeast(dp(28f))
        }

        val sidePad = dp(
            when {
                largeTablet -> if (landscape) 24f else 16f
                tablet -> if (landscape) 18f else 10f
                landscape -> 10f
                else -> 3f
            },
        )
        val maxContentW = dp(
            when {
                largeTablet -> if (landscape) 860f else 740f
                tablet -> if (landscape) 760f else 620f
                landscape -> 680f
                else -> 600f
            },
        )
        val availLeft = maxOf(sidePad, leftInset.toFloat() + dp(4f))
        val availRight = (wPx - maxOf(sidePad, rightInset.toFloat() + dp(4f))).coerceAtLeast(availLeft + dp(200f))
        val availW = (availRight - availLeft).coerceAtLeast(1f)
        val contentW = availW.coerceAtMost(maxContentW)
        val contentLeft = availLeft + (availW - contentW) / 2f
        val contentRight = contentLeft + contentW

        return Metrics(
            landscape = landscape,
            tablet = tablet,
            largeTablet = largeTablet,
            rowH = rowH,
            vGap = vGap,
            hGap = hGap,
            topPad = topPad,
            bottomPad = bottomPad,
            barHeight = barH,
            keyRadius = dp(if (largeTablet) 8f else if (tablet) 7f else if (landscape) 5.5f else 6f),
            containerRadius = dp(if (largeTablet || tablet) 22f else if (landscape) 14f else 20f),
            latinTextSp = if (largeTablet) 24f else if (tablet) 23f else if (landscape) 19f else 22f,
            devaTextSp = if (largeTablet) 22f else if (tablet) 21f else if (landscape) 17.5f else 20f,
            modTextSp = if (largeTablet) 18f else if (tablet) 17f else if (landscape) 14.5f else 16f,
            hintTextSp = if (largeTablet) 11f else if (tablet) 10.5f else if (landscape) 8.5f else 9.5f,
            suggestionSp = if (largeTablet) 19f else if (tablet) 18f else if (landscape) 15.5f else 17f,
            popupTextSp = if (largeTablet) 32f else if (tablet) 30f else if (landscape) 24f else 28f,
            emojiCellH = dp(if (largeTablet) 48f else if (tablet) 46f else if (landscape) 36f else 44f),
            emojiFontSp = if (largeTablet) 28f else if (tablet) 26f else if (landscape) 21.5f else 25f,
            contentLeft = contentLeft,
            contentRight = contentRight,
        )
    }

    fun onConfigChanged() {
        m = computeMetrics(width)
        stroke.strokeWidth = dp(if (m.largeTablet) 2.1f else 1.8f)
        layoutKeys()
        requestLayout()
        invalidate()
    }

    // ---- reusable drawing objects --------------------------------------------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val suggestionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(1.8f)
    }
    private val rect = RectF()
    private val path = Path()
    private val containerPath = Path()

    // ---- touch tracking --------------------------------------------------------
    private val pressed = SparseArray<KeyDef>()
    private val barDown = SparseArray<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var repeatPointer = -1
    private var repeatStartTime = 0L
    private var globePointer = -1
    private var globeLongFired = false
    private var barLongPointer = -1
    private var barLongFired = false
    private var charLongPointer = -1
    private var charLongFired = false
    private var spacePointer = -1
    private var spaceLastX = 0f
    private var spaceSlid = false
    private var clipboardCandidate: String? = null
    private var clipboardDown = false
    private var tappedSuggestionSlot = -1
    private val clearTapFeedbackRunnable = Runnable {
        tappedSuggestionSlot = -1
        invalidate()
    }

    // ---- emoji touch tracking ------------------------------------------------
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                emojiScrollY = scroller.currY.toFloat().coerceIn(0f, emojiMaxScroll())
                invalidate()
                if (!scroller.isFinished) {
                    postOnAnimation(this)
                }
            }
        }
    }
    private var emojiGridPointer = -1
    private var emojiTouchStartY = 0f
    private var emojiLastY = 0f
    private var emojiScrolling = false
    private var emojiDownIndex = -1
    private val emojiBottomAbc = KeyDef(KeyKind.ABC, "ABC", "", 1.4f)
    private val emojiBottomGlobe = KeyDef(KeyKind.GLOBE, "", "", 1.2f)
    private val emojiBottomSpace = KeyDef(KeyKind.SPACE, "", "", 5.4f)
    private val emojiBottomBksp = KeyDef(KeyKind.BKSP, "", "", 2.0f)

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.uptimeMillis() - repeatStartTime
            if (elapsed > 1200L) {
                if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener?.onBackspaceWord()
                handler.postDelayed(this, 150L)
            } else {
                listener?.onBackspace()
                handler.postDelayed(this, 45L)
            }
        }
    }
    private val globeLongRunnable = Runnable {
        globeLongFired = true
        listener?.onGlobeLong()
    }
    private val barLongRunnable = Runnable {
        val idx = barDown.get(barLongPointer, -1)
        if (idx >= 0) {
            barLongFired = true
            if (haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onSuggestionLong(idx)
        }
    }
    private val charLongRunnable = Runnable {
        val k = pressed.get(charLongPointer)
        if (k != null && k.hint.isNotEmpty()) {
            charLongFired = true
            if (haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onLongChar(k.hint)
            invalidate()
        }
    }

    // ---- public setters (cheap; only redraw/relayout when something changed) -----

    fun setTheme(t: KeyTheme) {
        theme = t
        invalidate()
    }

    fun setMode(m: Mode, p: Int) {
        if (m == mode && p == page && layout !== Layout.EMPTY) return
        mode = m
        page = p
        layout = layoutCache.getOrPut(m.id * 3 + p) { Layout.parse(Native.layout(m.id, p), m, p) }
        this.m = computeMetrics(width)
        layoutKeys()
        requestLayout()
        invalidate()
    }

    fun setShift(s: ShiftState) {
        if (s != shift) {
            shift = s
            invalidate()
        }
    }

    fun setReturn(style: ReturnStyle, glyph: ReturnGlyph) {
        if (style != retStyle || glyph != retGlyph) {
            retStyle = style
            retGlyph = glyph
            invalidate()
        }
    }

    fun setSuggestions(list: List<String>) {
        var changed = false
        for (i in 0..2) {
            val s = list.getOrElse(i) { "" }
            if (suggestions[i] != s) {
                suggestions[i] = s
                changed = true
            }
        }
        if (changed && barVisible) invalidate()
    }

    fun suggestionAt(i: Int): String = suggestions.getOrElse(i) { "" }

    fun setClipboardCandidate(candidate: String?) {
        if (clipboardCandidate != candidate) {
            clipboardCandidate = candidate
            clipboardDown = false
            if (barVisible) invalidate()
        }
    }

    fun setBarVisible(v: Boolean) {
        if (v == barVisible) return
        barVisible = v
        m = computeMetrics(width)
        layoutKeys()
        requestLayout()
        invalidate()
    }

    fun setEmojiOpen(open: Boolean, recents: List<String> = recentEmojis) {
        recentEmojis = if (recents.isEmpty()) EmojiCatalog.DEFAULT_RECENTS else recents
        val changed = emojiOpen != open
        emojiOpen = open
        if (changed) {
            scroller.abortAnimation()
            removeCallbacks(flingRunnable)
            velocityTracker?.recycle()
            velocityTracker = null
            emojiScrollY = 0f
            emojiDownIndex = -1
            emojiGridPointer = -1
            emojiScrolling = false
            pressed.clear()
            lingerPopup = null
            handler.removeCallbacks(clearLingerRunnable)
            m = computeMetrics(width)
            layoutKeys()
            requestLayout()
        }
        invalidate()
    }

    fun setRecentEmojis(recents: List<String>) {
        recentEmojis = if (recents.isEmpty()) EmojiCatalog.DEFAULT_RECENTS else recents
        if (emojiOpen && emojiCategoryIndex == 0) invalidate()
    }

    // ---- measuring & geometry ------------------------------------------------------

    private fun barH() = if (barVisible || emojiOpen) barHeight else 0f

    private fun keysHeight(): Float {
        val n = layout.rows.size.coerceAtLeast(4)
        return topPad + n * rowH + (n - 1) * vGap + bottomPad + bottomInset
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val (l, r, b) = if (Build.VERSION.SDK_INT >= 30) {
            val ins = insets.getInsets(
                WindowInsets.Type.systemBars() or
                    WindowInsets.Type.displayCutout() or
                    WindowInsets.Type.mandatorySystemGestures() or
                    WindowInsets.Type.tappableElement(),
            )
            var bottom = ins.bottom
            if (bottom == 0 && Build.VERSION.SDK_INT >= 35) {
                bottom = rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: 0
            }
            Triple(ins.left, ins.right, bottom)
        } else {
            @Suppress("DEPRECATION")
            Triple(insets.systemWindowInsetLeft, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        if (l != leftInset || r != rightInset || b != bottomInset) {
            leftInset = l
            rightInset = r
            bottomInset = b
            m = computeMetrics(width)
            layoutKeys()
            requestLayout()
            invalidate()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        m = computeMetrics(w)
        setMeasuredDimension(w, (barH() + keysHeight()).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        m = computeMetrics(w)
        stroke.strokeWidth = dp(if (m.largeTablet) 2.1f else 1.8f)
        val r = containerRadius
        containerPath.reset()
        containerPath.addRoundRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW,
        )
        layoutKeys()
    }

    private fun layoutKeys() {
        val w = width.toFloat()
        if (w <= 0f || layout.rows.isEmpty()) return
        val cw = m.contentWidth
        val unit = (cw - (layout.cols - 1) * hGap) / layout.cols
        val keysTop = barH() + topPad
        for ((ri, row) in layout.rows.withIndex()) {
            var x = m.contentLeft
            val top = keysTop + ri * (rowH + vGap)
            for (k in row) {
                val span = k.weight * (unit + hGap)
                if (k.kind == KeyKind.SPACER) {
                    x += span
                } else {
                    k.left = x
                    k.right = x + span - hGap
                    k.top = top
                    k.bottom = top + rowH
                    x = k.right + hGap
                }
            }
        }
        layoutEmojiBottomRow(cw)
    }

    private fun layoutEmojiBottomRow(contentW: Float) {
        val unit = (contentW - 9 * hGap) / 10f
        val top = barH() + topPad + 3 * (rowH + vGap)
        var x = m.contentLeft
        for (k in listOf(emojiBottomAbc, emojiBottomGlobe, emojiBottomSpace, emojiBottomBksp)) {
            val span = k.weight * (unit + hGap)
            k.left = x
            k.right = x + span - hGap
            k.top = top
            k.bottom = top + rowH
            x = k.right + hGap
        }
    }

    // ---- drawing -------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        fill.style = Paint.Style.FILL
        fill.color = theme.container
        canvas.drawPath(containerPath, fill)

        if (emojiOpen) {
            drawEmojiPanel(canvas)
            drawPopups(canvas)
            return
        }

        if (barVisible) drawSuggestions(canvas)
        for (row in layout.rows) for (k in row) if (k.kind != KeyKind.SPACER) drawKey(canvas, k)
        drawPopups(canvas)
    }

    private fun drawSuggestions(canvas: Canvas) {
        val clip = clipboardCandidate
        if (clip != null) {
            drawClipboardChip(canvas, clip)
            return
        }

        val slotW = m.contentWidth / 3f
        val hasSuggestions = suggestions.any { it.isNotEmpty() }

        // Slot background highlight on touch/tap
        for (i in 0..2) {
            if (suggestions[i].isEmpty()) continue
            val isPressed = (barDown.indexOfValue(i) >= 0) || (tappedSuggestionSlot == i)
            if (isPressed) {
                fill.style = Paint.Style.FILL
                fill.color = theme.charPressed
                rect.set(
                    m.contentLeft + slotW * i + dp(3f),
                    dp(4f),
                    m.contentLeft + slotW * (i + 1) - dp(3f),
                    barHeight - dp(4f)
                )
                canvas.drawRoundRect(rect, keyRadius, keyRadius, fill)
            }
        }

        // Thin divider lines between slots
        if (hasSuggestions) {
            val divTop = dp(9f)
            val divBottom = barHeight - dp(9f)
            val prevStrokeW = stroke.strokeWidth
            val prevStrokeColor = stroke.color
            stroke.strokeWidth = dp(0.8f)
            stroke.color = theme.hint
            stroke.alpha = 75
            val x1 = m.contentLeft + slotW
            val x2 = m.contentLeft + slotW * 2f
            if (suggestions[0].isNotEmpty() || suggestions[1].isNotEmpty()) {
                canvas.drawLine(x1, divTop, x1, divBottom, stroke)
            }
            if (suggestions[1].isNotEmpty() || suggestions[2].isNotEmpty()) {
                canvas.drawLine(x2, divTop, x2, divBottom, stroke)
            }
            stroke.alpha = 255
            stroke.strokeWidth = prevStrokeW
            stroke.color = prevStrokeColor
        }

        // Suggestion labels
        suggestionPaint.color = theme.suggestion
        val baseSize = sp(m.suggestionSp)
        val fm = suggestionPaint.fontMetrics
        val baseline = barHeight / 2f - (fm.ascent + fm.descent) / 2f
        for (i in 0..2) {
            val s = suggestions[i]
            if (s.isEmpty()) continue
            val isPrimary = (i == 1 && suggestions[1].isNotEmpty()) ||
                (i == 0 && suggestions[1].isEmpty() && suggestions[2].isEmpty())
            suggestionPaint.textSize = if (isPrimary) baseSize * 1.05f else baseSize
            suggestionPaint.isFakeBoldText = isPrimary
            val fitted = TextUtils.ellipsize(s, suggestionPaint, slotW - dp(16f), TextUtils.TruncateAt.END)
            canvas.drawText(fitted.toString(), m.contentLeft + slotW * i + slotW / 2f, baseline, suggestionPaint)
        }
        suggestionPaint.isFakeBoldText = false
    }

    private fun drawClipboardChip(canvas: Canvas, textStr: String) {
        val chipL = m.contentLeft + dp(6f)
        val chipR = m.contentRight - dp(6f)
        val chipT = dp(4f)
        val chipB = barHeight - dp(4f)
        val closeW = dp(36f)
        val closeL = chipR - closeW

        // Drop shadow
        fill.style = Paint.Style.FILL
        fill.color = theme.shadow
        rect.set(chipL, chipT + shadowDy, chipR, chipB + shadowDy)
        canvas.drawRoundRect(rect, keyRadius, keyRadius, fill)

        // Chip body
        val bg = if (clipboardDown) theme.charPressed else theme.charKey
        rect.set(chipL, chipT, chipR, chipB)
        fill.color = bg
        canvas.drawRoundRect(rect, keyRadius, keyRadius, fill)

        // Divider before close button
        val prevStrokeW = stroke.strokeWidth
        val prevStrokeColor = stroke.color
        stroke.strokeWidth = dp(0.8f)
        stroke.color = theme.shadow
        canvas.drawLine(closeL, chipT + dp(4f), closeL, chipB - dp(4f), stroke)
        stroke.strokeWidth = prevStrokeW
        stroke.color = prevStrokeColor

        // Dismiss × button – vector cross for crisp rendering
        val closeCx = closeL + closeW / 2f
        val closeCy = (chipT + chipB) / 2f
        val arm = dp(4.8f)
        val prevStrokeW2 = stroke.strokeWidth
        val prevStrokeColor2 = stroke.color
        stroke.strokeWidth = dp(1.75f)
        stroke.color = theme.hint
        canvas.drawLine(closeCx - arm, closeCy - arm, closeCx + arm, closeCy + arm, stroke)
        canvas.drawLine(closeCx - arm, closeCy + arm, closeCx + arm, closeCy - arm, stroke)
        stroke.strokeWidth = prevStrokeW2
        stroke.color = prevStrokeColor2

        // Paste preview
        suggestionPaint.color = theme.label
        suggestionPaint.textSize = sp(m.suggestionSp * 0.92f)
        suggestionPaint.isFakeBoldText = false
        val sFm = suggestionPaint.fontMetrics
        val sCy = (chipT + chipB) / 2f - (sFm.ascent + sFm.descent) / 2f

        val actionPrefix = if (mode == Mode.NEPALI) "📋 टाँस्नुहोस्: " else "📋 Paste: "
        val prefixW = suggestionPaint.measureText(actionPrefix)
        val availBodyW = (closeL - chipL - dp(18f) - prefixW).coerceAtLeast(dp(20f))
        val oneLine = textStr.replace('\n', ' ').trim()
        val ellipBody = TextUtils.ellipsize(oneLine, suggestionPaint, availBodyW, TextUtils.TruncateAt.END)
        val fullDisplay = actionPrefix + ellipBody

        suggestionPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(fullDisplay, chipL + dp(12f), sCy, suggestionPaint)
        suggestionPaint.textAlign = Paint.Align.CENTER
    }

    private fun isPressed(k: KeyDef): Boolean {
        for (i in 0 until pressed.size()) if (pressed.valueAt(i) === k) return true
        return false
    }

    private fun drawKey(canvas: Canvas, k: KeyDef) {
        val down = isPressed(k)
        var bg: Int
        var fg = theme.label
        when (k.kind) {
            KeyKind.CHAR, KeyKind.SPACE -> bg = if (down) theme.charPressed else theme.charKey
            KeyKind.SHIFT -> {
                if (shift != ShiftState.OFF) {
                    bg = theme.shiftActive
                    fg = theme.shiftActiveGlyph
                } else bg = if (down) theme.modPressed else theme.modKey
            }
            KeyKind.RET -> when (retStyle) {
                ReturnStyle.NEUTRAL -> bg = if (down) theme.charPressed else theme.charKey
                ReturnStyle.GRAY -> {
                    bg = if (down) theme.returnGrayPressed else theme.returnGray
                    fg = theme.returnGrayLabel
                }
                ReturnStyle.BLUE -> {
                    bg = if (down) theme.returnBluePressed else theme.returnBlue
                    fg = theme.white
                }
            }
            else -> bg = if (down) theme.modPressed else theme.modKey
        }

        // 1dp bottom drop shadow, then the keycap.
        fill.style = Paint.Style.FILL
        rect.set(k.left, k.top + shadowDy, k.right, k.bottom + shadowDy)
        fill.color = theme.shadow
        canvas.drawRoundRect(rect, keyRadius, keyRadius, fill)
        rect.set(k.left, k.top, k.right, k.bottom)
        fill.color = bg
        canvas.drawRoundRect(rect, keyRadius, keyRadius, fill)

        val cx = (k.left + k.right) / 2f
        val cy = (k.top + k.bottom) / 2f
        val s = rowH * 0.36f
        stroke.color = fg
        fill.color = fg
        when (k.kind) {
            KeyKind.CHAR -> {
                val lbl = k.text(shift != ShiftState.OFF)
                drawLabel(canvas, lbl, k, cx, cy, fg, labelSize(lbl))
                if (k.hint.isNotEmpty()) {
                    text.color = theme.hint
                    text.textSize = sp(m.hintTextSp)
                    text.textAlign = Paint.Align.RIGHT
                    canvas.drawText(k.hint, k.right - dp(3.5f), k.top + dp(11f), text)
                    text.textAlign = Paint.Align.CENTER
                }
            }
            KeyKind.NUM, KeyKind.SYM, KeyKind.ABC -> drawLabel(canvas, k.base, k, cx, cy, fg, m.modTextSp)
            KeyKind.SPACE -> {
                val lbl = mode.spaceLabel
                text.color = theme.hint
                text.textSize = sp(m.modTextSp * 0.88f)
                val maxW = (k.right - k.left) - dp(16f)
                val w = text.measureText(lbl)
                if (w > maxW) text.textSize = text.textSize * maxW / w
                val fm = text.fontMetrics
                canvas.drawText(lbl, cx, cy - (fm.ascent + fm.descent) / 2f, text)
            }
            KeyKind.SHIFT -> drawShift(canvas, cx, cy, s)
            KeyKind.BKSP -> drawBackspace(canvas, cx, cy, s)
            KeyKind.EMOJI -> drawEmojiIcon(canvas, cx, cy, s)
            KeyKind.GLOBE -> drawLabel(canvas, mode.badge, k, cx, cy, fg, m.modTextSp)
            KeyKind.RET -> drawReturn(canvas, cx, cy, s)
            else -> Unit
        }
    }

    /** iOS-style connected keyhole preview bubble above pressed keys (and recent quick releases). */
    private fun drawPopups(canvas: Canvas) {
        var drawn = false
        if (emojiOpen && emojiDownIndex >= 0 && !emojiScrolling) {
            val emojis = activeEmojis()
            if (emojiDownIndex < emojis.size) {
                val b = emojiCellBounds(emojiDownIndex)
                drawKeyholePopup(canvas, b.left + dp(2f), b.top + dp(2f), b.right - dp(2f), b.bottom - dp(2f), emojis[emojiDownIndex], keyW = b.width())
                drawn = true
            }
        }
        for (i in 0 until pressed.size()) {
            val pid = pressed.keyAt(i)
            val k = pressed.valueAt(i)
            if (k.kind != KeyKind.CHAR) continue
            val label = if (pid == charLongPointer && charLongFired && k.hint.isNotEmpty()) {
                k.hint
            } else {
                k.text(shift != ShiftState.OFF)
            }
            drawKeyholePopup(canvas, k.left, k.top, k.right, k.bottom, label, keyW = k.right - k.left)
            drawn = true
        }
        val linger = lingerPopup
        if (!drawn && linger != null) {
            drawKeyholePopup(
                canvas, linger.left, linger.top, linger.right, linger.bottom,
                linger.label, keyW = linger.right - linger.left,
            )
        }
    }

    private fun showLingerPopup(left: Float, top: Float, right: Float, bottom: Float, label: String) {
        lingerPopup = PopupPreview(left, top, right, bottom, label)
        handler.removeCallbacks(clearLingerRunnable)
        handler.postDelayed(clearLingerRunnable, 85L)
    }

    private fun drawKeyholePopup(
        canvas: Canvas,
        bl: Float,
        bt: Float,
        br: Float,
        bb: Float,
        label: String,
        keyW: Float,
    ) {
        val pw = (keyW * 1.44f).coerceIn(dp(40f), dp(82f))
        val headH = (rowH * 1.02f).coerceIn(dp(36f), dp(62f))
        val cx = ((bl + br) / 2f).coerceIn(pw / 2f + dp(4f), width - pw / 2f - dp(4f))
        val hl = cx - pw / 2f
        val hr = cx + pw / 2f
        val ht = (bt - headH).coerceAtLeast(dp(2f))
        val flareY = (bt - dp(2f)).coerceAtLeast(ht + headH * 0.72f)
        val neckY = (bt + dp(10f)).coerceAtMost(bb - keyRadius)
        val hrCorner = dp(9f)
        val brCorner = keyRadius

        path.reset()
        path.moveTo(hl + hrCorner, ht)
        path.lineTo(hr - hrCorner, ht)
        path.quadTo(hr, ht, hr, ht + hrCorner)
        path.lineTo(hr, flareY)
        path.cubicTo(hr, neckY, br, flareY, br, neckY + dp(4f))
        path.lineTo(br, bb - brCorner)
        path.quadTo(br, bb, br - brCorner, bb)
        path.lineTo(bl + brCorner, bb)
        path.quadTo(bl, bb, bl, bb - brCorner)
        path.lineTo(bl, neckY + dp(4f))
        path.cubicTo(bl, flareY, hl, neckY, hl, flareY)
        path.lineTo(hl, ht + hrCorner)
        path.quadTo(hl, ht, hl + hrCorner, ht)
        path.close()

        fill.style = Paint.Style.FILL
        fill.color = theme.shadow
        canvas.save()
        canvas.translate(0f, dp(1.5f))
        canvas.drawPath(path, fill)
        canvas.restore()

        fill.color = theme.popupBg
        canvas.drawPath(path, fill)

        val prevStrokeW = stroke.strokeWidth
        stroke.strokeWidth = dp(0.8f)
        stroke.color = theme.shadow
        canvas.drawPath(path, stroke)
        stroke.strokeWidth = prevStrokeW

        text.color = theme.label
        text.textSize = sp(m.popupTextSp)
        val fm = text.fontMetrics
        val headCenterY = (ht + flareY) / 2f
        canvas.drawText(label, cx, headCenterY - (fm.ascent + fm.descent) / 2f, text)
    }

    /** Devanagari glyphs sit a little smaller than Latin ones. */
    private fun labelSize(s: String): Float =
        if (s.isNotEmpty() && s[0].code >= 0x0900) m.devaTextSp else m.latinTextSp

    private fun drawLabel(canvas: Canvas, s: String, k: KeyDef, cx: Float, cy: Float, color: Int, size: Float) {
        text.color = color
        text.textSize = sp(size)
        val maxW = (k.right - k.left) - dp(6f)
        val w = text.measureText(s)
        if (w > maxW) text.textSize = text.textSize * maxW / w
        val fm = text.fontMetrics
        canvas.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text)
    }

    private fun drawEmojiIcon(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val r = s * 0.58f
        val prevW = stroke.strokeWidth
        stroke.strokeWidth = dp(1.8f)
        canvas.drawCircle(cx, cy, r, stroke)
        // Eyes
        val prevStyle = fill.style
        fill.style = Paint.Style.FILL
        val eyeR = r * 0.135f
        canvas.drawCircle(cx - r * 0.34f, cy - r * 0.16f, eyeR, fill)
        canvas.drawCircle(cx + r * 0.34f, cy - r * 0.16f, eyeR, fill)
        fill.style = prevStyle
        // Balanced smile arc
        stroke.strokeWidth = dp(1.7f)
        rect.set(cx - r * 0.48f, cy - r * 0.24f, cx + r * 0.48f, cy + r * 0.50f)
        canvas.drawArc(rect, 22f, 136f, false, stroke)
        stroke.strokeWidth = prevW
    }

    private fun drawShift(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val filled = shift != ShiftState.OFF
        val lock = shift == ShiftState.LOCK
        val prevW = stroke.strokeWidth
        stroke.strokeWidth = dp(1.85f)
        val yShift = if (lock) -s * 0.08f else 0f
        val topY = cy - s * 0.54f + yShift
        val midY = cy + s * 0.04f + yShift
        val botY = cy + s * 0.48f + yShift
        val wingW = s * 0.56f
        val stemHW = s * 0.23f
        path.reset()
        path.moveTo(cx, topY)
        path.lineTo(cx + wingW, midY)
        path.lineTo(cx + stemHW, midY)
        path.lineTo(cx + stemHW, botY)
        path.lineTo(cx - stemHW, botY)
        path.lineTo(cx - stemHW, midY)
        path.lineTo(cx - wingW, midY)
        path.close()
        if (filled) {
            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)
        } else {
            canvas.drawPath(path, stroke)
        }
        if (lock) {
            val barY = botY + dp(4f)
            stroke.strokeWidth = dp(2.2f)
            canvas.drawLine(cx - stemHW, barY, cx + stemHW, barY, stroke)
        }
        stroke.strokeWidth = prevW
    }

    private fun drawBackspace(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val prevW = stroke.strokeWidth
        stroke.strokeWidth = dp(1.8f)
        val h = s * 0.45f
        val rr = dp(3.5f)
        val tipX = cx - s * 0.66f
        val lEdge = cx - s * 0.24f
        val rEdge = cx + s * 0.64f
        path.reset()
        path.moveTo(tipX, cy)
        path.lineTo(lEdge, cy - h)
        path.lineTo(rEdge - rr, cy - h)
        path.quadTo(rEdge, cy - h, rEdge, cy - h + rr)
        path.lineTo(rEdge, cy + h - rr)
        path.quadTo(rEdge, cy + h, rEdge - rr, cy + h)
        path.lineTo(lEdge, cy + h)
        path.close()
        canvas.drawPath(path, stroke)
        val xc = (lEdge + rEdge) * 0.5f + dp(0.5f)
        val xs = s * 0.19f
        canvas.drawLine(xc - xs, cy - xs, xc + xs, cy + xs, stroke)
        canvas.drawLine(xc - xs, cy + xs, xc + xs, cy - xs, stroke)
        stroke.strokeWidth = prevW
    }

    private fun activeEmojis(): List<String> =
        if (emojiCategoryIndex == 0) recentEmojis else EmojiCatalog.CATEGORIES[emojiCategoryIndex].emojis

    private fun emojiCols(): Int {
        val targetCellW = dp(if (m.largeTablet) 48f else if (m.tablet) 46f else if (m.landscape) 40f else 44f)
        return (m.contentWidth / targetCellW).toInt().coerceIn(7, 16)
    }

    private fun emojiGridTop(): Float = barHeight + dp(4f)

    private fun emojiGridBottom(): Float = emojiBottomAbc.top - dp(6f)

    private fun emojiCellBounds(index: Int): RectF {
        val cols = emojiCols()
        val cellW = m.contentWidth / cols
        val cellH = m.emojiCellH
        val col = index % cols
        val row = index / cols
        val left = m.contentLeft + col * cellW
        val top = emojiGridTop() + row * cellH - emojiScrollY
        return RectF(left, top, left + cellW, top + cellH)
    }

    private fun emojiMaxScroll(): Float {
        val count = activeEmojis().size
        val cols = emojiCols()
        val rows = (count + cols - 1) / cols
        val contentH = rows * m.emojiCellH
        val viewportH = (emojiGridBottom() - emojiGridTop()).coerceAtLeast(1f)
        return (contentH - viewportH).coerceAtLeast(0f)
    }

    private fun drawEmojiPanel(canvas: Canvas) {
        val cats = EmojiCatalog.CATEGORIES
        val tabW = m.contentWidth / cats.size.toFloat()
        for ((i, cat) in cats.withIndex()) {
            val l = m.contentLeft + tabW * i + dp(3f)
            val r = m.contentLeft + tabW * (i + 1) - dp(3f)
            if (i == emojiCategoryIndex) {
                fill.style = Paint.Style.FILL
                fill.color = theme.charKey
                rect.set(l, dp(4f), r, barHeight - dp(4f))
                canvas.drawRoundRect(rect, keyRadius, keyRadius, fill)
            }
            text.color = theme.label
            text.textSize = sp(if (m.landscape && !m.tablet && !m.largeTablet) 16f else 18f)
            val fm = text.fontMetrics
            canvas.drawText(cat.icon, (l + r) / 2f, barHeight / 2f - (fm.ascent + fm.descent) / 2f, text)
        }

        val emojis = activeEmojis()
        val cols = emojiCols()
        val cellW = m.contentWidth / cols
        val cellH = m.emojiCellH
        val gTop = emojiGridTop()
        val gBottom = emojiGridBottom()
        canvas.save()
        canvas.clipRect(m.contentLeft, gTop, m.contentRight, gBottom)
        text.color = theme.label
        text.textSize = sp(m.emojiFontSp)
        val fm = text.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) / 2f
        val firstRow = (emojiScrollY / cellH).toInt().coerceAtLeast(0)
        val lastRow = ((emojiScrollY + (gBottom - gTop)) / cellH).toInt()
        for (row in firstRow..lastRow) {
            for (col in 0 until cols) {
                val idx = row * cols + col
                if (idx >= emojis.size) break
                val cx = m.contentLeft + (col + 0.5f) * cellW
                val cy = gTop + (row + 0.5f) * cellH - emojiScrollY
                if (idx == emojiDownIndex && !emojiScrolling) {
                    fill.style = Paint.Style.FILL
                    fill.color = theme.charPressed
                    rect.set(cx - cellW * 0.44f, cy - cellH * 0.44f, cx + cellW * 0.44f, cy + cellH * 0.44f)
                    canvas.drawRoundRect(rect, keyRadius, keyRadius, fill)
                }
                canvas.drawText(emojis[idx], cx, cy + baselineOffset, text)
            }
        }
        canvas.restore()

        drawKey(canvas, emojiBottomAbc)
        drawKey(canvas, emojiBottomGlobe)
        drawKey(canvas, emojiBottomSpace)
        drawKey(canvas, emojiBottomBksp)
    }

    private fun drawReturn(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val prevW = stroke.strokeWidth
        val bold = retStyle == ReturnStyle.BLUE
        stroke.strokeWidth = dp(if (bold) 2.0f else 1.85f)
        when (retGlyph) {
            ReturnGlyph.ENTER -> {
                // ⏎  Sleek rounded-elbow return arrow, optically centered
                val rightX = cx + s * 0.46f
                val leftX = cx - s * 0.46f
                val topY = cy - s * 0.36f
                val midY = cy + s * 0.10f
                val elbowR = dp(3f)
                path.reset()
                path.moveTo(rightX, topY)
                path.lineTo(rightX, midY - elbowR)
                path.quadTo(rightX, midY, rightX - elbowR, midY)
                path.lineTo(leftX, midY)
                // Arrowhead
                path.moveTo(leftX + s * 0.28f, midY - s * 0.26f)
                path.lineTo(leftX, midY)
                path.lineTo(leftX + s * 0.28f, midY + s * 0.26f)
                canvas.drawPath(path, stroke)
            }
            ReturnGlyph.SEARCH -> {
                // 🔍  Balanced magnifying glass
                val r = s * 0.35f
                val oX = cx - s * 0.12f
                val oY = cy - s * 0.12f
                canvas.drawCircle(oX, oY, r, stroke)
                stroke.strokeWidth = dp(2.1f)
                val hx = oX + r * 0.707f
                val hy = oY + r * 0.707f
                canvas.drawLine(hx, hy, cx + s * 0.46f, cy + s * 0.46f, stroke)
            }
            ReturnGlyph.ARROW -> {
                // →  Right-pointing arrow (Go / Send / Next)
                val lx = cx - s * 0.48f
                val rx = cx + s * 0.46f
                canvas.drawLine(lx, cy, rx, cy, stroke)
                path.reset()
                path.moveTo(rx - s * 0.30f, cy - s * 0.28f)
                path.lineTo(rx, cy)
                path.lineTo(rx - s * 0.30f, cy + s * 0.28f)
                canvas.drawPath(path, stroke)
            }
            ReturnGlyph.CHECK -> {
                // ✓  Clean checkmark (Done)
                stroke.strokeWidth = dp(2.15f)
                path.reset()
                path.moveTo(cx - s * 0.42f, cy + s * 0.02f)
                path.lineTo(cx - s * 0.12f, cy + s * 0.34f)
                path.lineTo(cx + s * 0.46f, cy - s * 0.32f)
                canvas.drawPath(path, stroke)
            }
        }
        stroke.strokeWidth = prevW
    }

    // ---- touch ---------------------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (emojiOpen) {
            if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
            velocityTracker?.addMovement(e)
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                pointerDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                if (emojiOpen && emojiGridPointer != -1) {
                    val idx = e.findPointerIndex(emojiGridPointer)
                    if (idx >= 0) {
                        val y = e.getY(idx)
                        if (!emojiScrolling && kotlin.math.abs(y - emojiTouchStartY) > dp(6f)) {
                            emojiScrolling = true
                            emojiDownIndex = -1
                        }
                        if (emojiScrolling) {
                            val dy = emojiLastY - y
                            emojiScrollY = (emojiScrollY + dy).coerceIn(0f, emojiMaxScroll())
                            emojiLastY = y
                            invalidate()
                        }
                    }
                }
                if (spacePointer != -1) {
                    val idx = e.findPointerIndex(spacePointer)
                    if (idx >= 0) {
                        val stepPx = dp(11f)
                        val dx = e.getX(idx) - spaceLastX
                        val steps = (dx / stepPx).toInt()
                        if (steps != 0) {
                            spaceLastX += steps * stepPx
                            spaceSlid = true
                            if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            listener?.onCursorMove(steps)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                pointerUp(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun emojiIndexAt(x: Float, y: Float): Int {
        if (y < emojiGridTop() || y > emojiGridBottom()) return -1
        if (x < m.contentLeft || x > m.contentRight) return -1
        val cols = emojiCols()
        val cellW = m.contentWidth / cols
        val cellH = m.emojiCellH
        val col = ((x - m.contentLeft) / cellW).toInt().coerceIn(0, cols - 1)
        val row = ((y - emojiGridTop() + emojiScrollY) / cellH).toInt().coerceAtLeast(0)
        val idx = row * cols + col
        return if (idx in activeEmojis().indices) idx else -1
    }

    private fun suggestionSlotAt(x: Float): Int {
        val slotW = m.contentWidth / 3f
        return ((x - m.contentLeft) / slotW).toInt().coerceIn(0, 2)
    }

    private fun emojiCategoryAt(x: Float): Int {
        val cats = EmojiCatalog.CATEGORIES
        val tabW = m.contentWidth / cats.size.toFloat()
        return ((x - m.contentLeft) / tabW).toInt().coerceIn(0, cats.size - 1)
    }

    private fun emojiBottomKeyAt(x: Float, y: Float): KeyDef? {
        if (y < emojiBottomAbc.top - vGap / 2f) return null
        val row = listOf(emojiBottomAbc, emojiBottomGlobe, emojiBottomSpace, emojiBottomBksp)
        return row.minByOrNull { k ->
            if (x < k.left) k.left - x else if (x > k.right) x - k.right else 0f
        }
    }

    private fun keyAt(x: Float, y: Float): KeyDef? {
        val rows = layout.rows
        if (rows.isEmpty()) return null
        val pitch = rowH + vGap
        val ri = Math.floor(((y - (barH() + topPad) + vGap / 2f) / pitch).toDouble())
            .toInt().coerceIn(0, rows.size - 1)
        var best: KeyDef? = null
        var bestD = Float.MAX_VALUE
        for (k in rows[ri]) {
            if (k.kind == KeyKind.SPACER) continue
            val d = if (x < k.left) k.left - x else if (x > k.right) x - k.right else 0f
            if (d < bestD) {
                bestD = d
                best = k
            }
        }
        return best
    }

    private fun feedback() {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (sound) playSoundEffect(SoundEffectConstants.CLICK)
    }

    private fun invalidateKey(k: KeyDef) {
        invalidate()
    }

    private fun pointerDown(id: Int, x: Float, y: Float) {
        handler.removeCallbacks(clearLingerRunnable)
        lingerPopup = null

        if (emojiOpen) {
            if (y < barHeight) {
                scroller.abortAnimation()
                removeCallbacks(flingRunnable)
                val nextCat = emojiCategoryAt(x)
                if (nextCat != emojiCategoryIndex) {
                    emojiCategoryIndex = nextCat
                    emojiScrollY = 0f
                }
                feedback()
                invalidate()
                return
            }
            val bottomKey = emojiBottomKeyAt(x, y)
            if (bottomKey != null) {
                val l = listener ?: return
                pressed.put(id, bottomKey)
                invalidate()
                feedback()
                when (bottomKey.kind) {
                    KeyKind.ABC -> l.onEmojiToggle()
                    KeyKind.GLOBE -> {
                        globePointer = id
                        globeLongFired = false
                        handler.postDelayed(globeLongRunnable, 450)
                    }
                    KeyKind.SPACE -> {
                        spacePointer = id
                        spaceLastX = x
                        spaceSlid = false
                    }
                    KeyKind.BKSP -> {
                        l.onBackspace()
                        repeatPointer = id
                        repeatStartTime = SystemClock.uptimeMillis()
                        handler.removeCallbacks(repeatRunnable)
                        handler.postDelayed(repeatRunnable, 360)
                    }
                    else -> Unit
                }
                return
            }
            val idx = emojiIndexAt(x, y)
            scroller.abortAnimation()
            removeCallbacks(flingRunnable)
            emojiGridPointer = id
            emojiTouchStartY = y
            emojiLastY = y
            emojiScrolling = false
            emojiDownIndex = idx
            if (idx >= 0) invalidate()
            return
        }

        if (barVisible && y < barHeight) {
            if (clipboardCandidate != null) {
                val chipR = m.contentRight - dp(6f)
                val closeW = dp(36f)
                val closeL = chipR - closeW
                if (x >= closeL) {
                    feedback()
                    listener?.onClipboardDismiss()
                } else {
                    clipboardDown = true
                    invalidate()
                }
                return
            }
            val slot = suggestionSlotAt(x)
            barDown.put(id, slot)
            invalidate()
            barLongPointer = id
            barLongFired = false
            handler.postDelayed(barLongRunnable, 500)
            return
        }
        val k = keyAt(x, y) ?: return
        val l = listener ?: return
        // If another key is pressed while Space is still held down (two-thumb overlap),
        // commit the pending space immediately before handling the new key.
        if (spacePointer != -1 && !spaceSlid && k.kind != KeyKind.SPACE) {
            spaceSlid = true
            l.onSpace()
        }
        pressed.put(id, k)
        invalidateKey(k)
        feedback()
        when (k.kind) {
            KeyKind.CHAR -> {
                l.onText(k.text(shift != ShiftState.OFF))
                if (k.hint.isNotEmpty()) {
                    handler.removeCallbacks(charLongRunnable)
                    charLongPointer = id
                    charLongFired = false
                    handler.postDelayed(charLongRunnable, 380)
                }
            }
            KeyKind.SPACE -> {
                spacePointer = id
                spaceLastX = x
                spaceSlid = false
            }
            KeyKind.SHIFT -> l.onShift()
            KeyKind.NUM -> l.onPage(1)
            KeyKind.SYM -> l.onPage(2)
            KeyKind.ABC -> l.onPage(0)
            KeyKind.EMOJI -> l.onEmojiToggle()
            KeyKind.RET -> l.onReturn()
            KeyKind.BKSP -> {
                l.onBackspace()
                repeatPointer = id
                repeatStartTime = SystemClock.uptimeMillis()
                handler.removeCallbacks(repeatRunnable)
                handler.postDelayed(repeatRunnable, 360)
            }
            KeyKind.GLOBE -> {
                globePointer = id
                globeLongFired = false
                handler.postDelayed(globeLongRunnable, 450)
            }
            KeyKind.SPACER -> Unit
        }
    }

    private fun pointerUp(id: Int, x: Float, y: Float) {
        if (emojiOpen && id == emojiGridPointer) {
            val idx = emojiDownIndex
            val wasScrolling = emojiScrolling
            emojiGridPointer = -1
            emojiScrolling = false
            emojiDownIndex = -1
            if (wasScrolling) {
                val vt = velocityTracker
                if (vt != null) {
                    vt.computeCurrentVelocity(1000)
                    val vy = vt.yVelocity
                    if (kotlin.math.abs(vy) > dp(100f)) {
                        scroller.fling(
                            0, emojiScrollY.toInt(),
                            0, (-vy).toInt(),
                            0, 0,
                            0, emojiMaxScroll().toInt()
                        )
                        postOnAnimation(flingRunnable)
                    }
                    vt.recycle()
                    velocityTracker = null
                }
            } else {
                velocityTracker?.recycle()
                velocityTracker = null
                if (idx >= 0) {
                    val emojis = activeEmojis()
                    if (idx < emojis.size) {
                        val emoji = emojis[idx]
                        val b = emojiCellBounds(idx)
                        showLingerPopup(b.left + dp(2f), b.top + dp(2f), b.right - dp(2f), b.bottom - dp(2f), emoji)
                        feedback()
                        listener?.onEmojiPick(emoji)
                    }
                }
            }
            invalidate()
            return
        }

        if (clipboardDown) {
            clipboardDown = false
            invalidate()
            if (y < barHeight) {
                val chipR = m.contentRight - dp(6f)
                val closeW = dp(36f)
                val closeL = chipR - closeW
                if (x < closeL) {
                    feedback()
                    listener?.onClipboardPaste()
                }
            }
            return
        }

        val slot = barDown.get(id, -1)
        if (slot >= 0) {
            barDown.remove(id)
            if (id == barLongPointer) {
                handler.removeCallbacks(barLongRunnable)
                if (!barLongFired && y < barHeight && slot == suggestionSlotAt(x)) {
                    tappedSuggestionSlot = slot
                    handler.removeCallbacks(clearTapFeedbackRunnable)
                    handler.postDelayed(clearTapFeedbackRunnable, 90L)
                    invalidate()
                    feedback()
                    listener?.onSuggestion(slot)
                } else {
                    invalidate()
                }
            } else {
                invalidate()
            }
            return
        }
        val k = pressed.get(id) ?: return
        pressed.remove(id)
        if (k.kind == KeyKind.CHAR) {
            val label = if (id == charLongPointer && charLongFired && k.hint.isNotEmpty()) {
                k.hint
            } else {
                k.text(shift != ShiftState.OFF)
            }
            showLingerPopup(k.left, k.top, k.right, k.bottom, label)
        }
        invalidateKey(k)
        if (id == charLongPointer) {
            handler.removeCallbacks(charLongRunnable)
            charLongPointer = -1
            charLongFired = false
        }
        if (id == spacePointer) {
            spacePointer = -1
            if (!spaceSlid) listener?.onSpace()
        }
        if (id == repeatPointer) {
            handler.removeCallbacks(repeatRunnable)
            repeatPointer = -1
        }
        if (id == globePointer) {
            handler.removeCallbacks(globeLongRunnable)
            globePointer = -1
            if (!globeLongFired) listener?.onGlobe()
        }
    }

    private fun cancelAll() {
        scroller.abortAnimation()
        removeCallbacks(flingRunnable)
        velocityTracker?.recycle()
        velocityTracker = null
        clipboardDown = false
        tappedSuggestionSlot = -1
        handler.removeCallbacks(clearTapFeedbackRunnable)
        handler.removeCallbacks(repeatRunnable)
        handler.removeCallbacks(globeLongRunnable)
        handler.removeCallbacks(barLongRunnable)
        handler.removeCallbacks(charLongRunnable)
        handler.removeCallbacks(clearLingerRunnable)
        repeatPointer = -1
        globePointer = -1
        charLongPointer = -1
        charLongFired = false
        spacePointer = -1
        emojiGridPointer = -1
        emojiDownIndex = -1
        emojiScrolling = false
        lingerPopup = null
        pressed.clear()
        barDown.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }
}
