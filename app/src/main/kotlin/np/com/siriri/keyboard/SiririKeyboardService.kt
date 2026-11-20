package np.com.siriri.keyboard

import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.Executors

/**
 * Thin OS shell. All language logic (layouts, transliteration, word learning)
 * lives in the Rust core; this class only talks to Android's text-input API.
 *
 * Privacy rules enforced here:
 *  - nothing is learned in password, e-mail, URL or numeric fields;
 *  - nothing is learned when the app asks for no personalised learning (incognito);
 *  - the learned-word file lives in the app's private storage (see Rust store).
 */
class SiririKeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var kb: KeyboardView
    private lateinit var prefs: Prefs
    private val io = Executors.newSingleThreadExecutor()

    // ---- keyboard state ----
    private var mode = Mode.ENGLISH
    private var page = 0
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L
    private var emojiOpen = false

    // ---- field state (set in onStartInputView) ----
    private var latinOnly = false      // password / e-mail / URL / numeric
    private var learnOn = false
    private var barAllowed = false
    private var autoCaps = false
    private var multiLine = false
    private var noEnter = false
    private var action = EditorInfo.IME_ACTION_NONE

    // ---- text tracking ----
    private val roman = StringBuilder()   // raw Roman keystrokes (Roman modes)
    private var composing = ""            // what the composing region currently shows
    private var composingBase = -1
    private val word = StringBuilder()    // current word (English / Nepali direct typing)
    private var prevWord = ""
    private var lastSpaceAt = 0L
    private var spaceAfterWord = false
    private var learnedSinceSave = 0

    // Cursor tracking: lets us tell our own edits from the user moving the cursor.
    private var pred = -1
    private val expected = ArrayDeque<Int>()
    private var selStart = 0
    private var selEnd = 0

    // ---- clipboard quick-paste state ----
    private var clipboardManager: ClipboardManager? = null
    private var clipboardText: String? = null
    private var lastPastedText: String? = null
    private var clipTimestamp = 0L
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        checkClipboard(isNewClip = true)
    }

    // ------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        Native.init(filesDir.absolutePath)
        prefs = Prefs(this)
        mode = prefs.mode
        try {
            clipboardManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardManager?.addPrimaryClipChangedListener(clipListener)
        } catch (_: Throwable) {
        }
        // Transparent window so the 20dp rounded top corners show.
        try {
            window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } catch (_: Throwable) {
        }
    }

    override fun onCreateInputView(): View {
        kb = KeyboardView(this)
        kb.listener = this
        applyTheme()
        return kb
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::kb.isInitialized) {
            applyTheme()
            kb.onConfigChanged()
        }
    }

    private fun applyTheme() {
        val t = KeyTheme.forContext(this)
        kb.setTheme(t)
        // Paint the navigation bar the same colour as the keyboard so it looks seamless.
        try {
            val w = window?.window ?: return
            @Suppress("DEPRECATION")
            w.navigationBarColor = t.container
            if (Build.VERSION.SDK_INT >= 30) {
                w.insetsController?.setSystemBarsAppearance(
                    if (t.dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                )
            } else {
                @Suppress("DEPRECATION")
                w.decorView.systemUiVisibility = if (t.dark) {
                    w.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    w.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyTheme()

        val type = info.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val isText = cls == InputType.TYPE_CLASS_TEXT
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        val password = (isText && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val email = isText && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        val uri = isText && variation == InputType.TYPE_TEXT_VARIATION_URI

        multiLine = isText && (type and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        noEnter = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val incognito = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        val noSuggestFlag = isText && (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0

        latinOnly = password || email || uri || numeric
        val savedMode = prefs.mode.let { m ->
            if (prefs.isModeEnabled(m)) m else Mode.entries.firstOrNull { prefs.isModeEnabled(it) } ?: Mode.ENGLISH
        }
        mode = if (latinOnly) Mode.ENGLISH else savedMode
        page = if (numeric) 1 else 0
        learnOn = prefs.learn && !latinOnly && !incognito
        // URL and raw search fields collapse the strip completely.
        barAllowed = !latinOnly && action != EditorInfo.IME_ACTION_SEARCH && !noSuggestFlag
        autoCaps = mode == Mode.ENGLISH && !latinOnly

        roman.clear()
        composing = ""
        composingBase = -1
        word.clear()
        prevWord = ""
        spaceAfterWord = false
        expected.clear()
        pred = info.initialSelStart
        selStart = info.initialSelStart.coerceAtLeast(0)
        selEnd = info.initialSelEnd.coerceAtLeast(0)
        shift = ShiftState.OFF
        emojiOpen = false

        kb.haptics = prefs.haptic
        kb.sound = prefs.sound
        kb.setEmojiOpen(false, prefs.recentEmojis())
        kb.requestApplyInsets()
        dismissClipboard()
        resync()
        pushModeToView()
        updateAutoShift()
        updateReturnKey()
        if (!latinOnly && !incognito && barAllowed) {
            checkClipboard(isNewClip = false)
        }
        refreshSuggestions()
    }

    private fun pushModeToView() {
        kb.setMode(mode, page)
        kb.setShift(shift)
        kb.setBarVisible(barAllowed && mode.showsSuggestions)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        commitComposing(learn = true)
        learnWord(word.toString())
        word.clear()
        saveAsync()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipListener)
        } catch (_: Throwable) {
        }
        Native.save()
        io.shutdown()
        super.onDestroy()
    }

    private fun saveAsync() {
        learnedSinceSave = 0
        io.execute { Native.save() }
    }

    // ------------------------------------------------------------------------------
    // Cursor tracking
    // ------------------------------------------------------------------------------

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        if (pred >= 0) {
            val idx = if (newSelStart == newSelEnd) expected.indexOf(newSelStart) else -1
            if (idx >= 0) {
                repeat(idx + 1) { expected.removeFirst() }
            } else {
                // The user moved the cursor / selected text / the app changed the text.
                expected.clear()
                pred = newSelStart
                onExternalChange()
            }
        }
        if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_GO ||
            action == EditorInfo.IME_ACTION_SEND
        ) updateReturnKey()
    }

    private fun onExternalChange() {
        if (roman.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            roman.clear()
            composing = ""
            composingBase = -1
        }
        resync()
        refreshSuggestions()
    }

    private fun push(p: Int) {
        if (p < 0) return
        expected.addLast(p)
        if (expected.size > 24) expected.removeFirst()
    }

    /** Re-read the word under the cursor (and the word before it) from the editor. */
    private fun resync() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        word.setLength(0)
        word.append(before, i, before.length)
        var j = i
        while (j > 0 && before[j - 1] == ' ') j--
        var k = j
        while (k > 0 && isWordChar(before[k - 1])) k--
        prevWord = if (j < i && k < j) before.substring(k, j) else ""
    }

    // ------------------------------------------------------------------------------
    // Edit helpers (keep `pred` / `expected` in step with what we send)
    // ------------------------------------------------------------------------------

    private fun commit(text: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty() && composingBase >= 0) pred = composingBase + text.length
        else if (pred >= 0) pred += text.length
        push(pred)
        ic.commitText(text, 1)
        composing = ""
        composingBase = -1
    }

    private fun setComposing(text: String) {
        val ic = currentInputConnection ?: return
        if (composing.isEmpty()) composingBase = pred
        if (composingBase >= 0) {
            pred = composingBase + text.length
            push(pred)
        }
        ic.setComposingText(text, 1)
        composing = text
    }

    private fun deleteBefore(n: Int) {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(n, 0)
        if (pred >= 0) {
            pred = (pred - n).coerceAtLeast(0)
            push(pred)
        }
    }

    // ------------------------------------------------------------------------------
    // Key handling
    // ------------------------------------------------------------------------------

    override fun onText(text: String) {
        dismissClipboard()
        val c = text[0]
        val latinLetter = text.length == 1 && (c in 'a'..'z' || c in 'A'..'Z')
        if (mode.roman && latinLetter) {
            roman.append(text)
            updateComposing()
        } else {
            if (mode.roman) commitComposing(learn = true)
            if (text.all { isWordChar(it) }) {
                word.append(text)
            } else {
                learnWord(word.toString())
                word.setLength(0)
                if (text == "." || text == "!" || text == "?" || text == "।" || text == "॥") prevWord = ""
            }
            spaceAfterWord = false
            commit(text)
        }
        if (shift == ShiftState.ONCE) {
            shift = ShiftState.OFF
            kb.setShift(shift)
        }
        refreshSuggestions()
    }

    override fun onLongChar(text: String) {
        dismissClipboard()
        if (mode.roman && roman.isNotEmpty()) {
            roman.setLength(roman.length - 1)
            if (roman.isEmpty()) {
                commit("")
            } else {
                commitComposing(learn = true)
            }
        } else {
            if (word.isNotEmpty()) word.setLength(word.length - 1)
            deleteBefore(1)
        }
        learnWord(word.toString())
        word.setLength(0)
        spaceAfterWord = false
        commit(text)
        refreshSuggestions()
    }

    override fun onCursorMove(delta: Int) {
        val ic = currentInputConnection ?: return
        if (roman.isNotEmpty()) commitComposing(learn = true)
        val target = (selEnd + delta).coerceAtLeast(0)
        ic.setSelection(target, target)
    }

    private fun updateComposing() {
        val deva = Native.transliterate(mode.id, roman.toString())
        setComposing(deva)
    }

    /** Commit the Roman composing text (as Devanagari) into the editor. */
    private fun commitComposing(learn: Boolean) {
        if (roman.isEmpty()) return
        val text = composing
        roman.setLength(0)
        commit(text)
        if (learn) learnWord(text)
    }

    override fun onSpace() {
        dismissClipboard()
        if (currentInputConnection == null) return
        val now = SystemClock.uptimeMillis()
        val hadWord = if (mode.roman && roman.isNotEmpty()) {
            commitComposing(learn = true)
            true
        } else {
            val had = word.isNotEmpty()
            learnWord(word.toString())
            had
        }
        word.setLength(0)

        if (!hadWord && spaceAfterWord && now - lastSpaceAt < 450) {
            // double-space => ". " (or "। " for Devanagari modes)
            deleteBefore(1)
            commit(mode.sentenceEnd + " ")
            spaceAfterWord = false
            prevWord = ""
        } else {
            commit(" ")
            spaceAfterWord = hadWord
        }
        lastSpaceAt = now
        updateAutoShift()
        refreshSuggestions()
    }

    override fun onBackspace() {
        dismissClipboard()
        val ic = currentInputConnection ?: return
        spaceAfterWord = false
        if (roman.isNotEmpty()) {
            roman.setLength(roman.length - 1)
            if (roman.isEmpty()) {
                commit("") // clears the composing region
            } else {
                updateComposing()
            }
            refreshSuggestions()
            return
        }
        if (selEnd > selStart) {
            pred = selStart
            commit("")
            word.setLength(0)
            resync()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            if (pred >= 0) {
                pred = (pred - 1).coerceAtLeast(0)
                push(pred)
            }
            if (word.isNotEmpty()) word.setLength(word.length - 1) else resync()
        }
        updateAutoShift()
        refreshSuggestions()
    }

    override fun onBackspaceWord() {
        dismissClipboard()
        val ic = currentInputConnection ?: return
        spaceAfterWord = false
        if (roman.isNotEmpty()) {
            roman.setLength(0)
            commit("")
            refreshSuggestions()
            return
        }
        if (selEnd > selStart) {
            pred = selStart
            commit("")
            word.setLength(0)
            resync()
            updateAutoShift()
            refreshSuggestions()
            return
        }
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
        if (before.isEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        var i = before.length
        // Skip trailing whitespace
        while (i > 0 && before[i - 1].isWhitespace()) i--
        // Skip non-word punctuation vs preceding word characters
        if (i > 0 && !isWordChar(before[i - 1])) {
            while (i > 0 && !isWordChar(before[i - 1]) && !before[i - 1].isWhitespace()) i--
        } else {
            while (i > 0 && isWordChar(before[i - 1])) i--
        }
        val toDelete = (before.length - i).coerceAtLeast(1)
        deleteBefore(toDelete)
        word.setLength(0)
        resync()
        updateAutoShift()
        refreshSuggestions()
    }

    override fun onShift() {
        dismissClipboard()
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == ShiftState.LOCK -> ShiftState.OFF
            shift == ShiftState.ONCE && now - lastShiftTap < 350 -> ShiftState.LOCK
            shift == ShiftState.ONCE -> ShiftState.OFF
            else -> ShiftState.ONCE
        }
        lastShiftTap = now
        kb.setShift(shift)
        refreshSuggestions()
    }

    override fun onPage(page: Int) {
        dismissClipboard()
        if (emojiOpen) {
            emojiOpen = false
            kb.setEmojiOpen(false, prefs.recentEmojis())
        }
        this.page = page
        kb.setMode(mode, page)
    }

    override fun onEmojiToggle() {
        dismissClipboard()
        if (!emojiOpen) {
            commitComposing(learn = true)
            learnWord(word.toString())
            word.setLength(0)
        }
        emojiOpen = !emojiOpen
        kb.setEmojiOpen(emojiOpen, prefs.recentEmojis())
    }

    override fun onEmojiPick(emoji: String) {
        dismissClipboard()
        if (currentInputConnection == null || emoji.isEmpty()) return
        if (mode.roman) commitComposing(learn = true)
        learnWord(word.toString())
        word.setLength(0)
        spaceAfterWord = false
        commit(emoji)
        prefs.addRecentEmoji(emoji)
        kb.setRecentEmojis(prefs.recentEmojis())
        updateAutoShift()
        refreshSuggestions()
    }

    override fun onGlobe() {
        dismissClipboard()
        if (latinOnly) return // password / URL / number fields stay Latin
        commitComposing(learn = true)
        learnWord(word.toString())
        word.setLength(0)
        prevWord = ""
        mode = mode.nextEnabled { prefs.isModeEnabled(it) }
        prefs.mode = mode
        page = 0
        autoCaps = mode == Mode.ENGLISH
        shift = ShiftState.OFF
        pushModeToView()
        updateAutoShift()
        refreshSuggestions()
    }

    override fun onGlobeLong() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onReturn() {
        dismissClipboard()
        val ic = currentInputConnection ?: return
        commitComposing(learn = true)
        learnWord(word.toString())
        word.setLength(0)
        prevWord = ""
        spaceAfterWord = false

        val info = currentInputEditorInfo
        val plain = isPlainEnter()
        if (plain) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            if (pred >= 0) {
                pred += 1
                push(pred)
            }
        } else if (info != null && info.actionId != 0 && info.actionLabel != null) {
            ic.performEditorAction(info.actionId)
        } else {
            ic.performEditorAction(action)
        }
        updateAutoShift()
        refreshSuggestions()
    }

    // ------------------------------------------------------------------------------
    // Clipboard Quick-Paste
    // ------------------------------------------------------------------------------

    private fun checkClipboard(isNewClip: Boolean) {
        if (latinOnly || !barAllowed) return
        try {
            val cm = clipboardManager ?: return
            if (!cm.hasPrimaryClip()) return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val item = clip.getItemAt(0) ?: return
            val text = item.coerceToText(this)?.toString()?.trim() ?: return
            if (text.isEmpty() || text == lastPastedText) return
            val now = SystemClock.uptimeMillis()
            if (isNewClip) {
                clipTimestamp = now
            } else {
                val desc = clip.description
                val ageMs = System.currentTimeMillis() - desc.timestamp
                if (ageMs > 60_000L && clipTimestamp == 0L) return
            }
            clipboardText = text
            kb.setClipboardCandidate(text)
        } catch (_: Throwable) {
        }
    }

    private fun dismissClipboard() {
        if (clipboardText != null) {
            clipboardText = null
            kb.setClipboardCandidate(null)
            refreshSuggestions()
        }
    }

    override fun onClipboardPaste() {
        val text = clipboardText ?: return
        commitComposing(learn = true)
        commit(text)
        lastPastedText = text
        clipboardText = null
        kb.setClipboardCandidate(null)
        spaceAfterWord = false
        updateAutoShift()
        refreshSuggestions()
    }

    override fun onClipboardDismiss() {
        dismissClipboard()
    }

    // ------------------------------------------------------------------------------
    // Suggestions
    // ------------------------------------------------------------------------------

    override fun onSuggestion(index: Int) {
        dismissClipboard()
        val s = kb.suggestionAt(index)
        if (s.isEmpty()) return
        val isEmoji = EmojiCatalog.isEmoji(s)
        if (mode.roman && roman.isNotEmpty()) {
            roman.setLength(0)
            commit("$s ") // replaces the composing region
        } else {
            if (word.isNotEmpty()) deleteBefore(word.length)
            commit("$s ")
        }
        word.setLength(0)
        if (isEmoji) {
            prefs.addRecentEmoji(s)
            kb.setRecentEmojis(prefs.recentEmojis())
            spaceAfterWord = false
        } else {
            learnWord(s)
            spaceAfterWord = true
            lastSpaceAt = SystemClock.uptimeMillis()
        }
        updateAutoShift()
        refreshSuggestions()
    }

    /** Long-press a suggestion = forget that word. */
    override fun onSuggestionLong(index: Int) {
        val s = kb.suggestionAt(index)
        if (s.isEmpty() || EmojiCatalog.isEmoji(s)) return
        Native.forget(s)
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        if (!(barAllowed && mode.showsSuggestions)) return
        val prefix = if (mode.roman) composing else word.toString()
        var list = Native.suggest(mode.id, prefix.trimStart('\''), prevWord, 3)
            .split('\n').filter { it.isNotEmpty() }
        if (mode.roman && composing.isNotEmpty()) {
            val variants = Native.transliterateVariants(mode.id, roman.toString())
                .split('\n').filter { it.isNotEmpty() }
            // The literal transliteration always comes first, followed by learned words and phonetic variants.
            list = (listOf(composing) + list + variants).distinct().take(3)
        }
        if (mode == Mode.ENGLISH) {
            val cap = prefix.firstOrNull()?.isUpperCase() == true ||
                (prefix.isEmpty() && shift != ShiftState.OFF)
            list = list.map { englishCase(it, cap) }
        }
        val emojiQuery = when {
            mode.roman && roman.isNotEmpty() -> roman.toString()
            prefix.isNotEmpty() -> prefix
            else -> prevWord
        }
        val emojis = EmojiCatalog.suggestEmojis(emojiQuery, limit = 2)
        if (emojis.isNotEmpty()) {
            // Surface matching emoji(s) on the right side (keeping up to 2 text suggestions).
            list = (list.take(2) + emojis).distinct().take(3)
        }
        kb.setSuggestions(list)
    }

    private fun englishCase(s: String, capitalise: Boolean): String {
        if (s == "i" || s.startsWith("i'")) return "I" + s.substring(1)
        return if (capitalise) s.replaceFirstChar { it.uppercaseChar() } else s
    }

    // ------------------------------------------------------------------------------
    // Learning, auto-shift, return key
    // ------------------------------------------------------------------------------

    private fun learnWord(w: String) {
        if (w.isEmpty()) return
        val ok = learnOn && Native.learn(w, prevWord)
        prevWord = if (ok || !learnOn) w else ""
        if (ok && ++learnedSinceSave >= 25) saveAsync()
    }

    private fun updateAutoShift() {
        if (!autoCaps || shift == ShiftState.LOCK || mode != Mode.ENGLISH) return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val s = if (ic.getCursorCapsMode(info.inputType) != 0) ShiftState.ONCE else ShiftState.OFF
        if (s != shift) {
            shift = s
            kb.setShift(s)
        }
    }

    private fun fieldEmpty(ic: InputConnection): Boolean =
        ic.getTextBeforeCursor(1, 0).isNullOrEmpty() && ic.getTextAfterCursor(1, 0).isNullOrEmpty()

    /**
     * Plain white key + ↵ in multi-line boxes; muted grey when a search/submit
     * field is empty; solid blue when there is something to submit.
     */
    private fun isPlainEnter(): Boolean {
        if (noEnter || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            return true
        }
        // Only treat multiline as plain Enter when the editor didn't request a specific submit/navigation action.
        return multiLine && action != EditorInfo.IME_ACTION_SEARCH &&
            action != EditorInfo.IME_ACTION_GO &&
            action != EditorInfo.IME_ACTION_SEND &&
            action != EditorInfo.IME_ACTION_DONE &&
            action != EditorInfo.IME_ACTION_NEXT &&
            action != EditorInfo.IME_ACTION_PREVIOUS
    }

    private fun updateReturnKey() {
        if (isPlainEnter()) {
            kb.setReturn(ReturnStyle.NEUTRAL, ReturnGlyph.ENTER)
            return
        }
        val glyph = when (action) {
            EditorInfo.IME_ACTION_SEARCH -> ReturnGlyph.SEARCH
            EditorInfo.IME_ACTION_DONE -> ReturnGlyph.CHECK
            else -> ReturnGlyph.ARROW
        }
        val style = when (action) {
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> ReturnStyle.NEUTRAL
            EditorInfo.IME_ACTION_DONE -> ReturnStyle.BLUE
            else -> {
                val ic = currentInputConnection
                if (ic != null && fieldEmpty(ic) && composing.isEmpty()) ReturnStyle.GRAY else ReturnStyle.BLUE
            }
        }
        kb.setReturn(style, glyph)
    }

    /** Letters, combining marks (matras, virama...) and apostrophes belong to a word. */
    private fun isWordChar(c: Char): Boolean {
        if (Character.isLetter(c) || c == '\'' || c == '’' || c == '‌' || c == '‍') return true
        val t = Character.getType(c)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt()
    }
}
