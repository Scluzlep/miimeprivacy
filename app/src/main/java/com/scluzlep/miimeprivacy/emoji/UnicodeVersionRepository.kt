package com.scluzlep.miimeprivacy.emoji

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class UnicodeVersionRepository {
    suspend fun fetchStableVersions(): List<UnicodeVersionOption> = withContext(Dispatchers.IO) {
        val publicIndex = downloadText(EmojiCatalog.UNICODE_PUBLIC_URL)
        val latestManifest = downloadText(EmojiCatalog.UNICODE_LATEST_EMOJI_TEST_URL)
        parseStableVersions(publicIndex, EmojiCatalog.emojiVersion(latestManifest))
    }

    suspend fun downloadEmojiTest(
        context: Context,
        option: UnicodeVersionOption,
        status: (String) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        require(FOLDER_PATTERN.matches(option.unicodeFolder)) {
            "无效的 Unicode 版本目录: ${option.unicodeFolder}"
        }
        val targetFile = emojiTestFile(context, option.unicodeFolder)
        if (isValidCachedFile(targetFile, option.emojiVersion)) {
            status("已复用本地 emoji-test ${option.emojiVersion}")
            return@withContext contentUri(context, targetFile)
        }
        targetFile.delete()
        val partFile = File(targetFile.path + ".part")
        partFile.delete()
        try {
            status("正在下载 Unicode ${option.emojiVersion} 清单…")
            val text = downloadText(EmojiCatalog.emojiTestUrl(option.unicodeFolder))
            val actualVersion = EmojiCatalog.emojiVersion(text)
            require(actualVersion == option.emojiVersion) {
                "所选目录返回 Emoji $actualVersion，预期 ${option.emojiVersion}"
            }
            partFile.writeText(text, StandardCharsets.UTF_8)
            require(partFile.renameTo(targetFile)) {
                "无法保存 emoji-test 缓存文件"
            }
            status("已缓存 Unicode ${option.emojiVersion} 清单")
            contentUri(context, targetFile)
        } finally {
            if (partFile.exists()) partFile.delete()
        }
    }

    fun cachedEmojiTestUri(context: Context, option: UnicodeVersionOption): Uri? {
        if (!FOLDER_PATTERN.matches(option.unicodeFolder)) return null
        val file = emojiTestFile(context, option.unicodeFolder)
        return if (isValidCachedFile(file, option.emojiVersion)) contentUri(context, file) else null
    }

    fun isEmojiTestCached(context: Context, option: UnicodeVersionOption): Boolean {
        if (!FOLDER_PATTERN.matches(option.unicodeFolder)) return false
        return isValidCachedFile(emojiTestFile(context, option.unicodeFolder), option.emojiVersion)
    }

    fun loadCachedVersions(context: Context): List<UnicodeVersionOption> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VERSION_LIST, null)
            .orEmpty()
        if (raw.isBlank()) return FALLBACK_VERSIONS
        return runCatching {
            val array = JSONArray(raw)
            val items = ArrayList<UnicodeVersionOption>(array.length())
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val folder = obj.optString("unicodeFolder")
                val version = obj.optString("emojiVersion")
                if (FOLDER_PATTERN.matches(folder) && VERSION_PATTERN.matches(version)) {
                    items += UnicodeVersionOption(folder, version)
                }
            }
            items.ifEmpty { FALLBACK_VERSIONS }
        }.getOrDefault(FALLBACK_VERSIONS)
    }

    fun saveCachedVersions(context: Context, versions: List<UnicodeVersionOption>) {
        if (versions.isEmpty()) return
        val array = JSONArray()
        versions.forEach { option ->
            array.put(
                JSONObject()
                    .put("unicodeFolder", option.unicodeFolder)
                    .put("emojiVersion", option.emojiVersion),
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VERSION_LIST, array.toString())
            .apply()
    }

    private fun emojiTestFile(context: Context, unicodeFolder: String): File {
        val root = File(context.filesDir, "noto-archive")
        require(root.isDirectory || root.mkdirs()) { "无法创建素材缓存目录" }
        return File(root, "emoji-test-$unicodeFolder.txt")
    }

    private fun isValidCachedFile(file: File, expectedEmojiVersion: String): Boolean {
        if (!file.isFile || file.length() !in 1L..MAX_EMOJI_TEST_BYTES) return false
        return runCatching {
            EmojiCatalog.emojiVersion(file.readText(StandardCharsets.UTF_8)) == expectedEmojiVersion
        }.getOrDefault(false)
    }

    private fun contentUri(context: Context, file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.archiveprovider",
        file,
    )

    private fun downloadText(url: String): String {
        var currentUrl = url
        var redirects = 0
        while (true) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Redirect without Location ($code): $currentUrl")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirects++
                    if (redirects > 5) throw IOException("Too many redirects: $url")
                    continue
                }
                if (code !in 200..299) {
                    throw IOException("HTTP $code: $currentUrl")
                }
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    val out = java.io.ByteArrayOutputStream()
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > MAX_EMOJI_TEST_BYTES) {
                            throw IOException("Response exceeds size limit: $currentUrl")
                        }
                        out.write(buffer, 0, read)
                    }
                    return String(out.toByteArray(), StandardCharsets.UTF_8)
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val USER_AGENT = "MiIMEPrivacy/1.0"
        private const val PREFS_NAME = "miime_emoji_patch"
        private const val KEY_VERSION_LIST = "unicode_versions"
        private const val MAX_EMOJI_TEST_BYTES = 16L * 1024 * 1024
        private val FOLDER_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+$")
        private val VERSION_PATTERN = Regex("^\\d+\\.\\d+$")
        private val FOLDER_LINK = Regex("href=[\"'](\\d+\\.\\d+\\.\\d+)/[\"']")

        val FALLBACK_VERSIONS = listOf(
            UnicodeVersionOption("17.0.0", "17.0"),
            UnicodeVersionOption("16.0.0", "16.0"),
            UnicodeVersionOption("15.1.0", "15.1"),
            UnicodeVersionOption("15.0.0", "15.0"),
            UnicodeVersionOption("14.0.0", "14.0"),
        )

        fun parseStableVersions(
            publicIndex: String,
            latestEmojiVersion: String,
        ): List<UnicodeVersionOption> {
            val latest = Version.parse(latestEmojiVersion)
            return FOLDER_LINK.findAll(publicIndex)
                .map { it.groupValues[1] }
                .distinct()
                .mapNotNull { folder ->
                    val version = Version.parseOrNull(folder) ?: return@mapNotNull null
                    if (version.major < 11 || version > latest) return@mapNotNull null
                    UnicodeVersionOption(
                        unicodeFolder = folder,
                        emojiVersion = "${version.major}.${version.minor}",
                    )
                }
                .sortedWith(
                    compareByDescending<UnicodeVersionOption> {
                        Version.parse(it.unicodeFolder)
                    },
                )
                .toList()
        }

        private data class Version(
            val major: Int,
            val minor: Int,
            val patch: Int,
        ) : Comparable<Version> {
            override fun compareTo(other: Version): Int =
                compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

            companion object {
                fun parse(value: String): Version = parseOrNull(value)
                    ?: error("Invalid version: $value")

                fun parseOrNull(value: String): Version? {
                    val components = value.split('.')
                    if (components.size !in 2..3) return null
                    val numbers = components.map { it.toIntOrNull() ?: return null }
                    return Version(numbers[0], numbers[1], numbers.getOrElse(2) { 0 })
                }
            }
        }
    }
}
