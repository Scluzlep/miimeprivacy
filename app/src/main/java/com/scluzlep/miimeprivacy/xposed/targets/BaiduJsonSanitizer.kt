package com.scluzlep.miimeprivacy.xposed.targets

/**
 * Pure decision outcome for `com.baidu.iptcore.net.IptCloudStream#send(int, byte[], int)`.
 */
enum class StreamSendDecision {
    /** Block CloudLog telemetry streams (`type == 4` or `type == 5`) when telemetry blocking is on. */
    BLOCK_TELEMETRY,

    /** Block live cloud requests when Baidu cloud input is disabled by policy. */
    BLOCK_CLOUD_DISABLED,

    /** Proceed normally so live cloud candidates and `.bcd` dictionary updates keep working. */
    PROCEED
}

/**
 * Pure, framework-free JSON sanitizer and hook decision helpers for [BaiduHookPack].
 */
object BaiduJsonSanitizer {

    private const val MAX_JSON_DEPTH = 64
    private val ZERO_BYTES = byteArrayOf('0'.code.toByte())
    private val ZERO_STRING_BYTES = byteArrayOf('"'.code.toByte(), '0'.code.toByte(), '"'.code.toByte())

    /**
     * Rewrites `"cpn"` inside the `"report"` object of a `CloudInfo#get_json_buf()` UTF-8 JSON
     * payload to `0` when [zeroClipboardCount] is enabled.
     *
     * Returns [bytes] untouched if [zeroClipboardCount] is false, [bytes] is null/empty, JSON is
     * malformed, or `"report"."cpn"` is absent or already `0`.
     */
    fun sanitizeCloudInfoJson(bytes: ByteArray?, zeroClipboardCount: Boolean): ByteArray? {
        if (!zeroClipboardCount || bytes == null || bytes.isEmpty()) {
            return bytes
        }
        val scanner = JsonCpnScanner(bytes)
        val patches = scanner.scanPatches() ?: return bytes
        if (patches.isEmpty()) {
            return bytes
        }
        return applyPatches(bytes, patches)
    }

    /**
     * Decides whether an `IptCloudStream#send(type, buf, len)` call should be blocked or allowed.
     *
     * `type == 4 || type == 5` correspond to `?log_type=7` / `?log_type=9` `cfile` `CloudLog`
     * telemetry streams; all other types carry live cloud candidates and `.bcd` cell dictionary
     * updates.
     */
    fun decideIptCloudStreamSend(
        type: Int,
        blockTelemetry: Boolean,
        cloudEnabled: Boolean
    ): StreamSendDecision {
        return if (type == 4 || type == 5) {
            if (blockTelemetry) StreamSendDecision.BLOCK_TELEMETRY else StreamSendDecision.PROCEED
        } else {
            if (!cloudEnabled) StreamSendDecision.BLOCK_CLOUD_DISABLED else StreamSendDecision.PROCEED
        }
    }

    /**
     * Returns `true` ONLY for `sj0.a` selector `14` (periodic KP data upload), preserving
     * `case 9` (personal dictionary sync) and all other task selectors.
     */
    fun shouldBlockSj0Task(selector: Int, blockTelemetry: Boolean): Boolean =
        blockTelemetry && selector == 14

    /**
     * Fixes Baidu's inverted `dj0.l.I1` privacy-mode logic so clipboard listeners, silent DB
     * writes, and clipboard readers are blocked whenever privacy input mode (`dj0.l.I1`) is active
     * OR user privacy consent (`2439`) has not been granted, while keeping normal local typing
     * paste and clipboard bar functional otherwise.
     */
    fun shouldBlockPrivacyClipboard(
        clipboardPrivacy: Boolean,
        privacyAgreed: Boolean,
        privacyInputMode: Boolean
    ): Boolean {
        if (!clipboardPrivacy) return false
        return !privacyAgreed || privacyInputMode
    }

    /**
     * Returns `true` ONLY for `fg.w` selector `5` (`mobile_log_salvage`).
     */
    fun shouldBlockSalvageTask(selector: Int, blockRemoteLogSalvage: Boolean): Boolean =
        blockRemoteLogSalvage && selector == 5

