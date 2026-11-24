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
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.zip.ZipInputStream
import kotlin.math.min

data class InstalledSogouStatus(
    val installedVersion: String?,
    val installedCount: Int,
    val hasBackup: Boolean,
)

class SogouEmojiInstaller(
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
        )
        val entries = catalog.entries
        require(entries.isNotEmpty()) { "当前系统没有可安装的 Unicode Emoji" }

        onProgress(
            PatchUpdate(
                phase = PatchPhase.PREPARING,
                current = 0,
                total = catalog.imageCount,
                message = "系统支持 ${catalog.supportedSequenceCount}/${catalog.sourceSequenceCount} 项，" +
                    "合并 ${catalog.mergedSkinSets} 组肤色、${catalog.mergedDualSkinMatrices} 个双肤色…",
            ),
        )

        val layout = prepareStaging(entries)
        installImagesFromZip(entries, layout, archive, onProgress)
        writePackageMetadata(entries, layout)
        validateStaging(entries, layout)

        onProgress(
            PatchUpdate(
                phase = PatchPhase.INSTALLING,
                current = catalog.imageCount,
                total = catalog.imageCount,
                message = "正在替换搜狗 Emoji 分组…",
            ),
        )
        commit(layout, catalog)

        onProgress(
            PatchUpdate(
                phase = PatchPhase.COMPLETE,
                current = catalog.imageCount,
                total = catalog.imageCount,
                message = "搜狗已安装 Emoji ${configuration.emojiVersion}，共 ${entries.size} 项，即将重载",
                installedVersion = configuration.emojiVersion,
                installedCount = entries.size,
                hasBackup = layout.backupRoot.isDirectory,
            ),
        )
    }

    private fun installImagesFromZip(
        entries: List<EmojiEntry>,
        layout: InstallLayout,
        archive: ParcelFileDescriptor,
        onProgress: (PatchUpdate) -> Unit,
    ) {
        val needed = HashMap<String, MutableList<Pair<Int, String>>>()
        fun add(packageId: Int, fileName: String, source: String) {
            val path = when {
                source.contains("/third_party/region-flags/png/") ->
                    "third_party/region-flags/png/" +
                        source.substringAfter("/third_party/region-flags/png/")
                source.contains("/png/128/") ->
                    "png/128/" + source.substringAfter("/png/128/")
                else -> error("未知 Noto 图片路径: $source")
            }
            needed.getOrPut(path) { mutableListOf() }.add(packageId to fileName)
        }
        entries.forEach { item ->
            add(item.group.packageId, "${item.imageId}.png", item.sourceUrl)
            item.skinVariants.forEach { variant ->
                add(item.group.packageId, "${item.imageId}_${variant.suffix}.png", variant.sourceUrl)
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
                    targets.forEach { (packageId, fileName) ->
                        val output = File(
                            layout.packageDirectories.getValue(packageId),
                            "emoji/$fileName",
                        )
                        writeNormalizedPng(bytes, output)
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
            "ZIP 缺少 ${total - done} 张图片，请检查素材版本。示例：$missing"
        }
    }

    private fun readZipPng(zip: ZipInputStream): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            require(result.size() + count <= MAX_SINGLE_PNG_BYTES) {
                "ZIP 中图片超过 2 MiB，已拒绝读取"
            }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }

    private fun prepareStaging(entries: List<EmojiEntry>): InstallLayout {
        val sogouRoot = File(context.filesDir, "sogou")
        val packageRoot = File(sogouRoot, "emojipackage")
        require(packageRoot.isDirectory) {
            "搜狗 Emoji 目录不存在，请先在输入法中打开一次 Emoji 面板"
        }

        val stagingRoot = File(sogouRoot, ".unicode-staging")
        val backupRoot = File(sogouRoot, ".unicode-backup")
        deleteManagedDirectory(stagingRoot, sogouRoot)
        require(stagingRoot.mkdirs()) { "Unable to create staging directory" }

        val packageDirectories = EmojiCatalog.groups.associate { group ->
            val directoryName = "${group.packageId}_${configuration.orderValue(group.packageId)}"
            val directory = File(stagingRoot, directoryName)
            require(File(directory, "emoji").mkdirs()) {
                "Unable to create staging package ${group.packageId}"
            }
            group.packageId to directory
        }

        require(entries.all { it.group.packageId in packageDirectories }) {
            "Some Unicode groups do not have a target package directory"
        }
        return InstallLayout(packageRoot, stagingRoot, backupRoot, packageDirectories)
    }

    private fun writePackageMetadata(
        entries: List<EmojiEntry>,
        layout: InstallLayout,
    ) {
        EmojiCatalog.groups.forEach { group ->
            val groupEntries = entries.filter { it.group == group }
            require(groupEntries.isNotEmpty()) { "Empty group: ${group.unicodeName}" }
            val directory = layout.packageDirectories.getValue(group.packageId)

            File(directory, "info.xml").writeText(
                buildInfoXml(group, groupEntries),
                StandardCharsets.UTF_8,
            )
            File(directory, "version.json").writeText(
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" +
                    "<Version text=\"${xmlEscape(configuration.emojiVersion)}\" />",
                StandardCharsets.UTF_8,
            )

            val firstImage = File(directory, "emoji/${groupEntries.first().imageId}.png")
            firstImage.inputStream().use { input ->
                File(directory, "${group.type}.png").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun buildInfoXml(
        group: EmojiGroup,
        entries: List<EmojiEntry>,
    ): String = buildString(entries.size * 180) {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<expList version=\"1.0\">\n")
        append("  <exppkg type=\"")
        append(xmlEscape(group.type))
        append("\" name=\"")
        append(xmlEscape(group.type))
        append("\" chineseName=\"")
        append(xmlEscape(group.chineseName))
        append("\" id=\"")
        append(group.packageId)
        append("\">\n")

        entries.forEach { entry ->
            append("    <exp name=\"")
            append(entry.imageId)
            append("\" other=\"[")
            append(xmlEscape(entry.englishName))
            append("]\" pinyin=\"\" unified=\"")
            append(xmlEscape(entry.unified))
            append("\" softbank=\"\" supportSkin=\"")
            append(if (entry.supportsSkin) 1 else 0)
            append("\" FE0F=\"")
            append(if (entry.insertsSkinBeforeFe0f) 1 else 0)
            append("\" currentColor=\"0\" hasSelectedColor=\"")
            append(if (entry.startsWithSelectedSkin) 1 else 0)
            append("\"/>\n")
        }

        append("  </exppkg>\n")
        append("</expList>\n")
    }

    private fun validateStaging(
        entries: List<EmojiEntry>,
        layout: InstallLayout,
    ) {
        EmojiCatalog.groups.forEach { group ->
            val expected = entries
                .filter { it.group == group }
                .sumOf(EmojiEntry::imageCount)
            val directory = layout.packageDirectories.getValue(group.packageId)
            val images = File(directory, "emoji")
                .listFiles { file -> file.isFile && file.extension == "png" }
                ?.size
                ?: 0
            require(images == expected) {
                "Package ${group.packageId} expected $expected images, found $images"
            }
            require(File(directory, "info.xml").length() > 0L)
            require(File(directory, "version.json").length() > 0L)
            require(File(directory, "${group.type}.png").length() > 0L)
        }
    }

    private fun commit(
        layout: InstallLayout,
        catalog: PreparedEmojiCatalog,
    ) {
        val sogouRoot = layout.packageRoot.parentFile
            ?: error("Invalid Sogou package root")
        val marker = File(sogouRoot, MARKER_FILE_NAME)

        // Preserve original stock packages in .unicode-backup ONLY when .unicode-installed
        // does not exist yet so re-installing does not overwrite stock packages with patched ones.
        val preserveStockBackup = marker.exists()
        val oldTargetRoot = if (preserveStockBackup) {
            File(sogouRoot, ".unicode-previous").also { prev ->
                deleteManagedDirectory(prev, sogouRoot)
                require(prev.mkdirs()) { "Unable to create temporary previous directory" }
            }
        } else {
            deleteManagedDirectory(layout.backupRoot, sogouRoot)
            require(layout.backupRoot.mkdirs()) { "Unable to create backup directory" }
            layout.backupRoot
        }

        val oldDirectories = activePackageDirectories(layout.packageRoot)
        val movedOld = mutableListOf<Pair<File, File>>()
        val installed = mutableListOf<Pair<File, File>>()
        try {
            oldDirectories.forEach { source ->
                val dest = File(oldTargetRoot, source.name)
                require(source.renameTo(dest)) { "Unable to move existing ${source.name}" }
                movedOld += source to dest
            }

            layout.packageDirectories.values.forEach { staged ->
                val target = File(layout.packageRoot, staged.name)
                require(staged.renameTo(target)) { "Unable to install ${staged.name}" }
                installed += staged to target
            }
        } catch (error: Throwable) {
            installed.asReversed().forEach { (staged, target) ->
                if (target.exists()) target.renameTo(staged)
            }
            movedOld.asReversed().forEach { (original, dest) ->
                if (dest.exists()) dest.renameTo(original)
            }
            throw error
        }

        if (preserveStockBackup) {
            deleteManagedDirectory(oldTargetRoot, sogouRoot)
        }
        deleteManagedDirectory(layout.stagingRoot, sogouRoot)
        marker.writeText(
            "unicode=${configuration.emojiVersion}\n" +
                "unicodeFolder=${configuration.unicodeFolder}\n" +
                "sourceSequences=${catalog.sourceSequenceCount}\n" +
                "supportedSequences=${catalog.supportedSequenceCount}\n" +
                "skippedSequences=${catalog.skippedSequenceCount}\n" +
                "entries=${catalog.entries.size}\n" +
                "images=${catalog.imageCount}\n" +
                "skinSets=${catalog.mergedSkinSets}\n" +
                "dualSkinMatrices=${catalog.mergedDualSkinMatrices}\n" +
                "includeNonFullyQualified=${configuration.includeNonFullyQualified}\n" +
                "includeUnsupportedSystemEmoji=${configuration.includeUnsupportedSystemEmoji}\n" +
                "notoSource=${configuration.notoDownloadSource.name}\n" +
                "groupOrder=${configuration.groupOrder.joinToString(",")}\n" +
                "installedAt=${System.currentTimeMillis()}\n",
            StandardCharsets.UTF_8,
        )
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

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private data class InstallLayout(
        val packageRoot: File,
        val stagingRoot: File,
        val backupRoot: File,
        val packageDirectories: Map<Int, File>,
    )

    companion object {
        private const val MARKER_FILE_NAME = ".unicode-installed"
        private const val BACKUP_DIR_NAME = ".unicode-backup"
        private const val IMAGE_SIZE = 64
        private const val PROGRESS_STEP = 12
        private const val MAX_SINGLE_PNG_BYTES = 2 * 1024 * 1024
        private val PACKAGE_DIRECTORY = Regex("^(\\d+)_(-?\\d+)$")
        private val REPLACED_PACKAGE_IDS = 3..23

        fun readInstalledStatus(context: Context): InstalledSogouStatus {
            val sogouRoot = File(context.filesDir, "sogou")
            val marker = File(sogouRoot, MARKER_FILE_NAME)
            val backup = File(sogouRoot, BACKUP_DIR_NAME)
            val props = Properties()
            if (marker.isFile) {
                runCatching {
                    marker.reader(StandardCharsets.UTF_8).use(props::load)
                }
            }
            val version = props.getProperty("unicode")?.takeIf { it.isNotBlank() }
            val count = props.getProperty("entries")?.toIntOrNull()
                ?: InstalledEmojiGroupCounts.read(context).filter { it > 0 }.sum()
            return InstalledSogouStatus(
                installedVersion = version,
                installedCount = count,
                hasBackup = marker.isFile || backup.isDirectory,
            )
        }

        fun restore(context: Context) {
            val sogouRoot = File(context.filesDir, "sogou")
            val packageRoot = File(sogouRoot, "emojipackage")
            val backupRoot = File(sogouRoot, BACKUP_DIR_NAME)
            val marker = File(sogouRoot, MARKER_FILE_NAME)
            require(packageRoot.isDirectory) { "搜狗 Emoji 目录不存在" }
            require(marker.isFile || backupRoot.isDirectory) {
                "尚未安装 Unicode 补全或不存在原版备份"
            }

            val removingRoot = File(sogouRoot, ".unicode-removing")
            deleteManagedDirectory(removingRoot, sogouRoot)
            require(removingRoot.mkdirs()) { "Unable to prepare restore directory" }

            val currentPatched = activePackageDirectories(packageRoot)
            val backupDirs = if (backupRoot.isDirectory) {
                backupRoot.listFiles()
                    ?.filter { it.isDirectory && PACKAGE_DIRECTORY.matches(it.name) }
                    .orEmpty()
            } else {
                emptyList()
            }

            val movedOut = mutableListOf<Pair<File, File>>()
            val restoredIn = mutableListOf<Pair<File, File>>()
            try {
                currentPatched.forEach { source ->
                    val dest = File(removingRoot, source.name)
                    require(source.renameTo(dest)) { "无法移除补全分组 ${source.name}" }
                    movedOut += source to dest
                }
                backupDirs.forEach { backup ->
                    val dest = File(packageRoot, backup.name)
                    require(backup.renameTo(dest)) { "无法恢复备份分组 ${backup.name}" }
                    restoredIn += backup to dest
                }
            } catch (error: Throwable) {
                restoredIn.asReversed().forEach { (backup, dest) ->
                    if (dest.exists()) dest.renameTo(backup)
                }
                movedOut.asReversed().forEach { (source, dest) ->
                    if (dest.exists()) dest.renameTo(source)
                }
                throw error
            }

            deleteManagedDirectory(removingRoot, sogouRoot)
            if (backupRoot.exists()) {
                deleteManagedDirectory(backupRoot, sogouRoot)
            }
            if (marker.exists()) {
                require(marker.delete()) { "Unable to remove install marker" }
            }
        }

        private fun activePackageDirectories(packageRoot: File): List<File> =
            packageRoot.listFiles()
                ?.filter { file ->
                    file.isDirectory && packageId(file) in REPLACED_PACKAGE_IDS
                }
                .orEmpty()

        private fun packageId(directory: File): Int =
            PACKAGE_DIRECTORY.matchEntire(directory.name)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: -1

        private fun deleteManagedDirectory(directory: File, parent: File) {
            val parentPath = parent.canonicalFile.path + File.separator
            require(directory.canonicalFile.path.startsWith(parentPath)) {
                "Refusing to delete unmanaged path: $directory"
            }
            if (directory.exists()) {
                require(directory.deleteRecursively()) { "Unable to clear $directory" }
            }
        }
    }
}
