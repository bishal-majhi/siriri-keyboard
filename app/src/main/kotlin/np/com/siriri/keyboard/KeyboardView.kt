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
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseArray
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.WindowInsets

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
        fun onGlobe()
        fun onGlobeLong()
        fun onSpace()
        fun onCursorMove(delta: Int)
        fun onReturn()
        fun onSuggestion(index: Int)
        fun onSuggestionLong(index: Int)
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
    private var bottomInset = 0
    private val layoutCache = HashMap<Int, Layout>()

    // ---- metrics (px) --------------------------------------------------------
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val landscape get() = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
    private val rowH get() = dp(if (landscape) 38f else 46f)
    private val vGap get() = dp(if (landscape) 6f else 9f)
    private val hGap = dp(5f)
    private val side = dp(3f)
    private val topPad = dp(8f)
    private val bottomPad = dp(6f)
    private val keyRadius = dp(6f)
    private val containerRadius = dp(20f)
    private val shadowDy = dp(1f)
    private val barHeight = dp(40f)

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
    private var globePointer = -1
    private var globeLongFired = false
    private var barLongPointer = -1
    private var barLongFired = false
    private var charLongPointer = -1
    private var charLongFired = false
    private var spacePointer = -1
    private var spaceLastX = 0f
    private var spaceSlid = false

    private val repeatRunnable = object : Runnable {
        override fun run() {
            listener?.onBackspace()
            handler.postDelayed(this, 45)
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

    fun setBarVisible(v: Boolean) {
        if (v == barVisible) return
        barVisible = v
        layoutKeys()
        requestLayout()
        invalidate()
    }

    // ---- measuring & geometry ------------------------------------------------------

    private fun barH() = if (barVisible) barHeight else 0f

    private fun keysHeight(): Float {
        val n = layout.rows.size.coerceAtLeast(4)
        return topPad + n * rowH + (n - 1) * vGap + bottomPad + bottomInset
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val b = if (Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(WindowInsets.Type.systemBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (b != bottomInset) {
            bottomInset = b
            requestLayout()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (barH() + keysHeight()).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
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
        val unit = (w - 2 * side - (layout.cols - 1) * hGap) / layout.cols
        val keysTop = barH() + topPad
        for ((ri, row) in layout.rows.withIndex()) {
            var x = side
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
    }

    // ---- drawing -------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        fill.style = Paint.Style.FILL
        fill.color = theme.container
        canvas.drawPath(containerPath, fill)

        if (barVisible) drawSuggestions(canvas)
        for (row in layout.rows) for (k in row) if (k.kind != KeyKind.SPACER) drawKey(canvas, k)
        drawPopups(canvas)
    }

    private fun drawSuggestions(canvas: Canvas) {
        val slotW = width / 3f
        suggestionPaint.color = theme.suggestion
        suggestionPaint.textSize = sp(17f)
        val fm = suggestionPaint.fontMetrics
        val baseline = barHeight / 2f - (fm.ascent + fm.descent) / 2f
        for (i in 0..2) {
            val s = suggestions[i]
            if (s.isEmpty()) continue
            val fitted = TextUtils.ellipsize(s, suggestionPaint, slotW - dp(16f), TextUtils.TruncateAt.END)
            canvas.drawText(fitted.toString(), slotW * i + slotW / 2f, baseline, suggestionPaint)
        }
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
                    text.textSize = sp(9.5f)
                    text.textAlign = Paint.Align.RIGHT
                    canvas.drawText(k.hint, k.right - dp(3.5f), k.top + dp(11f), text)
                    text.textAlign = Paint.Align.CENTER
                }
            }
            KeyKind.NUM, KeyKind.SYM, KeyKind.ABC -> drawLabel(canvas, k.base, k, cx, cy, fg, 16f)
            KeyKind.SPACE -> drawBadge(canvas, k)
            KeyKind.SHIFT -> drawShift(canvas, cx, cy, s)
            KeyKind.BKSP -> drawBackspace(canvas, cx, cy, s)
            KeyKind.GLOBE -> drawGlobe(canvas, cx, cy, s)
            KeyKind.RET -> drawReturn(canvas, cx, cy, s)
            else -> Unit
        }
    }

    /** Floating key-press preview bubble above any currently pressed character key. */
    private fun drawPopups(canvas: Canvas) {
        for (i in 0 until pressed.size()) {
            val pid = pressed.keyAt(i)
            val k = pressed.valueAt(i)
            if (k.kind != KeyKind.CHAR) continue
            val label = if (pid == charLongPointer && charLongFired && k.hint.isNotEmpty()) {
                k.hint
            } else {
                k.text(shift != ShiftState.OFF)
            }
            val pw = ((k.right - k.left) * 1.24f).coerceAtLeast(dp(38f))
            val ph = rowH * 1.15f
            val cx = ((k.left + k.right) / 2f).coerceIn(pw / 2f + dp(4f), width - pw / 2f - dp(4f))
            val bottom = (k.top - dp(4f)).coerceAtLeast(ph + dp(2f))
            val top = bottom - ph

            rect.set(cx - pw / 2f, top + shadowDy * 1.5f, cx + pw / 2f, bottom + shadowDy * 1.5f)
            fill.color = theme.shadow
            canvas.drawRoundRect(rect, keyRadius * 1.3f, keyRadius * 1.3f, fill)

            rect.set(cx - pw / 2f, top, cx + pw / 2f, bottom)
            fill.color = theme.popupBg
            canvas.drawRoundRect(rect, keyRadius * 1.3f, keyRadius * 1.3f, fill)

            text.color = theme.label
            text.textSize = sp(26f)
            val fm = text.fontMetrics
            canvas.drawText(label, cx, (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f, text)
        }
    }

    /** Devanagari glyphs sit a little smaller than Latin ones. */
    private fun labelSize(s: String): Float =
        if (s.isNotEmpty() && s[0].code >= 0x0900) 20f else 22f

    private fun drawLabel(canvas: Canvas, s: String, k: KeyDef, cx: Float, cy: Float, color: Int, size: Float) {
        text.color = color
        text.textSize = sp(size)
        val maxW = (k.right - k.left) - dp(6f)
        val w = text.measureText(s)
        if (w > maxW) text.textSize = text.textSize * maxW / w
        val fm = text.fontMetrics
        canvas.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text)
    }

    /** The tiny mode badge (A / क / ने / हि) in the spacebar's bottom-right corner. */
    private fun drawBadge(canvas: Canvas, k: KeyDef) {
        text.color = theme.badge
        text.textSize = sp(11f)
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(mode.badge, k.right - dp(8f), k.bottom - dp(7f), text)
        text.textAlign = Paint.Align.CENTER
    }

    private fun drawShift(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val filled = shift != ShiftState.OFF
        path.reset()
        path.moveTo(cx, cy - s * 0.6f)
        path.lineTo(cx + s * 0.62f, cy)
        path.lineTo(cx + s * 0.25f, cy)
        path.lineTo(cx + s * 0.25f, cy + s * 0.5f)
        path.lineTo(cx - s * 0.25f, cy + s * 0.5f)
        path.lineTo(cx - s * 0.25f, cy)
        path.lineTo(cx - s * 0.62f, cy)
        path.close()
        canvas.drawPath(path, if (filled) fill else stroke)
        if (shift == ShiftState.LOCK) {
            rect.set(cx - s * 0.25f, cy + s * 0.68f, cx + s * 0.25f, cy + s * 0.68f + dp(2f))
            canvas.drawRect(rect, fill)
        }
    }

    private fun drawBackspace(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx - s * 0.85f, cy)
        path.lineTo(cx - s * 0.35f, cy - s * 0.5f)
        path.lineTo(cx + s * 0.75f, cy - s * 0.5f)
        path.lineTo(cx + s * 0.75f, cy + s * 0.5f)
        path.lineTo(cx - s * 0.35f, cy + s * 0.5f)
        path.close()
        canvas.drawPath(path, stroke)
        canvas.drawLine(cx - s * 0.05f, cy - s * 0.2f, cx + s * 0.45f, cy + s * 0.2f, stroke)
        canvas.drawLine(cx - s * 0.05f, cy + s * 0.2f, cx + s * 0.45f, cy - s * 0.2f, stroke)
    }

    private fun drawGlobe(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        canvas.drawCircle(cx, cy, s * 0.7f, stroke)
        rect.set(cx - s * 0.3f, cy - s * 0.7f, cx + s * 0.3f, cy + s * 0.7f)
        canvas.drawOval(rect, stroke)
        canvas.drawLine(cx - s * 0.7f, cy, cx + s * 0.7f, cy, stroke)
    }

    private fun drawReturn(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        when (retGlyph) {
            ReturnGlyph.ENTER -> {
                path.reset()
                path.moveTo(cx + s * 0.7f, cy - s * 0.45f)
                path.lineTo(cx + s * 0.7f, cy + s * 0.1f)
                path.lineTo(cx - s * 0.55f, cy + s * 0.1f)
                path.moveTo(cx - s * 0.2f, cy - s * 0.25f)
                path.lineTo(cx - s * 0.6f, cy + s * 0.1f)
                path.lineTo(cx - s * 0.2f, cy + s * 0.45f)
                canvas.drawPath(path, stroke)
            }
            ReturnGlyph.SEARCH -> {
                canvas.drawCircle(cx - s * 0.12f, cy - s * 0.12f, s * 0.42f, stroke)
                canvas.drawLine(cx + s * 0.2f, cy + s * 0.2f, cx + s * 0.62f, cy + s * 0.62f, stroke)
            }
            ReturnGlyph.ARROW -> {
                path.reset()
                path.moveTo(cx - s * 0.6f, cy)
                path.lineTo(cx + s * 0.6f, cy)
                path.moveTo(cx + s * 0.15f, cy - s * 0.45f)
                path.lineTo(cx + s * 0.6f, cy)
                path.lineTo(cx + s * 0.15f, cy + s * 0.45f)
                canvas.drawPath(path, stroke)
            }
            ReturnGlyph.CHECK -> {
                path.reset()
                path.moveTo(cx - s * 0.55f, cy)
                path.lineTo(cx - s * 0.15f, cy + s * 0.4f)
                path.lineTo(cx + s * 0.6f, cy - s * 0.45f)
                canvas.drawPath(path, stroke)
            }
        }
    }

    // ---- touch ---------------------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                pointerDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
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
        if (barVisible && y < barHeight) {
            val slot = (x / (width / 3f)).toInt().coerceIn(0, 2)
            barDown.put(id, slot)
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
            KeyKind.RET -> l.onReturn()
            KeyKind.BKSP -> {
                l.onBackspace()
                repeatPointer = id
                handler.removeCallbacks(repeatRunnable)
                handler.postDelayed(repeatRunnable, 400)
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
        val slot = barDown.get(id, -1)
        if (slot >= 0) {
            barDown.remove(id)
            if (id == barLongPointer) {
                handler.removeCallbacks(barLongRunnable)
                if (!barLongFired && y < barHeight && slot == (x / (width / 3f)).toInt().coerceIn(0, 2)) {
                    feedback()
                    listener?.onSuggestion(slot)
                }
            }
            return
        }
        val k = pressed.get(id) ?: return
        pressed.remove(id)
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
        handler.removeCallbacks(repeatRunnable)
        handler.removeCallbacks(globeLongRunnable)
        handler.removeCallbacks(barLongRunnable)
        handler.removeCallbacks(charLongRunnable)
        repeatPointer = -1
        globePointer = -1
        charLongPointer = -1
        charLongFired = false
        spacePointer = -1
        pressed.clear()
        barDown.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }
}