    /**
     * Returns `true` ONLY for `rj0.b` selector `1` (installed app scan task).
     */
    fun shouldBlockAppScanTask(selector: Int, blockContactsAndAppScan: Boolean): Boolean =
        blockContactsAndAppScan && selector == 1

    /**
     * Returns `true` ONLY when querying or auto-enabling Baidu settings bit `1839`
     * (default-agree UBC/telemetry bypass), leaving `2439` and user keys intact.
     */
    fun shouldBlockBit1839Consent(prefKey: Int, blockTelemetry: Boolean): Boolean =
        blockTelemetry && prefKey == 1839

    /**
     * Returns `true` only when optional strict Baidu HTTPDNS blocking is enabled.
     */
    fun shouldBlockHttpDns(blockHttpDns: Boolean): Boolean = blockHttpDns

    /**
     * Resolves RxJava `Observable.empty()` (`0`-arg static method `empty`) for `fg.w#b(x6.a)`
     * so blocked `case 5` returns a non-null empty `Observable` without NPE in `.flatMap(...)`.
     */
    fun findRxEmptyObservableMethod(returnType: Class<*>): java.lang.reflect.Method? =
        runCatching { returnType.getMethod("empty") }.getOrNull()

    /**
     * Invokes a resolved RxJava `Observable.empty()` method safely.
     */
    fun invokeRxEmptyObservable(emptyMethod: java.lang.reflect.Method?): Any? =
        emptyMethod?.let { runCatching { it.invoke(null) }.getOrNull() }

    /**
     * Resolves obfuscated `pe1.x.e(Object)` (`Single.just(Object)`) for `fg.w#c(kh0.j)` so blocked
     * `case 5` returns a non-null `Single<Boolean>` (`false`) without NPE in `.f(...)`.
     */
    fun findRxSingleJustMethod(returnType: Class<*>): java.lang.reflect.Method? =
        runCatching { returnType.getMethod("e", Any::class.java) }.getOrNull()

    /**
     * Invokes `pe1.x.e(value)` cleanly for a boolean completion value.
     */
    fun invokeRxSingleBoolean(singleJustMethod: java.lang.reflect.Method?, value: Boolean): Any? =
        singleJustMethod?.let { runCatching { it.invoke(null, value) }.getOrNull() }

    private fun applyPatches(original: ByteArray, patches: List<BytePatch>): ByteArray {
        var delta = 0
        for (patch in patches) {
            delta += patch.replacement.size - (patch.endExclusive - patch.startInclusive)
        }
        val out = ByteArray(original.size + delta)
        var srcPos = 0
        var dstPos = 0
        for (patch in patches) {
            val prefixLen = patch.startInclusive - srcPos
            if (prefixLen > 0) {
                System.arraycopy(original, srcPos, out, dstPos, prefixLen)
                dstPos += prefixLen
            }
            System.arraycopy(patch.replacement, 0, out, dstPos, patch.replacement.size)
            dstPos += patch.replacement.size
            srcPos = patch.endExclusive
        }
        val tailLen = original.size - srcPos
        if (tailLen > 0) {
            System.arraycopy(original, srcPos, out, dstPos, tailLen)
        }
        return out
    }

    private class BytePatch(
        val startInclusive: Int,
        val endExclusive: Int,
        val replacement: ByteArray
    )

    private class JsonCpnScanner(private val bytes: ByteArray) {
        private var pos = 0
        private val patches = ArrayList<BytePatch>(2)

        fun scanPatches(): List<BytePatch>? {
            skipUtf8Bom()
            skipWhitespace()
            if (pos >= bytes.size || bytes[pos].toInt() != '{'.code) {
                return null
            }
            if (!parseObject(insideReport = false, depth = 0)) {
                return null
            }
            skipWhitespace()
            return if (pos == bytes.size) patches else null
        }

        private fun skipUtf8Bom() {
            if (bytes.size >= 3 &&
                (bytes[0].toInt() and 0xFF) == 0xEF &&
                (bytes[1].toInt() and 0xFF) == 0xBB &&
                (bytes[2].toInt() and 0xFF) == 0xBF
            ) {
                pos = 3
            }
        }

