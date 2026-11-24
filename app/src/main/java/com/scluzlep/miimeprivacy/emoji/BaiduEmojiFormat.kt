package com.scluzlep.miimeprivacy.emoji

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pure JVM serializer/parser and compatibility checker for Baidu Xiaomi IME (`com.baidu.input_mi`)
 * custom emoji packs (`files/emoji/{ver}`).
 *
 * Verified against decompiled Baidu v7108:
 * - `l11.b.x0`: UTF-16LE with `0xFF 0xFE` BOM, `3 * N` `\r\n`-terminated lines, `\n` escaped as `\\n`
 * - `fp.a.n()` + `a71.b`: 9 `\r\n`-terminated comma-separated lines (`recent`, `face`, `bear`, ...)
 * - `ao.b.Y`: inserts Fitzpatrick skin modifier immediately before first `\u200D`, or appends if none
 */
object BaiduEmojiFormat {
    data class StockBaiduEntry(
        val puaChar: String,
        val chineseName: String,
        val unicodeText: String,
    )

    data class StockBaiduData(
        val byExactUnicode: Map<String, StockBaiduEntry> = emptyMap(),
        val byStrippedFe0f: Map<String, StockBaiduEntry> = emptyMap(),
        val preferredRecentOrder: List<String> = DEFAULT_RECENT_UNICODE,
    )

    data class BaiduGeneratedFiles(
        val emojiBytes: ByteArray,
        val emojiGroupText: String,
        val changeSkinText: String,
        val errorHintText: String = "",
        val assignedIds: List<Int>,
        val retainedPuaCount: Int,
        val skinEnabledCount: Int,
    )

    private const val BOM_0 = 0xFF.toByte()
    private const val BOM_1 = 0xFE.toByte()
    private const val MAX_RECENT_COUNT = 20

    private val DEFAULT_RECENT_UNICODE = listOf(
        "\uD83D\uDE00", // 😀
        "\uD83D\uDE01", // 😁
        "\uD83D\uDE02", // 😂
        "\uD83E\uDD23", // 🤣
        "\uD83D\uDE0A", // 😊
        "\uD83D\uDE0D", // 😍
        "\uD83E\uDD7A", // 🥺
        "\uD83D\uDE2D", // 😭
        "\uD83D\uDE18", // 😘
        "\uD83D\uDC4D", // 👍
        "\u2764\uFE0F", // ❤️
        "\uD83D\uDD25", // 🔥
        "\u2728",       // ✨
        "\uD83C\uDF89", // 🎉
        "\uD83D\uDE4F", // 🙏
        "\uD83E\uDD14", // 🤔
        "\uD83D\uDE05", // 😅
        "\uD83E\uDD70", // 🥰
        "\uD83D\uDE0E", // 😎
        "\uD83E\uDD1D", // 🤝
    )

    /**
     * Exact Kotlin equivalent of Baidu `l11.b.x0(byte[])`.
     */
    fun parseBaiduEmojiX0(bytes: ByteArray): Array<String>? {
        if (bytes.size < 2 || bytes.size % 2 != 0) return null
        return runCatching {
            val length = bytes.size
            val maxChars = length shr 1
            val chars = CharArray(maxChars)
            var byteIndex = 2 // Skip 2-byte UTF-16LE BOM
            var lineCount = 0
            var charIndex = 0
            while (byteIndex + 1 < length) {
                val ch = (((bytes[byteIndex + 1].toInt() and 0xFF) shl 8) or
                    (bytes[byteIndex].toInt() and 0xFF)).toChar()
                if (ch == '\r') {
                    lineCount++
                    chars[charIndex] = ch
                    byteIndex += 2 // Skip paired '\n' (4 bytes total for \r\n)
                } else if (ch != '\\') {
                    chars[charIndex] = ch
                } else {
                    val nextByteIndex = byteIndex + 2
                    if (nextByteIndex < length && bytes[nextByteIndex].toInt() == 110 /* 'n' */) {
                        chars[charIndex] = '\n'
                        byteIndex = nextByteIndex
                    } else {
                        chars[charIndex] = ch
                    }
                }
                charIndex++
                byteIndex += 2
            }

            val result = Array(lineCount) { "" }
            var tokenLength = 0
            var outIdx = 0
            var tokenStart = 0
            for (i in 0 until maxChars) {
                val ch = chars[i]
                if (ch == '\u0000') break
                if (ch == '\r') {
                    result[outIdx] = String(chars, tokenStart, tokenLength)
                    tokenStart = i + 1
                    outIdx++
                    tokenLength = 0
                } else {
                    tokenLength++
                }
            }
            if (outIdx != lineCount) result.sliceArray(0 until outIdx) else result
        }.getOrNull()
    }

