package com.scluzlep.miimeprivacy.emoji

import android.content.Context
import java.io.File

class SogouEmojiOrderInstaller(
    private val context: Context,
    private val configuration: EmojiPatchConfig,
) {
    fun apply() {
        val packageRoot = File(context.filesDir, "sogou/emojipackage")
        require(packageRoot.isDirectory) {
            "搜狗 Emoji 目录不存在，请先安装一次 Emoji"
        }
        val active = packageRoot.listFiles()
            ?.filter { directory ->
                directory.isDirectory && PACKAGE_DIRECTORY.matches(directory.name)
            }
            ?.mapNotNull { directory ->
                val id = PACKAGE_DIRECTORY.matchEntire(directory.name)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
                if (id != null && id in configuration.groupOrder) id to directory else null
            }
            .orEmpty()
        val activeIds = active.map(Pair<Int, File>::first).toSet()
        val installableIds = EmojiCatalog.groups
            .map(EmojiGroup::packageId)
            .toSet()
        require(installableIds.all { it in activeIds }) {
            "活动 Unicode 分组不完整，请先执行一次安装"
        }
        require(activeIds.size == active.size) {
            "检测到重复的活动 Unicode 分组，请先重新安装"
        }

        val moves = active.map { (id, original) ->
            OrderMove(
                original = original,
                staged = File(packageRoot, ".order-$id-${System.nanoTime()}"),
                target = File(packageRoot, "${id}_${configuration.orderValue(id)}"),
            )
        }
        try {
            moves.forEach { move ->
                require(move.original.renameTo(move.staged)) {
                    "无法暂存分组 ${move.original.name}"
                }
            }
            moves.forEach { move ->
                require(move.staged.renameTo(move.target)) {
                    "无法应用分组 ${move.target.name} 的排序"
                }
            }
        } catch (error: Throwable) {
            moves.asReversed().forEach { move ->
                when {
                    move.target.exists() -> move.target.renameTo(move.original)
                    move.staged.exists() -> move.staged.renameTo(move.original)
                }
            }
            throw error
        }
    }

    private data class OrderMove(
        val original: File,
        val staged: File,
        val target: File,
    )

    companion object {
        private val PACKAGE_DIRECTORY = Regex("^(\\d+)_(-?\\d+)$")

        fun readCurrentOrder(context: Context): IntArray? {
            val packageRoot = File(context.filesDir, "sogou/emojipackage")
            if (!packageRoot.isDirectory) return null
            val orderableIds = EmojiCatalog.orderGroups.map { it.packageId }.toSet()
            val ids = packageRoot.listFiles()
                ?.asSequence()
                ?.filter(File::isDirectory)
                ?.mapNotNull { directory ->
                    val match = PACKAGE_DIRECTORY.matchEntire(directory.name) ?: return@mapNotNull null
                    val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val order = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                    if (id !in orderableIds) return@mapNotNull null
                    id to order
                }
                ?.sortedByDescending { it.second }
                ?.map { it.first }
                ?.toList()
                .orEmpty()
            if (ids.size != orderableIds.size) return null
            return ids.toIntArray()
        }
    }
}
