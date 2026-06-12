package com.nodaysidle.voiceanywhere.polish

object TextPostProcessor {

    // Standalone filler words (whole-word, case-insensitive)
    private val standaloneFillers = setOf(
        "um", "umm", "uh", "uhh", "erm", "ermm", "ah", "ahh",
        "like", "basically", "literally", "actually", "honestly", "seriously",
        "well", "right", "okay", "ok"
    )

    // Multi-word filler phrases — removed before tokenization
    private val phraseFillers = listOf(
        Regex("""(?i)\byou know\b"""),
        Regex("""(?i)\bi mean\b"""),
        Regex("""(?i)\byou see\b"""),
        Regex("""(?i)\bkind of\b"""),
        Regex("""(?i)\bsort of\b"""),
    )

    // "so we need to…" — "so" as a sentence-opener filler
    private val leadingFillers = listOf(
        Regex("""(?i)^(so|basically|well)\s+"""),
    )

    // Trailing filler words that indicate the sentence end → replace with period
    private val trailingFillerToStop = Regex(
        """(?i)[,\s]+(right|okay|ok|yeah|yep|sure|alright)\s*$"""
    )

    // ── Brand / project names (longest match first) ──────────────────────────
    private val replacements = listOf(
        // NODAYSIDLE brand
        "no days idle"          to "NODAYSIDLE",
        "no day sidle"          to "NODAYSIDLE",   // mis-recognition
        "no days i go"          to "NODAYSIDLE",
        "nodaysidle"            to "NODAYSIDLE",
        "node days idle"        to "NODAYSIDLE",
        "ndi"                   to "NDI",
        "kaly"                  to "Kaly",
        "kill3r"                to "Kill3r",
        "killer"                to "Kill3r",       // voice says "killer" → Kill3r
        "alan"                  to "Alan",
        "alan pfeifer"          to "Alan Pfeifer",
        "pfeifer"               to "Pfeifer",

        // NODAYSIDLE project names
        "voice anywhere"        to "VoiceAnywhere",
        "voice any where"       to "VoiceAnywhere",
        "nodaysidle mobile"     to "nodaysidle-mobile",
        "nodaysidle whispermenu" to "nodaysidle-whispermenu",
        "nodaysidle drawing"    to "nodaysidle-drawing",
        "nodaysidle rawing"     to "nodaysidle-drawing",  // FUTO mis-recognition
        "punto"                 to "Punto",

        // Tech terms NDI uses
        "deep seek"             to "DeepSeek",
        "deepseek"              to "DeepSeek",
        "git lab"               to "GitLab",
        "github"                to "GitHub",
        "git hub"               to "GitHub",
        "kotlin"                to "Kotlin",
        "flutter"               to "Flutter",
        "android"               to "Android",
        "apple"                 to "Apple",
        "mac mini"              to "Mac Mini",
        "pixel"                 to "Pixel",
        "adb"                   to "ADB",
        "apk"                   to "APK",
        "ui"                    to "UI",
        "ux"                    to "UX",
        "api"                   to "API",
        "url"                   to "URL",
        "http"                  to "HTTP",
        "https"                 to "HTTPS",
        "sdk"                   to "SDK",
        "ide"                   to "IDE",
        "llm"                   to "LLM",
        "ai"                    to "AI",
        "ml"                    to "ML",
        "cpu"                   to "CPU",
        "gpu"                   to "GPU",
        "ram"                   to "RAM",
        "ssd"                   to "SSD",
        "wifi"                  to "WiFi",
        "wi fi"                 to "WiFi",
        "bluetooth"             to "Bluetooth",
    )

    // ── Slovenian filler words (removed like English fillers) ─────────────────
    // These are standalone filler/hesitation words common in Slovenian speech
    private val slovenianFillers = setOf(
        "ej", "eej", "ja", "jah",         // hesitation / yeah
        "torej", "skratka",               // "so" / "in short" — sentence openers
        "recimo",                          // "let's say" — filler
        "nekako",                          // "somehow/kind of" — filler
        "pravzaprav",                      // "actually" — filler
        "dejansko",                        // "actually/literally" — filler
        "čisto",                           // "totally/completely" — over-used filler
    )

    // ── Italian filler words ──────────────────────────────────────────────────
    private val italianFillers = setOf(
        "allora",   // "so/then" — sentence opener filler
        "cioè",     // "I mean" / "that is"
        "praticamente", // "basically/practically"
        "tipo",     // "like/kind of" (Italian slang filler)
        "insomma",  // "basically/in short"
        "vabbè",    // "okay/whatever"
        "vabbe",
        "beh",      // "well"
        "mah",      // hesitation
        "ecco",     // "here/there" — often used as filler
        "diciamo",  // "let's say"
    )

