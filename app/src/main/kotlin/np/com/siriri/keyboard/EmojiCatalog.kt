package np.com.siriri.keyboard

/** Offline emoji picker catalog and keyword-to-emoji prediction table. */
object EmojiCatalog {

    data class Category(val id: String, val icon: String, val title: String, val emojis: List<String>)

    val DEFAULT_RECENTS: List<String> = listOf(
        "😂", "❤️", "🙏", "😊", "😭", "👍", "🔥", "🥰",
        "🤣", "😍", "😘", "🥺", "👌", "🎉", "🇳🇵", "💯",
    )

    val CATEGORIES: List<Category> = listOf(
        Category(
            id = "recent",
            icon = "🕒",
            title = "Recent",
            emojis = DEFAULT_RECENTS,
        ),
        Category(
            id = "smileys",
            icon = "😀",
            title = "Smileys",
            emojis = listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
                "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
                "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪",
                "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
                "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "😌",
                "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢",
                "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠",
                "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁", "☹️",
                "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨",
                "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞",
                "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬",
                "😈", "👿", "💀", "💩", "🤡", "👻", "👽", "🤖",
            ),
        ),
        Category(
            id = "hands",
            icon = "🙏",
            title = "Hearts & Gestures",
            emojis = listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
                "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
                "💘", "💝", "💟", "🙏", "👋", "🤚", "🖐️", "✋",
                "🖖", "👌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
                "👈", "👉", "👆", "👇", "☝️", "👍", "👎", "✊",
                "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
                "💪", "🫶", "👀", "🫂",
            ),
        ),
        Category(
            id = "celebration",
            icon = "🎉",
            title = "Celebration",
            emojis = listOf(
                "🎉", "🎊", "🎈", "🎁", "🎂", "🕯️", "🎆", "🎇",
                "✨", "🏆", "🥇", "🥈", "🥉", "⚽", "🏏", "🏀",
                "🎵", "🎶", "🎸", "🎤", "🎧", "🔥", "💥", "⭐",
                "🌟", "💫", "⚡", "☀️", "🌙", "🌈", "☁️", "🌧️",
            ),
        ),
        Category(
            id = "nature",
            icon = "🌸",
            title = "Animals & Nature",
            emojis = listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
                "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈",
                "🙉", "🙊", "🐔", "🐧", "🐦", "🦅", "🦉", "🦋",
                "🐛", "🐝", "🌸", "🌺", "🌻", "🌹", "🌷", "🌱",
                "🌲", "🌳", "🌴", "🍀", "🌿", "🌾", "🌊", "❄️",
            ),
        ),
        Category(
            id = "food",
            icon = "🍵",
            title = "Food & Drink",
            emojis = listOf(
                "☕", "🍵", "🧃", "🍺", "🍻", "🥂", "🍷", "🥤",
                "🍕", "🍔", "🍟", "🌭", "🍿", "🥟", "🍛", "🍜",
                "🍝", "🍚", "🍞", "🥐", "🧀", "🍗", "🍖", "🍎",
                "🍌", "🥭", "🍇", "🍓", "🍉", "🍊", "🍋", "🍍",
                "🥥", "🥑", "🌶️", "🍰", "🍫", "🍬", "🍭", "🍦",
            ),
        ),
        Category(
            id = "travel",
            icon = "🏔️",
            title = "Travel & Objects",
            emojis = listOf(
                "🏠", "🏡", "🏢", "🏫", "🏥", "🕌", "🛕", "⛪",
                "⛰️", "🏔️", "🗻", "✈️", "🚀", "🚗", "🚕", "🚌",
                "🚂", "🚲", "🛵", "📱", "💻", "📷", "🎥", "📞",
                "💡", "📚", "✏️", "🔒", "🔑", "💰", "💵", "🛒",
                "⏰", "⌛", "🧭", "🎒", "☂️", "🕶️", "💊", "🩺",
            ),
        ),
        Category(
            id = "flags",
            icon = "🇳🇵",
            title = "Flags & Symbols",
            emojis = listOf(
                "🇳🇵", "🇮🇳", "🇺🇸", "🇬🇧", "🇦🇺", "🇨🇦", "🇯🇵", "🇰🇷",
                "🇨🇳", "🇩🇪", "🇫🇷", "🇦🇪", "🇶🇦", "🇸🇦", "🇲🇾", "🇸🇬",
                "✅", "❌", "❓", "❗", "💯", "💢", "💬", "💭",
                "🔔", "🔕", "➕", "➖", "➗", "♻️", "☑️", "⚠️",
                "⛔", "🚫", "🔴", "🟢", "🔵", "🟡", "🟠", "🟣",
            ),
        ),
    )

    private val KEYWORDS: Map<String, List<String>> = buildMap {
        fun bind(emojis: List<String>, vararg words: String) {
            for (w in words) put(w, emojis)
        }

        // Laughter / humor
        bind(listOf("😂", "🤣"), "haha", "hahaha", "lol", "lmao", "rofl", "hehe", "funny", "joke", "hasho", "haso", "हाँसो")
        // Love / affection
        bind(listOf("❤️", "🥰"), "love", "luv", "ily", "heart", "maya", "pyar", "pyaar", "prem", "dil", "माया", "प्रेम", "प्यार", "दिल")
        bind(listOf("😍", "💖"), "crush", "lovely", "beautiful", "cute", "ramri", "sundar", "सुन्दर", "राम्री")
        bind(listOf("😘", "💋"), "kiss", "muah", "mwah", "chumma")
        bind(listOf("🤗", "🫂"), "hug", "hugs", "angalo")
        // Happy / smile
        bind(listOf("😊", "😄"), "happy", "smile", "glad", "joy", "khusi", "khushi", "ramailo", "खुसी", "खुशी", "रमाइलो")
        bind(listOf("😁", "🤩"), "excited", "yay", "woohoo", "hurray")
        bind(listOf("😎", "🔥"), "cool", "swag", "boss", "attitude")
        bind(listOf("🥳", "🎉"), "party", "celebrate", "congrats", "congratulations", "badhai", "बधाई")
        // Sad / crying / sorry
        bind(listOf("😭", "😢"), "sad", "cry", "crying", "sobbing", "tears", "dukkha", "dukha", "runu", "दुःख", "दुख", "रुनु")
        bind(listOf("😔", "🥺"), "miss", "missed", "missing", "lonely", "yaad", "याद", "please", "pls", "plz")
        bind(listOf("🙏", "😔"), "sorry", "apology", "maaf", "maafi", "माफ", "माफी")
        bind(listOf("💔"), "broken", "heartbreak", "breakup")
        // Greetings / respect / thanks
        bind(listOf("🙏", "✨"), "namaste", "namaskar", "pranam", "namaskaram", "नमस्ते", "नमस्कार", "प्रणाम")
        bind(listOf("🙏", "😊"), "thanks", "thank", "thankyou", "thx", "ty", "dhanyabad", "dhanyawad", "धन्यवाद", "welcome")
        bind(listOf("👋", "😊"), "hi", "hello", "hey", "hii", "bye", "goodbye", "tata", "namaste!")
        // Approval / disagreement
        bind(listOf("👍", "👌"), "ok", "okay", "yes", "yep", "yeah", "sure", "thik", "thikai", "good", "nice", "great", "best", "super", "awesome", "ramro", "babal", "dami", "dammi", "mast", "राम्रो", "ठिक", "ठिकै")
        bind(listOf("👎", "🙅"), "no", "nope", "nah", "bad", "naramro", "नराम्रो")
        bind(listOf("💯", "🔥"), "100", "hundred", "perfect", "exact", "pakka", "पक्का")
        // Anger / shock / thinking / sleep / sick
        bind(listOf("😡", "😠"), "angry", "mad", "hate", "gussa", "ris", "रिस", "गुस्सा")
        bind(listOf("🤯", "😮"), "wow", "omg", "shocked", "mindblown", "ooh", "वाह")
        bind(listOf("🤔", "🧐"), "think", "thinking", "hmm", "hmmm", "wonder", "kya")
        bind(listOf("😴", "🌙"), "sleep", "sleepy", "tired", "gn", "goodnight", "night", "nindra", "sutne", "निद्रा", "राती")
        bind(listOf("🤒", "😷"), "sick", "ill", "fever", "cough", "cold", "birami", "joro", "बिरामी", "ज्वरो")
        bind(listOf("🔥", "💥"), "fire", "lit", "hot", "aago", "आगो")
        // Applause / success
        bind(listOf("👏", "🏆"), "clap", "bravo", "win", "winner", "won", "trophy", "champ", "champion", "jit", "जित")
        // Time of day / weather
        bind(listOf("☀️", "🌄"), "gm", "morning", "goodmorning", "sun", "sunny", "bihana", "subah", "बिहान")
        bind(listOf("🌧️", "☔"), "rain", "raining", "umbrella", "barsha", "झरी", "वर्षा")
        bind(listOf("⭐", "🌟"), "star", "stars", "tara", "तारा")
        bind(listOf("🌈", "✨"), "rainbow", "indreni", "इन्द्रेणी")
        // Celebrations / gifts / flowers
        bind(listOf("🎂", "🎉"), "birthday", "bday", "cake", "janmadin", "जन्मदिन")
        bind(listOf("🎁", "🎈"), "gift", "present", "surprise", "upahar", "उपहार")
        bind(listOf("🌹", "🌸"), "flower", "rose", "flowers", "phul", "phool", "फूल")
        // Food & drink
        bind(listOf("🍵", "☕"), "tea", "chiya", "chai", "चिया")
        bind(listOf("☕"), "coffee", "कफी")
        bind(listOf("🥟", "🍛"), "momo", "momos", "food", "eat", "dinner", "lunch", "khana", "bhat", "मम", "खाना", "भात")
        bind(listOf("🍕"), "pizza")
        bind(listOf("🍔"), "burger")
        bind(listOf("💧"), "water", "pani", "paani", "पानी")
        bind(listOf("🍻", "🍺"), "beer", "cheers", "drink", "drinks", "partytime")
        // Music / media / tech / work / money
        bind(listOf("🎵", "🎶"), "music", "song", "songs", "geet", "गीत")
        bind(listOf("📷", "📸"), "photo", "pic", "picture", "camera", "selfie", "फोटो")
        bind(listOf("📞", "📱"), "call", "phone", "ring", "फोन")
        bind(listOf("💰", "💵"), "money", "cash", "paisa", "rich", "पैसा")
        bind(listOf("💻", "📚"), "work", "office", "study", "book", "books", "padhai", "काम", "पढाइ")
        // Places / travel / countries
        bind(listOf("🏠", "🏡"), "home", "house", "ghar", "घर")
        bind(listOf("🏔️", "⛰️"), "mountain", "mountains", "himal", "everest", "sagarmatha", "हिमाल", "सगरमाथा")
        bind(listOf("🚗", "🛵"), "car", "bike", "ride", "drive", "bus", "गाडी")
        bind(listOf("✈️", "🚀"), "plane", "flight", "travel", "trip", "fly")
        bind(listOf("🇳🇵"), "nepal", "nepali", "kathmandu", "pokhara", "नेपाल", "नेपाली")
        bind(listOf("🇮🇳"), "india", "indian", "hindi", "delhi", "mumbai", "भारत")
        // Animals
        bind(listOf("🐶"), "dog", "puppy", "kukur", "कुकुर")
        bind(listOf("🐱"), "cat", "kitty", "biralo", "बिरालो")
    }

    /** Returns matching emoji suggestions for the active or just-finished word. */
    fun suggestEmojis(raw: String, limit: Int = 2): List<String> {
        val q = raw.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        KEYWORDS[q]?.let { return it.take(limit) }

        // Handle elongated laughter/reactions such as "hahaha", "hahahaha", "hehee", "yessss".
        if (q.matches(Regex("(ha){2,}h?"))) return KEYWORDS["haha"]!!.take(limit)
        if (q.matches(Regex("(he){2,}h?"))) return KEYWORDS["hehe"]!!.take(limit)
        val squeezed = q.replace(Regex("(.)\\1{2,}"), "$1$1")
        KEYWORDS[squeezed]?.let { return it.take(limit) }
        val single = q.replace(Regex("(.)\\1+"), "$1")
        KEYWORDS[single]?.let { return it.take(limit) }
        return emptyList()
    }

    /** True when [s] is an emoji suggestion rather than a Latin/Devanagari word. */
    fun isEmoji(s: String): Boolean {
        if (s.isEmpty()) return false
        val cp = s.codePointAt(0)
        return cp >= 0x2000 && !s.any { Character.isLetter(it) }
    }
}
