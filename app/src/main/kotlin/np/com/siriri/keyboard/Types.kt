package np.com.siriri.keyboard

/** Typing modes. [id] matches `Mode` in `rust-core/src/lib.rs`. */
enum class Mode(val id: Int, val badge: String, val roman: Boolean, val title: String) {
    ENGLISH(0, "A", false, "English"),
    NEPALI(1, "क", false, "Traditional Nepali (क)"),
    NEPALI_ROMAN(2, "ने", true, "Nepali Roman (ने)"),
    HINDI_ROMAN(3, "हि", true, "Hindi Roman (हि)");

    /** All modes show the suggestion strip when allowed by the text field. */
    val showsSuggestions: Boolean get() = true

    /** Sentence terminator inserted by double-space. */
    val sentenceEnd: String get() = if (this == ENGLISH) "." else "।"

    fun next(): Mode = entries[(ordinal + 1) % entries.size]

    fun nextEnabled(isEnabled: (Mode) -> Boolean): Mode {
        val all = entries
        for (step in 1..all.size) {
            val candidate = all[(ordinal + step) % all.size]
            if (isEnabled(candidate)) return candidate
        }
        return ENGLISH
    }

    companion object {
        fun fromId(id: Int): Mode = entries.firstOrNull { it.id == id } ?: ENGLISH
    }
}

enum class ShiftState { OFF, ONCE, LOCK }

enum class ReturnStyle { NEUTRAL, GRAY, BLUE }

enum class ReturnGlyph { ENTER, SEARCH, ARROW, CHECK }

enum class KeyKind { CHAR, SPACER, SHIFT, BKSP, NUM, SYM, ABC, GLOBE, SPACE, RET }
