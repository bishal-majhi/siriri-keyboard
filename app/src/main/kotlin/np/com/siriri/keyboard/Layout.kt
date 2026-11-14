package np.com.siriri.keyboard

/** One key (or spacer). Geometry is filled in by [KeyboardView]. */
class KeyDef(val kind: KeyKind, val base: String, val shift: String, val weight: Float, val hint: String = "") {
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f

    fun text(shifted: Boolean): String = if (shifted && shift.isNotEmpty()) shift else base
}

class Layout(val cols: Int, val rows: List<List<KeyDef>>) {
    companion object {
        val EMPTY = Layout(10, emptyList())
        private val LATIN_DIGITS = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        private val DEVA_DIGITS = arrayOf("१", "२", "३", "४", "५", "६", "७", "८", "९", "०")

        /** Parses the format produced by `layout::layout` in Rust. */
        fun parse(s: String, mode: Mode = Mode.ENGLISH, page: Int = 0): Layout {
            if (s.isEmpty()) return EMPTY
            val lines = s.split('\n')
            val cols = lines[0].trim().toIntOrNull() ?: return EMPTY
            val digits = if (mode == Mode.NEPALI) DEVA_DIGITS else LATIN_DIGITS
            val rows = lines.drop(1).mapIndexed { rowIdx, line ->
                var charIdx = 0
                line.split('\t').mapNotNull { raw ->
                    val f = raw.split('\u001f')
                    if (f.size < 4) return@mapNotNull null
                    val kind = when (f[0]) {
                        "C" -> KeyKind.CHAR
                        "_" -> KeyKind.SPACER
                        "SHIFT" -> KeyKind.SHIFT
                        "BKSP" -> KeyKind.BKSP
                        "NUM" -> KeyKind.NUM
                        "SYM" -> KeyKind.SYM
                        "ABC" -> KeyKind.ABC
                        "EMOJI" -> KeyKind.EMOJI
                        "GLOBE" -> KeyKind.GLOBE
                        "SPACE" -> KeyKind.SPACE
                        "RET" -> KeyKind.RET
                        else -> return@mapNotNull null
                    }
                    val hint = if (page == 0 && rowIdx == 0 && kind == KeyKind.CHAR && charIdx < digits.size) {
                        digits[charIdx++]
                    } else {
                        ""
                    }
                    KeyDef(kind, f[1], f[2], f[3].toFloatOrNull() ?: 1f, hint)
                }
            }
            return Layout(cols, rows)
        }
    }
}