    /**
     * Escapes fields before serializing to `l11.b.x0`-compatible UTF-16LE bytes:
     * strips NUL and `\r`, and escapes literal `\n` as `\\n` (0x005C 0x006E).
     */
    fun sanitizeX0Field(value: String): String {
        val cleaned = buildString(value.length) {
            for (ch in value) {
                when (ch) {
                    '\u0000', '\r' -> Unit
                    '\n' -> append("\\n")
                    else -> append(ch)
                }
            }
        }
        return cleaned
    }

    /**
     * Serializes `3 * N` lines to UTF-16LE with `0xFF 0xFE` BOM and `\r\n` terminators.
     */
    fun encodeBaiduEmojiX0(lines: List<String>): ByteArray {
        require(lines.size % 3 == 0) {
            "Baidu emoji file requires 3 * N lines, got ${lines.size}"
        }
        val out = ByteArrayOutputStream(lines.size * 16 + 2)
        out.write(BOM_0.toInt() and 0xFF)
        out.write(BOM_1.toInt() and 0xFF)
        for (line in lines) {
            val encoded = (sanitizeX0Field(line) + "\r\n").toByteArray(StandardCharsets.UTF_16LE)
            out.write(encoded)
        }
        return out.toByteArray()
    }

    fun parseStockBaiduAssets(emojiBytes: ByteArray): StockBaiduData {
        val parsed = parseBaiduEmojiX0(emojiBytes) ?: return StockBaiduData()
        if (parsed.isEmpty() || parsed.size % 3 != 0) return StockBaiduData()

        val count = parsed.size / 3
        val exact = LinkedHashMap<String, StockBaiduEntry>(count)
        val normalizedBuckets = HashMap<String, MutableList<StockBaiduEntry>>(count)
        for (i in 0 until count) {
            val pua = parsed[i]
            val name = parsed[count + i]
            val unicode = parsed[2 * count + i]
            if (unicode.isEmpty()) continue
            val item = StockBaiduEntry(
                puaChar = pua,
                chineseName = name,
                unicodeText = unicode,
            )
            exact.putIfAbsent(unicode, item)
            val stripped = stripFe0f(unicode)
            normalizedBuckets.getOrPut(stripped) { mutableListOf() }.add(item)
        }

        val unambiguousStripped = HashMap<String, StockBaiduEntry>()
        normalizedBuckets.forEach { (key, list) ->
            if (list.size == 1) {
                unambiguousStripped[key] = list.single()
            }
        }

        return StockBaiduData(
            byExactUnicode = exact,
            byStrippedFe0f = unambiguousStripped,
        )
    }

    /**
     * Exact Kotlin mirror of Baidu `ao.b.Y(String, cp.d)`:
     * inserts [skinUtf] immediately before the first `\u200D` if present,
     * otherwise appends [skinUtf] to the end.
     */
    fun applyBaiduAoBY(baseText: String, skinUtf: String): String {
        val zwjIndex = baseText.indexOf('\u200D')
        return if (zwjIndex >= 0) {
            baseText.substring(0, zwjIndex) + skinUtf + baseText.substring(zwjIndex)
        } else {
            baseText + skinUtf
        }
    }

    /**
     * Returns true iff Baidu's `ao.b.Y` produces every Unicode RGI skin variant `[1F3FB..1F3FF]`
     * identically when given [base].
     */
    fun isSkinSetCompatibleWithAoBY(base: EmojiEntry, orderedVariants: List<EmojiEntry>): Boolean {
        if (orderedVariants.size != SKIN_MODIFIER_COUNT) return false
        for (index in 0 until SKIN_MODIFIER_COUNT) {
            val modifierText = String(Character.toChars(SKIN_MODIFIER_START + index))
            if (applyBaiduAoBY(base.text, modifierText) != orderedVariants[index].text) {
                return false
            }
        }
        return true
    }