        private fun parseObject(insideReport: Boolean, depth: Int): Boolean {
            if (depth >= MAX_JSON_DEPTH || pos >= bytes.size || bytes[pos].toInt() != '{'.code) {
                return false
            }
            pos++ // '{'
            skipWhitespace()
            if (pos < bytes.size && bytes[pos].toInt() == '}'.code) {
                pos++
                return true
            }
            while (true) {
                skipWhitespace()
                val keyStart = pos
                if (!skipString()) return false
                val keyEnd = pos
                val key = decodeJsonString(keyStart, keyEnd)

                skipWhitespace()
                if (pos >= bytes.size || bytes[pos].toInt() != ':'.code) return false
                pos++ // ':'
                skipWhitespace()

                val valStart = pos
                if (!parseValueForMember(insideReport, key, depth + 1)) return false
                val valEnd = pos

                if (insideReport && key == "cpn") {
                    maybeRecordCpnPatch(valStart, valEnd)
                }

                skipWhitespace()
                if (pos >= bytes.size) return false
                when (bytes[pos].toInt()) {
                    ','.code -> pos++
                    '}'.code -> {
                        pos++
                        return true
                    }
                    else -> return false
                }
            }
        }

        private fun parseValueForMember(
            insideReport: Boolean,
            key: String?,
            depth: Int
        ): Boolean {
            if (pos >= bytes.size) return false
            return when (bytes[pos].toInt()) {
                '{'.code -> {
                    val childIsReport = !insideReport && key == "report"
                    parseObject(insideReport = childIsReport, depth = depth)
                }
                else -> skipValue(depth)
            }
        }

        private fun maybeRecordCpnPatch(start: Int, end: Int) {
            if (end <= start) return
            val first = bytes[start].toInt()
            if (first == '"'.code) {
                if (end - start == 3 &&
                    bytes[start + 1].toInt() == '0'.code &&
                    bytes[start + 2].toInt() == '"'.code
                ) {
                    return
                }
                patches.add(BytePatch(start, end, ZERO_STRING_BYTES))
                return
            }
            if (first == '-'.code || first in '0'.code..'9'.code) {
                if (end - start == 1 && first == '0'.code) {
                    return
                }
                patches.add(BytePatch(start, end, ZERO_BYTES))
            }
        }

        private fun skipValue(depth: Int): Boolean {
            if (depth >= MAX_JSON_DEPTH || pos >= bytes.size) return false
            return when (bytes[pos].toInt()) {
                '{'.code -> parseObject(insideReport = false, depth = depth)
                '['.code -> skipArray(depth)
                '"'.code -> skipString()
                't'.code -> matchLiteral("true")
                'f'.code -> matchLiteral("false")
                'n'.code -> matchLiteral("null")
                '-'.code, in '0'.code..'9'.code -> skipNumber()
                else -> false
            }
        }

        private fun skipArray(depth: Int): Boolean {
            if (depth >= MAX_JSON_DEPTH || pos >= bytes.size || bytes[pos].toInt() != '['.code) {
                return false
            }
            pos++ // '['
            skipWhitespace()
            if (pos < bytes.size && bytes[pos].toInt() == ']'.code) {
                pos++
                return true
            }
            while (true) {
                skipWhitespace()
                if (!skipValue(depth + 1)) return false
                skipWhitespace()
                if (pos >= bytes.size) return false
                when (bytes[pos].toInt()) {
                    ','.code -> pos++
                    ']'.code -> {
                        pos++
                        return true
                    }
                    else -> return false
                }
            }
        }

