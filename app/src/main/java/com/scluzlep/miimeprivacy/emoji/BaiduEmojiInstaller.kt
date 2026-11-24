package com.scluzlep.miimeprivacy.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.zip.ZipInputStream
import kotlin.math.min

data class InstalledBaiduStatus(
    val installedVersion: String?,
    val activeLocalVersion: String?,
    val installedCount: Int,
    val hasBackup: Boolean,
)

class BaiduEmojiInstaller(
    private val context: Context,
    private val configuration: EmojiPatchConfig,
) {
    fun install(
        archive: ParcelFileDescriptor,
        emojiTestFd: ParcelFileDescriptor,
        onProgress: (PatchUpdate) -> Unit,
    ) {
        onProgress(
            PatchUpdate(
                phase = PatchPhase.FETCHING_MANIFEST,
                current = 0,
                total = 0,
                message = "正在读取 Unicode Emoji ${configuration.emojiVersion} 清单…",
            ),
        )
        val emojiTest = FileInputStream(emojiTestFd.fileDescriptor)
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
        val manifestVersion = EmojiCatalog.emojiVersion(emojiTest)
        require(manifestVersion == configuration.emojiVersion) {
            "清单版本为 Emoji $manifestVersion，预期 ${configuration.emojiVersion}"
        }

        val sourceEntries = EmojiCatalog.parseEmojiTest(
            text = emojiTest,
            includeNonFullyQualified = configuration.includeNonFullyQualified,
            notoDownloadSource = configuration.notoDownloadSource,
        )
        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = IMAGE_SIZE.toFloat()
        }
        val catalog = EmojiCatalog.prepareForSystem(
            sourceEntries = sourceEntries,
            hasGlyph = if (configuration.includeUnsupportedSystemEmoji) {
                { true }
            } else {
                glyphPaint::hasGlyph
            },
            mergeDualSkinMatrices = false,
            canMergeSkinSet = BaiduEmojiFormat::isSkinSetCompatibleWithAoBY,
        )
        val entries = catalog.entries
        require(entries.isNotEmpty()) { "当前系统没有可安装的 Unicode Emoji" }

        val stockData = readStockBaiduData(context)
        val generated = BaiduEmojiFormat.buildBaiduCatalogOutput(entries, stockData)
        val idByUnified = HashMap<String, Int>(entries.size)
        entries.forEachIndexed { idx, entry ->
            idByUnified[entry.unified] = generated.assignedIds[idx]
        }

        onProgress(
            PatchUpdate(
                phase = PatchPhase.PREPARING,
                current = 0,
                total = catalog.imageCount,
                message = "系统支持 ${catalog.supportedSequenceCount} 项，" +
                    "生成 ${entries.size} 个百度条目（兼容肤色 ${generated.skinEnabledCount} 组）…",
            ),
        )

        val targetDir = resolveTargetDirectory(context, configuration.emojiVersion)
        val emojiRoot = targetDir.parentFile
            ?: error("无法定位百度 Emoji 根目录: $targetDir")
        require(emojiRoot.isDirectory || emojiRoot.mkdirs()) {
            "无法创建百度 Emoji 根目录"
        }

        val stagingDir = File(emojiRoot, ".miime-staging-${System.nanoTime()}")
        val stagingPngDir = File(stagingDir, "emojipngs")
        require(stagingPngDir.mkdirs()) { "无法创建百度 Emoji 暂存目录" }

        try {
            extractAndNormalizeImages(
                entries = entries,
                idByUnified = idByUnified,
                archive = archive,
                pngDir = stagingPngDir,
                onProgress = onProgress,
            )

            File(stagingDir, "emoji").writeBytes(generated.emojiBytes)
            File(stagingDir, "emojigroup").writeText(generated.emojiGroupText, StandardCharsets.UTF_8)
            File(stagingDir, "emoji_change_skin").writeText(
                generated.changeSkinText,
                StandardCharsets.UTF_8,
            )
            File(stagingDir, "emoji_error_hint").writeText(
                generated.errorHintText,
                StandardCharsets.UTF_8,
            )

            validateStagedFiles(entries, idByUnified, stagingDir, stagingPngDir)

            onProgress(
                PatchUpdate(
                    phase = PatchPhase.INSTALLING,
                    current = catalog.imageCount,
                    total = catalog.imageCount,
                    message = "正在写入百度 Emoji ${configuration.emojiVersion} 目录并更新配置…",
                ),
            )

            val previousStatus = readMarker(context)
            swapIntoTarget(emojiRoot, stagingDir, targetDir)

            val prevInstalledPath = previousStatus?.getProperty(KEY_INSTALLED_DIR)
            if (!prevInstalledPath.isNullOrBlank()) {
                val prevDir = File(prevInstalledPath)
                if (prevDir.canonicalPath != targetDir.canonicalPath &&
                    isSafeChildOf(prevDir, emojiRoot)
                ) {
                    prevDir.deleteRecursively()
                }
            }

            writeLocalEmojiVersion(context, configuration.emojiVersion)
            writeMarker(
                context = context,
                targetDir = targetDir,
                emojiVersion = configuration.emojiVersion,
                unicodeFolder = configuration.unicodeFolder,
                entryCount = entries.size,
                imageCount = catalog.imageCount,
                skinCount = generated.skinEnabledCount,
            )
        } finally {
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
        }

        onProgress(
            PatchUpdate(
                phase = PatchPhase.COMPLETE,
                current = catalog.imageCount,
                total = catalog.imageCount,
                message = "百度已安装 Emoji ${configuration.emojiVersion}（${entries.size} 项），即将重载",
                installedVersion = configuration.emojiVersion,
                installedCount = entries.size,
                hasBackup = true,
            ),
        )
    }

    private fun extractAndNormalizeImages(
        entries: List<EmojiEntry>,
        idByUnified: Map<String, Int>,
        archive: ParcelFileDescriptor,
        pngDir: File,
        onProgress: (PatchUpdate) -> Unit,
    ) {
        val needed = HashMap<String, MutableList<String>>()
        fun add(fileName: String, sourceUrl: String) {
            val path = when {
                sourceUrl.contains("/third_party/region-flags/png/") ->
                    "third_party/region-flags/png/" +
                        sourceUrl.substringAfter("/third_party/region-flags/png/")
                sourceUrl.contains("/png/128/") ->
                    "png/128/" + sourceUrl.substringAfter("/png/128/")
                else -> error("未知 Noto 图片路径: $sourceUrl")
            }
            needed.getOrPut(path) { mutableListOf() }.add(fileName)
        }
        entries.forEach { entry ->
            val id = idByUnified.getValue(entry.unified)
            add("$id.png", entry.sourceUrl)
            entry.skinVariants.forEach { variant ->
                add("${id}_${variant.suffix}.png", variant.sourceUrl)
            }
        }

        val total = needed.values.sumOf { it.size }
        var done = 0
        ZipInputStream(FileInputStream(archive.fileDescriptor).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name.replace('\\', '/')
                val key = when {
                    name.startsWith("png/128/") -> name
                    name.contains("/png/128/") -> "png/128/" + name.substringAfter("/png/128/")
                    name.startsWith("third_party/region-flags/png/") -> name
                    name.contains("/third_party/region-flags/png/") ->
                        "third_party/region-flags/png/" +
                            name.substringAfter("/third_party/region-flags/png/")
                    else -> null
                }
                val targets = if (key == null) null else needed.remove(key)
                if (targets != null) {
                    val bytes = readZipPng(zip)
                    targets.forEach { fileName ->
                        writeNormalizedPng(bytes, File(pngDir, fileName))
                        done++
                    }
                    if (done % PROGRESS_STEP == 0 || done == total) {
                        onProgress(
                            PatchUpdate(
                                phase = PatchPhase.DOWNLOADING,
                                current = done,
                                total = total,
                                message = "正在写入 Noto 图像：$done / $total",
                            ),
                        )
                    }
                }
                zip.closeEntry()
            }
        }
        require(done == total) {
            val missing = needed.keys.take(5).joinToString()
            "ZIP 缺少 ${total - done} 张图片，示例：$missing"
        }
    }

    private fun readZipPng(zip: ZipInputStream): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            require(result.size() + count <= MAX_SINGLE_PNG_BYTES) {
                "ZIP 中图片超过 2 MiB"
            }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }

    private fun writeNormalizedPng(bytes: ByteArray, target: File) {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("无效 PNG 图片: ${target.name}")
        val output = createBitmap(IMAGE_SIZE, IMAGE_SIZE)
        val scale = min(
            IMAGE_SIZE.toFloat() / source.width.toFloat(),
            IMAGE_SIZE.toFloat() / source.height.toFloat(),
        )
        val width = source.width * scale
        val height = source.height * scale
        val left = (IMAGE_SIZE - width) / 2f
        val top = (IMAGE_SIZE - height) / 2f
        Canvas(output).drawBitmap(
            source,
            null,
            RectF(left, top, left + width, top + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )

        FileOutputStream(target).use { stream ->
            require(output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode ${target.name}"
            }
            stream.flush()
            stream.fd.sync()
        }
        source.recycle()
        output.recycle()
    }

    private fun validateStagedFiles(
        entries: List<EmojiEntry>,
        idByUnified: Map<String, Int>,
        stagingDir: File,
        pngDir: File,
    ) {
        require(File(stagingDir, "emoji").length() > 2L)
        require(File(stagingDir, "emojigroup").length() > 0L)
        require(File(stagingDir, "emoji_error_hint").isFile)
        val expectedPngs = entries.sumOf { it.imageCount }
        val actualPngs = pngDir.listFiles { f -> f.isFile && f.extension == "png" }?.size ?: 0
        require(actualPngs == expectedPngs) {
            "百度 Emoji 图片数量校验失败: 预期 $expectedPngs，实际 $actualPngs"
        }
        val firstId = idByUnified.getValue(entries.first().unified)
        require(File(pngDir, "$firstId.png").isFile)
    }

    private fun swapIntoTarget(emojiRoot: File, stagingDir: File, targetDir: File) {
        val backupTmp = File(emojiRoot, ".miime-previous-${System.nanoTime()}")
        var movedExisting = false
        try {
            if (targetDir.exists()) {
                require(targetDir.renameTo(backupTmp)) { "无法暂存旧百度 Emoji 目录" }
                movedExisting = true
            }
            require(stagingDir.renameTo(targetDir)) { "无法提交新百度 Emoji 目录" }
            if (movedExisting && backupTmp.exists()) {
                backupTmp.deleteRecursively()
            }
        } catch (error: Throwable) {
            if (!targetDir.exists() && movedExisting && backupTmp.exists()) {
                backupTmp.renameTo(targetDir)
            }
            throw error
        }
    }

    companion object {
        private const val STOCK_ASSET_EMOJI = "newEmoji/emoji"
        private const val PREF_KEY_LOCAL_EMOJI_VER = "pref_key_local_emoji_ver"
        private const val STOCK_VERSION = "1.0"
        private const val MARKER_RELATIVE_PATH = "emoji/.miime-installed"
        private const val KEY_INSTALLED_DIR = "installedDir"
        private const val KEY_EMOJI_VERSION = "emojiVersion"
        private const val KEY_UNICODE_FOLDER = "unicodeFolder"
        private const val KEY_ENTRY_COUNT = "entryCount"
        private const val KEY_IMAGE_COUNT = "imageCount"
        private const val KEY_SKIN_COUNT = "skinCount"
        private const val IMAGE_SIZE = 64
        private const val PROGRESS_STEP = 12
        private const val MAX_SINGLE_PNG_BYTES = 2 * 1024 * 1024

        fun resolveTargetDirectory(context: Context, emojiVersion: String): File {
            val reflectedPath = runCatching {
                val gpF = Class.forName("gp.f", false, context.classLoader)
                val methodH = gpF.getDeclaredMethod("h", String::class.java).apply {
                    isAccessible = true
                }
                methodH.invoke(null, emojiVersion) as? String
            }.getOrNull()
            if (!reflectedPath.isNullOrBlank()) {
                return File(reflectedPath)
            }
            return File(File(context.filesDir, "emoji"), emojiVersion)
        }

        private fun readStockBaiduData(context: Context): BaiduEmojiFormat.StockBaiduData =
            runCatching {
                context.assets.open(STOCK_ASSET_EMOJI).use { input ->
                    BaiduEmojiFormat.parseStockBaiduAssets(input.readBytes())
                }
            }.getOrDefault(BaiduEmojiFormat.StockBaiduData())

        fun readInstalledStatus(context: Context): InstalledBaiduStatus {
            val marker = readMarker(context)
            val markerVer = marker?.getProperty(KEY_EMOJI_VERSION)?.takeIf { it.isNotBlank() }
            val markerCount = marker?.getProperty(KEY_ENTRY_COUNT)?.toIntOrNull() ?: 0
            val installedDir = marker?.getProperty(KEY_INSTALLED_DIR)?.let(::File)
            val dirExists = installedDir?.isDirectory == true
            val prefVer = readLocalEmojiVersion(context)
            val isCustomActive = (marker != null && dirExists) || isVersionCustom(prefVer)
            val activeVersion = when {
                isVersionCustom(prefVer) -> prefVer
                marker != null && dirExists -> markerVer
                else -> null
            }
            return InstalledBaiduStatus(
                installedVersion = activeVersion,
                activeLocalVersion = prefVer,
                installedCount = if (dirExists) markerCount else 0,
                hasBackup = isCustomActive,
            )
        }

        fun restore(context: Context) {
            val marker = readMarker(context)
            val installedDirPath = marker?.getProperty(KEY_INSTALLED_DIR)
            val activePrefVer = readLocalEmojiVersion(context)

            writeLocalEmojiVersion(context, STOCK_VERSION)

            if (!installedDirPath.isNullOrBlank()) {
                val customDir = File(installedDirPath)
                if (customDir.exists() && isSafeChildOf(customDir, context.filesDir)) {
                    require(customDir.deleteRecursively()) { "无法删除百度自定义 Emoji 目录" }
                }
            }
            if (!activePrefVer.isNullOrBlank() && isVersionCustom(activePrefVer)) {
                val resolvedByPref = resolveTargetDirectory(context, activePrefVer)
                if (resolvedByPref.exists() && isSafeChildOf(resolvedByPref, context.filesDir)) {
                    resolvedByPref.deleteRecursively()
                }
            }

            val markerFile = markerFile(context)
            if (markerFile.exists()) {
                markerFile.delete()
            }
        }

        private fun isVersionCustom(version: String?): Boolean {
            if (version.isNullOrBlank()) return false
            val major = version.substringBefore('.').toIntOrNull() ?: return false
            return major >= 6
        }

        private fun readLocalEmojiVersion(context: Context): String? = runCatching {
            val uoA = Class.forName("uo.a", false, context.classLoader)
            val methodG1 = uoA.getDeclaredMethod("G1").apply { isAccessible = true }
            val store = methodG1.invoke(null) ?: return@runCatching null
            val storeInterface = runCatching {
                Class.forName("en.d", false, context.classLoader)
            }.getOrNull() ?: store.javaClass
            val getString = findMethodInHierarchy(
                storeInterface,
                store.javaClass,
                "getString",
                String::class.java,
                String::class.java,
            ) ?: return@runCatching null
            getString.invoke(store, PREF_KEY_LOCAL_EMOJI_VER, STOCK_VERSION) as? String
        }.getOrNull()

        private fun writeLocalEmojiVersion(context: Context, version: String) {
            val uoA = Class.forName("uo.a", false, context.classLoader)
            val methodG1 = uoA.getDeclaredMethod("G1").apply { isAccessible = true }
            val store = requireNotNull(methodG1.invoke(null)) { "无法获取百度 uo.a.G1() 配置对象" }
            val storeInterface = runCatching {
                Class.forName("en.d", false, context.classLoader)
            }.getOrNull() ?: store.javaClass
            val putString = findMethodInHierarchy(
                storeInterface,
                store.javaClass,
                "putString",
                String::class.java,
                String::class.java,
            ) ?: error("未找到百度 en.d#putString 方法")
            val editor = putString.invoke(store, PREF_KEY_LOCAL_EMOJI_VER, version) ?: store
            val apply = findMethodInHierarchy(
                storeInterface,
                editor.javaClass,
                "apply",
            ) ?: error("未找到百度 en.d#apply 方法")
            apply.invoke(editor)
        }

        private fun findMethodInHierarchy(
            preferredInterface: Class<*>,
            concreteClass: Class<*>,
            name: String,
            vararg params: Class<*>,
        ): Method? {
            runCatching { preferredInterface.getDeclaredMethod(name, *params) }.getOrNull()?.let {
                it.isAccessible = true
                return it
            }
            var cursor: Class<*>? = concreteClass
            while (cursor != null && cursor != Any::class.java) {
                runCatching { cursor.getDeclaredMethod(name, *params) }.getOrNull()?.let {
                    it.isAccessible = true
                    return it
                }
                cursor = cursor.superclass
            }
            return null
        }

        private fun markerFile(context: Context): File = File(context.filesDir, MARKER_RELATIVE_PATH)

        private fun readMarker(context: Context): Properties? {
            val file = markerFile(context)
            if (!file.isFile) return null
            val props = Properties()
            return runCatching {
                file.reader(StandardCharsets.UTF_8).use(props::load)
                props
            }.getOrNull()
        }

        private fun writeMarker(
            context: Context,
            targetDir: File,
            emojiVersion: String,
            unicodeFolder: String,
            entryCount: Int,
            imageCount: Int,
            skinCount: Int,
        ) {
            val file = markerFile(context)
            file.parentFile?.mkdirs()
            file.writeText(
                "$KEY_EMOJI_VERSION=$emojiVersion\n" +
                    "$KEY_UNICODE_FOLDER=$unicodeFolder\n" +
                    "$KEY_INSTALLED_DIR=${targetDir.absolutePath}\n" +
                    "$KEY_ENTRY_COUNT=$entryCount\n" +
                    "$KEY_IMAGE_COUNT=$imageCount\n" +
                    "$KEY_SKIN_COUNT=$skinCount\n" +
                    "installedAt=${System.currentTimeMillis()}\n",
                StandardCharsets.UTF_8,
            )
        }

        private fun isSafeChildOf(candidate: File, parent: File): Boolean {
            val parentPath = parent.canonicalFile.path + File.separator
            return candidate.canonicalFile.path.startsWith(parentPath)
        }
    }
}
