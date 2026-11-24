package com.scluzlep.miimeprivacy.emoji

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Owns Noto Emoji archive downloads/imports inside MiIMEPrivacy's private `filesDir/noto-archive/`
 * and slims imported archives down to `png/128` and `third_party/region-flags/png` PNG entries.
 */
object NotoArchiveCache {
    private const val MAX_ZIP_BYTES = 1024L * 1024L * 1024L
    private const val MAX_IMAGES = 10_000
    private const val MAX_SINGLE_PNG = 2L * 1024 * 1024
    private const val MAX_TOTAL_PNG_BYTES = 512L * 1024 * 1024
    private const val IMAGE_PREFIX = "png/128/"
    private val downloadUrl: String
        get() = "https://codeload.github.com/googlefonts/noto-emoji/zip/refs/tags/${EmojiCatalog.NOTO_VERSION}"

    private fun archive(context: Context): File {
        val root = File(context.filesDir, "noto-archive")
        require(root.isDirectory || root.mkdirs()) { "无法建立 ZIP 缓存目录" }
        return File(root, "noto-emoji-${EmojiCatalog.NOTO_VERSION}.zip")
    }

    private fun validate(file: File) {
        require(file.isFile && file.length() in 1..MAX_ZIP_BYTES) {
            "ZIP 文件为空、过大或不存在"
        }
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            var imageCount = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val normalized = entry.name.replace('\\', '/')
                if (!entry.isDirectory &&
                    !normalized.contains("..") &&
                    (normalized.contains("/$IMAGE_PREFIX") || normalized.startsWith(IMAGE_PREFIX)) &&
                    normalized.endsWith(".png", ignoreCase = true)
                ) {
                    imageCount++
                    require(imageCount <= MAX_IMAGES) { "ZIP 包含过多 PNG" }
                }
            }
            require(imageCount >= 1000) {
                "不是完整的 Noto Emoji ZIP（仅找到 $imageCount 张 png/128 图像）"
            }
        }
    }

    suspend fun download(context: Context, status: (String) -> Unit): Uri = withContext(Dispatchers.IO) {
        val destination = archive(context)
        if (destination.exists()) {
            if (runCatching { validate(destination) }.isSuccess) {
                status("已复用本地 Noto ZIP 缓存")
                return@withContext contentUri(context, destination)
            }
            destination.delete()
        }
        val partial = File(destination.path + ".part")
        if (partial.length() > MAX_ZIP_BYTES) partial.delete()
        status("开始下载 Noto Emoji ${EmojiCatalog.NOTO_VERSION}（支持断点续传）…")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MiIMEPrivacy-NotoCache/1.0")
            }
            val existing = partial.length()
            if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")
            val response = connection.responseCode
            require(response == HttpURLConnection.HTTP_OK || response == HttpURLConnection.HTTP_PARTIAL) {
                "ZIP 下载失败 HTTP $response"
            }
            val append = existing > 0 && response == HttpURLConnection.HTTP_PARTIAL
            if (append) {
                val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                require(contentRange.startsWith("bytes $existing-")) {
                    "服务器返回的 Content-Range 不匹配，无法续传：$contentRange"
                }
            }
            val expectedTotal = (connection.contentLengthLong.takeIf { it >= 0 } ?: 0L) +
                if (append) existing else 0L
            var read = if (append) existing else 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var lastReport = read
                    while (true) {
                        val bytes = input.read(buffer)
                        if (bytes < 0) break
                        read += bytes
                        require(read <= MAX_ZIP_BYTES) { "ZIP 超过 1 GiB 安全限制" }
                        output.write(buffer, 0, bytes)
                        if (read - lastReport >= 4L * 1024 * 1024) {
                            val progress = if (expectedTotal > 0) " / ${expectedTotal / 1048576} MiB" else ""
                            status("已下载 ${read / 1048576} MiB$progress")
                            lastReport = read
                        }
                    }
                    output.flush()
                }
            }
            status("正在校验 ZIP 目录…")
            validate(partial)
            status("正在裁剪冗余源码（保留 PNG 与旗帜）…")
            makeSlimArchive(partial, destination)
            partial.delete()
            status("素材包已缓存，可离线重复安装")
            contentUri(context, destination)
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun importZip(context: Context, selected: Uri, status: (String) -> Unit): Uri = withContext(Dispatchers.IO) {
        val destination = archive(context)
        val temp = File(destination.path + ".import")
        temp.delete()
        try {
            status("正在导入本地 ZIP…")
            context.contentResolver.openInputStream(selected).use { input ->
                requireNotNull(input) { "无法读取所选 ZIP" }
                temp.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ZIP_BYTES) { "ZIP 超过 1 GiB 安全限制" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            validate(temp)
            status("正在裁剪冗余源码…")
            makeSlimArchive(temp, destination)
            status("ZIP 导入完成，可离线使用")
            contentUri(context, destination)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** Only copies normalized PNG and region flag members into the slim archive. */
    private fun makeSlimArchive(source: File, destination: File) {
        val ready = File(destination.path + ".ready")
        ready.delete()
        try {
            ZipFile(source).use { zip ->
                ZipOutputStream(ready.outputStream().buffered()).use { output ->
                    val entries = zip.entries()
                    val seen = HashSet<String>()
                    var cumulative = 0L
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val path = entry.name.replace('\\', '/')
                        if (path.contains("..")) continue
                        val relative = when {
                            path.contains("/png/128/") -> "png/128/" + path.substringAfter("/png/128/")
                            path.startsWith("png/128/") -> path
                            path.contains("/third_party/region-flags/png/") ->
                                "third_party/region-flags/png/" + path.substringAfter("/third_party/region-flags/png/")
                            path.startsWith("third_party/region-flags/png/") -> path
                            else -> continue
                        }
                        if (relative.contains("..") ||
                            !relative.endsWith(".png", ignoreCase = true) ||
                            !seen.add(relative) ||
                            entry.size > MAX_SINGLE_PNG
                        ) {
                            continue
                        }
                        require(seen.size <= MAX_IMAGES) { "图片数量超过安全限制" }
                        output.putNextEntry(ZipEntry(relative))
                        zip.getInputStream(entry).use { input ->
                            val buffer = ByteArray(32 * 1024)
                            var copied = 0L
                            while (true) {
                                val bytes = input.read(buffer)
                                if (bytes < 0) break
                                copied += bytes
                                require(copied <= MAX_SINGLE_PNG) { "ZIP 中单张图片超过 2 MiB" }
                                cumulative += bytes
                                require(cumulative <= MAX_TOTAL_PNG_BYTES) { "图片解压总量超过 512 MiB" }
                                output.write(buffer, 0, bytes)
                            }
                        }
                        output.closeEntry()
                    }
                }
            }
            validate(ready)
            require(!destination.exists() || destination.delete()) { "无法替换旧缓存" }
            require(ready.renameTo(destination)) { "无法保存裁剪后的 ZIP" }
        } finally {
            if (ready.exists()) ready.delete()
        }
    }

    fun cached(context: Context): Uri? {
        val file = archive(context)
        return if (file.isFile && runCatching { validate(file) }.isSuccess) contentUri(context, file) else null
    }

    fun cachedSummary(context: Context): String? {
        val file = archive(context)
        if (!file.isFile) return null
        val valid = runCatching { validate(file) }.isSuccess
        if (!valid) return null
        val mib = file.length() / (1024.0 * 1024.0)
        return String.format(Locale.ROOT, "已缓存（%.1f MiB）", mib)
    }

    private fun contentUri(context: Context, file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.archiveprovider",
        file,
    )
}
