package np.com.siriri.keyboard

/** Thin JNI facade over the Rust core (`rust-core/src/jni_api.rs`). */
object Native {
    init {
        System.loadLibrary("siriri_core")
    }

    /** Opens/creates the learned-word file inside [dir] (the app's private files dir). */
    @JvmStatic external fun init(dir: String)

    /** Serialised key grid; see `layout.rs` for the wire format. */
    @JvmStatic external fun layout(mode: Int, page: Int): String

    /** Roman -> Devanagari for the Roman modes; identity for others. */
    @JvmStatic external fun transliterate(mode: Int, roman: String): String

    /** Up to 3 phonetic Devanagari variants joined by '\n' for Roman modes. */
    @JvmStatic external fun transliterateVariants(mode: Int, roman: String): String

    /** Up to [limit] suggestions joined by '\n'. */
    @JvmStatic external fun suggest(mode: Int, prefix: String, prev: String, limit: Int): String

    @JvmStatic external fun learn(word: String, prev: String): Boolean
    @JvmStatic external fun forget(word: String)

    /** Wipes every learned word from memory and disk. */
    @JvmStatic external fun clearAll()
    @JvmStatic external fun wordCount(): Int
    @JvmStatic external fun save()
}