    // ── English non-native / dialect patterns → normalized ───────────────────
    // Common mis-recognitions for Slovenian-accented English
    private val dialectNormalizations = listOf(
        // "is" vs "it's" confusion
        Regex("""(?i)\bis is\b""")          to "it is",
        Regex("""(?i)\bthis is is\b""")     to "this is",
        // double articles common in SL→EN
        Regex("""(?i)\bthe the\b""")        to "the",
        Regex("""(?i)\ba a\b""")            to "a",
        // "we need to to" type stutters
        Regex("""(?i)\bto to\b""")          to "to",
        // common accent mis-recognitions
        Regex("""(?i)\bwery\b""")           to "very",   // v→w confusion
    )

    // Functional words whose consecutive duplicates are collapsed
    private val collapsibleWords = setOf(
        "a", "an", "the", "to", "of", "in", "on", "at", "by", "for", "with",
        "from", "and", "or", "but", "that", "this", "these", "those",
        "i", "you", "we", "they", "he", "she", "it", "is", "was", "are", "were"
    )

    // Sentence-ending punctuation → capitalize next word
    private val sentenceEndRegex = Regex("""([.!?]\s+)([a-z])""")

    // Space before punctuation: "hello , world" → "hello, world"
    private val spacedPunctBefore = Regex("""\s+([,.:;!?])""")

    // No space after punctuation: "hello,world" → "hello, world"
    private val spacedPunctAfter = Regex("""([,.:;!?])([^\s\d"'])""")

    fun clean(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) return text

        // 1. Remove phrase fillers
        phraseFillers.forEach { text = it.replace(text, " ") }

        // 2. Remove leading sentence-opener fillers — loop until stable ("basically so …" → "…")
        var prev: String
        do {
            prev = text
            leadingFillers.forEach { text = it.replace(text, "") }
        } while (text != prev)

        // 3. Check for trailing filler (captures sentence end intent) — remove and add period later
        val hadTrailingFiller = trailingFillerToStop.containsMatchIn(text)
        if (hadTrailingFiller) {
            text = trailingFillerToStop.replace(text, "")
        }

        // 3b. Apply dialect normalizations (doubled articles, v→w, etc.)
        dialectNormalizations.forEach { (regex, replacement) -> text = regex.replace(text, replacement) }

        // 4. Tokenize and remove standalone fillers (English + Slovenian + Italian)
        text = text.replace(Regex("""\s+"""), " ").trim()
        val tokens = text.split(" ").filter { it.isNotBlank() }
        val allFillers = standaloneFillers + slovenianFillers + italianFillers
        val cleaned = mutableListOf<String>()
        for (token in tokens) {
            val bare = token.lowercase().trimEnd('.', ',', '!', '?', ';', ':')
            // Only skip if the token IS a bare filler (no attached punctuation that changes meaning)
            val hasSentencePunct = token.endsWith('.') || token.endsWith('!') || token.endsWith('?')
            if (bare in allFillers && !hasSentencePunct) continue
            cleaned.add(token)
        }

        // 5. Collapse consecutive duplicate functional words
        val collapsed = mutableListOf<String>()
        for (token in cleaned) {
            val bare = token.lowercase().trimEnd('.', ',', '!', '?', ';', ':')
            if (collapsed.isNotEmpty() &&
                bare in collapsibleWords &&
                bare == collapsed.last().lowercase().trimEnd('.', ',', '!', '?', ';', ':')
            ) continue
            collapsed.add(token)
        }
        text = collapsed.joinToString(" ").trim()

        if (text.isEmpty()) return text

        // 6. Add period if trailing filler was removed and text doesn't already end in punctuation
        if (hadTrailingFiller) {
            val last = text.last()
            if (last.isLetterOrDigit()) text += "."
        }

        // 7. Brand/name replacements
        replacements.forEach { (from, to) ->
            text = text.replace(Regex("""\b${Regex.escape(from)}\b""", RegexOption.IGNORE_CASE), to)
        }

        // 8. Fix punctuation spacing: remove space before, add space after
        text = spacedPunctBefore.replace(text, "$1")
        text = spacedPunctAfter.replace(text, "$1 $2")

        // 9. Capitalize after sentence-ending punctuation
        text = sentenceEndRegex.replace(text) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }

        // 10. Capitalize first character
        text = text.trim()
        if (text.isEmpty()) return text
        return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