    fun buildBaiduCatalogOutput(
        entries: List<EmojiEntry>,
        stock: StockBaiduData = StockBaiduData(),
    ): BaiduGeneratedFiles {
        require(entries.isNotEmpty()) { "Baidu catalog cannot be empty" }

        val n = entries.size
        val assignedIds = List(n) { it + 1 }
        val puaLines = ArrayList<String>(n)
        val nameLines = ArrayList<String>(n)
        val unicodeLines = ArrayList<String>(n)

        val usedPuaChars = HashSet<String>()
        val idByUnicode = HashMap<String, Int>(n)
        val groupBuckets = HashMap<Int, MutableList<Int>>()
        val changeSkinLines = StringBuilder()

        var retainedPuaCount = 0
        var skinEnabledCount = 0

        entries.forEachIndexed { index, entry ->
            val id = index + 1
            val unicodeText = entry.text
            val stockMatch = stock.byExactUnicode[unicodeText]
                ?: stock.byStrippedFe0f[stripFe0f(unicodeText)]

            val pua = stockMatch?.puaChar
                ?.takeIf { it.length == 1 && usedPuaChars.add(it) }
                .orEmpty()
            if (pua.isNotEmpty()) retainedPuaCount++

            val chineseName = stockMatch?.chineseName
                ?.takeIf { it.isNotBlank() }
                ?: "/${entry.englishName}"

            puaLines += pua
            nameLines += chineseName
            unicodeLines += unicodeText
            idByUnicode.putIfAbsent(unicodeText, id)

            val bucketIndex = mapGroupToBaiduLine(entry.group.packageId)
            groupBuckets.getOrPut(bucketIndex) { mutableListOf() }.add(id)

            if (entry.supportsSkin) {
                skinEnabledCount++
                changeSkinLines.append(id).append(",0\n")
            }
        }

        val recentIds = ArrayList<Int>(MAX_RECENT_COUNT)
        val seenRecent = HashSet<Int>(MAX_RECENT_COUNT)
        for (candidateUnicode in stock.preferredRecentOrder) {
            val id = idByUnicode[candidateUnicode] ?: continue
            if (seenRecent.add(id)) {
                recentIds += id
                if (recentIds.size >= MAX_RECENT_COUNT) break
            }
        }
        if (recentIds.size < MAX_RECENT_COUNT) {
            for (id in assignedIds) {
                if (seenRecent.add(id)) {
                    recentIds += id
                    if (recentIds.size >= MAX_RECENT_COUNT) break
                }
            }
        }

        val allLines = ArrayList<String>(3 * n).apply {
            addAll(puaLines)
            addAll(nameLines)
            addAll(unicodeLines)
        }
        val emojiBytes = encodeBaiduEmojiX0(allLines)

        // Exactly 9 \r\n-terminated lines matching Baidu fp.a 9-group baseline
        val emojiGroupText = buildString {
            append(recentIds.joinToString(",")).append("\r\n")
            for (lineIndex in 1..8) {
                val ids = groupBuckets[lineIndex].orEmpty()
                append(ids.joinToString(",")).append("\r\n")
            }
        }

        return BaiduGeneratedFiles(
            emojiBytes = emojiBytes,
            emojiGroupText = emojiGroupText,
            changeSkinText = changeSkinLines.toString(),
            errorHintText = "",
            assignedIds = assignedIds,
            retainedPuaCount = retainedPuaCount,
            skinEnabledCount = skinEnabledCount,
        )
    }

    private fun mapGroupToBaiduLine(packageId: Int): Int = when (packageId) {
        3, 4, 5 -> 1 // face = Smileys & Emotion + People & Body + Component
        6 -> 2       // bear = Animals & Nature
        7 -> 3       // food = Food & Drink
        9 -> 4       // activity = Activities
        8 -> 5       // travel = Travel & Places
        10 -> 6      // object = Objects
        11 -> 7      // symbol = Symbols
        12 -> 8      // flag = Flags
        else -> 1
    }

    private fun stripFe0f(text: String): String = text.replace("\uFE0F", "")

    private const val SKIN_MODIFIER_COUNT = 5
    private const val SKIN_MODIFIER_START = 0x1F3FB
}
