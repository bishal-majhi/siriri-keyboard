package np.com.siriri.keyboard

/** Typing modes. [id] matches `Mode` in `rust-core/src/lib.rs`. */
enum class Mode(val id: Int, val badge: String, val roman: Boolean, val title: String) {
    ENGLISH(0, "En", false, "English (En)"),
    NEPALI(1, "ने", false, "Traditional Nepali (ने)"),
    NEPALI_ROMAN(2, "Np", true, "Roman Nepali (Np)"),
    HINDI_ROMAN(3, "Hi", true, "Hindi (Hi)");

    /** All modes show the suggestion strip when allowed by the text field. */
    val showsSuggestions: Boolean get() = true

    /** Sentence terminator inserted by double-space. */
    val sentenceEnd: String get() = if (this == ENGLISH) "." else "।"

    val spaceLabel: String get() = when (this) {
        ENGLISH -> "English"
        NEPALI -> "नेपाली"
        HINDI_ROMAN -> "Hindi"
        NEPALI_ROMAN -> "Nepali"
    }

    fun next(): Mode {
        val idx = CYCLE_ORDER.indexOf(this)
        return CYCLE_ORDER[(idx + 1) % CYCLE_ORDER.size]
    }

    fun nextEnabled(isEnabled: (Mode) -> Boolean): Mode {
        val idx = CYCLE_ORDER.indexOf(this).coerceAtLeast(0)
        for (step in 1..CYCLE_ORDER.size) {
            val candidate = CYCLE_ORDER[(idx + step) % CYCLE_ORDER.size]
            if (isEnabled(candidate)) return candidate
        }
        return ENGLISH
    }

    companion object {
        /** Tap cycle order for the language key: En -> ने -> Hi -> Np. */
        val CYCLE_ORDER: List<Mode> = listOf(ENGLISH, NEPALI, HINDI_ROMAN, NEPALI_ROMAN)

        fun fromId(id: Int): Mode = entries.firstOrNull { it.id == id } ?: ENGLISH
    }
}

enum class ShiftState { OFF, ONCE, LOCK }

enum class ReturnStyle { NEUTRAL, GRAY, BLUE }

enum class ReturnGlyph { ENTER, SEARCH, ARROW, CHECK }

enum class KeyKind { CHAR, SPACER, SHIFT, BKSP, NUM, SYM, ABC, EMOJI, GLOBE, SPACE, RET }