        private fun skipString(): Boolean {
            if (pos >= bytes.size || bytes[pos].toInt() != '"'.code) return false
            pos++
            while (pos < bytes.size) {
                val b = bytes[pos].toInt() and 0xFF
                when {
                    b == '"'.code -> {
                        pos++
                        return true
                    }
                    b == '\\'.code -> {
                        pos++
                        if (pos >= bytes.size) return false
                        val esc = bytes[pos].toInt() and 0xFF
                        pos++
                        when (esc) {
                            '"'.code, '\\'.code, '/'.code, 'b'.code, 'f'.code,
                            'n'.code, 'r'.code, 't'.code -> Unit
                            'u'.code -> {
                                if (pos + 4 > bytes.size) return false
                                for (i in 0 until 4) {
                                    if (!isHexDigit(bytes[pos + i].toInt() and 0xFF)) return false
                                }
                                pos += 4
                            }
                            else -> return false
                        }
                    }
                    b < 0x20 -> return false
                    else -> pos++
                }
            }
            return false
        }

        private fun skipNumber(): Boolean {
            if (pos < bytes.size && bytes[pos].toInt() == '-'.code) {
                pos++
            }
            if (pos >= bytes.size) return false
            val first = bytes[pos].toInt() and 0xFF
            if (first == '0'.code) {
                pos++
            } else if (first in '1'.code..'9'.code) {
                while (pos < bytes.size && (bytes[pos].toInt() and 0xFF) in '0'.code..'9'.code) {
                    pos++
                }
            } else {
                return false
            }
            if (pos < bytes.size && bytes[pos].toInt() == '.'.code) {
                pos++
                if (pos >= bytes.size || (bytes[pos].toInt() and 0xFF) !in '0'.code..'9'.code) {
                    return false
                }
                while (pos < bytes.size && (bytes[pos].toInt() and 0xFF) in '0'.code..'9'.code) {
                    pos++
                }
            }
            if (pos < bytes.size && (bytes[pos].toInt() == 'e'.code || bytes[pos].toInt() == 'E'.code)) {
                pos++
                if (pos < bytes.size && (bytes[pos].toInt() == '+'.code || bytes[pos].toInt() == '-'.code)) {
                    pos++
                }
                if (pos >= bytes.size || (bytes[pos].toInt() and 0xFF) !in '0'.code..'9'.code) {
                    return false
                }
                while (pos < bytes.size && (bytes[pos].toInt() and 0xFF) in '0'.code..'9'.code) {
                    pos++
                }
            }
            return true
        }

        private fun matchLiteral(literal: String): Boolean {
            if (pos + literal.length > bytes.size) return false
            for (i in literal.indices) {
                if ((bytes[pos + i].toInt() and 0xFF) != literal[i].code) return false
            }
            pos += literal.length
            return true
        }

        private fun skipWhitespace() {
            while (pos < bytes.size) {
                when (bytes[pos].toInt()) {
                    ' '.code, '\t'.code, '\r'.code, '\n'.code -> pos++
                    else -> return
                }
            }
        }

        private fun decodeJsonString(startInclusive: Int, endExclusive: Int): String? {
            if (endExclusive - startInclusive < 2) return null
            val innerStart = startInclusive + 1
            val innerEnd = endExclusive - 1
            var hasEscape = false
            for (i in innerStart until innerEnd) {
                if (bytes[i].toInt() == '\\'.code) {
                    hasEscape = true
                    break
                }
            }
            if (!hasEscape) {
                // Fast exit before allocating when key length cannot match "report" (6) or "cpn" (3).
                val len = innerEnd - innerStart
                if (len != 3 && len != 6) return null
                return String(bytes, innerStart, len, Charsets.UTF_8)
            }
            val raw = String(bytes, innerStart, innerEnd - innerStart, Charsets.UTF_8)
            val sb = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c != '\\') {
                    sb.append(c)
                    i++
                    continue
                }
                i++
                if (i >= raw.length) return null
                when (val esc = raw[i++]) {
                    '"', '\\', '/' -> sb.append(esc)
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (i + 4 > raw.length) return null
                        val codePoint = raw.substring(i, i + 4).toIntOrNull(16) ?: return null
                        sb.append(codePoint.toChar())
                        i += 4
                    }
                    else -> return null
                }
            }
            return sb.toString()
        }

        private fun isHexDigit(c: Int): Boolean =
            c in '0'.code..'9'.code || c in 'a'.code..'f'.code || c in 'A'.code..'F'.code
    }
}
