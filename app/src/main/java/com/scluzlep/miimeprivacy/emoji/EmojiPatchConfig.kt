package com.scluzlep.miimeprivacy.emoji

import android.content.Intent

enum class NotoDownloadSource(
    val displayName: String,
) {
    GITHUB("GitHub 源站"),
    JSDELIVR("jsDelivr 镜像"),
}

data class UnicodeVersionOption(
    val unicodeFolder: String,
    val emojiVersion: String,
) {
    val displayName: String
        get() = "Emoji $emojiVersion"
}

data class EmojiPatchConfig(
    val unicodeFolder: String = DEFAULT_UNICODE_FOLDER,
    val emojiVersion: String = DEFAULT_EMOJI_VERSION,
    val includeNonFullyQualified: Boolean = false,
    val includeUnsupportedSystemEmoji: Boolean = false,
    val notoDownloadSource: NotoDownloadSource = NotoDownloadSource.GITHUB,
    val groupOrder: List<Int> = EmojiCatalog.orderGroups.map(EmojiGroup::packageId),
) {
    init {
        require(UNICODE_FOLDER_PATTERN.matches(unicodeFolder)) {
            "Invalid Unicode folder: $unicodeFolder"
        }
        require(EMOJI_VERSION_PATTERN.matches(emojiVersion)) {
            "Invalid Emoji version: $emojiVersion"
        }
        require(groupOrder.size == VALID_GROUP_IDS.size && groupOrder.toSet() == VALID_GROUP_IDS) {
            "Group order must contain every Unicode group exactly once"
        }
    }

    fun putInto(intent: Intent): Intent = intent
        .putExtra(EmojiPatchProtocol.EXTRA_UNICODE_FOLDER, unicodeFolder)
        .putExtra(EmojiPatchProtocol.EXTRA_EMOJI_VERSION, emojiVersion)
        .putExtra(
            EmojiPatchProtocol.EXTRA_INCLUDE_NON_FULLY_QUALIFIED,
            includeNonFullyQualified,
        )
        .putExtra(
            EmojiPatchProtocol.EXTRA_INCLUDE_UNSUPPORTED_SYSTEM_EMOJI,
            includeUnsupportedSystemEmoji,
        )
        .putExtra(EmojiPatchProtocol.EXTRA_NOTO_SOURCE, notoDownloadSource.name)
        .putExtra(EmojiPatchProtocol.EXTRA_GROUP_ORDER, groupOrder.toIntArray())

    fun orderValue(packageId: Int): Long {
        val index = groupOrder.indexOf(packageId)
        require(index >= 0) { "Unknown group $packageId" }
        return (groupOrder.size - index) * ORDER_STEP
    }

    companion object {
        const val DEFAULT_UNICODE_FOLDER = "17.0.0"
        const val DEFAULT_EMOJI_VERSION = "17.0"
        private const val ORDER_STEP = 1_000L
        private val UNICODE_FOLDER_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+$")
        private val EMOJI_VERSION_PATTERN = Regex("^\\d+\\.\\d+$")
        private val VALID_GROUP_IDS = EmojiCatalog.orderGroups
            .map(EmojiGroup::packageId)
            .toSet()

        fun fromIntent(intent: Intent): EmojiPatchConfig {
            val defaults = EmojiPatchConfig()
            val folder = intent.getStringExtra(EmojiPatchProtocol.EXTRA_UNICODE_FOLDER)
                ?.takeIf(UNICODE_FOLDER_PATTERN::matches)
                ?: defaults.unicodeFolder
            val version = intent.getStringExtra(EmojiPatchProtocol.EXTRA_EMOJI_VERSION)
                ?.takeIf(EMOJI_VERSION_PATTERN::matches)
                ?: defaults.emojiVersion
            val source = intent.getStringExtra(EmojiPatchProtocol.EXTRA_NOTO_SOURCE)
                ?.let { value ->
                    NotoDownloadSource.entries.firstOrNull { it.name == value }
                }
                ?: defaults.notoDownloadSource
            val order = intent.getIntArrayExtra(EmojiPatchProtocol.EXTRA_GROUP_ORDER)
                ?.toList()
                ?.takeIf { it.size == VALID_GROUP_IDS.size && it.toSet() == VALID_GROUP_IDS }
                ?: defaults.groupOrder

            return EmojiPatchConfig(
                unicodeFolder = folder,
                emojiVersion = version,
                includeNonFullyQualified = intent.getBooleanExtra(
                    EmojiPatchProtocol.EXTRA_INCLUDE_NON_FULLY_QUALIFIED,
                    defaults.includeNonFullyQualified,
                ),
                includeUnsupportedSystemEmoji = intent.getBooleanExtra(
                    EmojiPatchProtocol.EXTRA_INCLUDE_UNSUPPORTED_SYSTEM_EMOJI,
                    defaults.includeUnsupportedSystemEmoji,
                ),
                notoDownloadSource = source,
                groupOrder = order,
            )
        }
    }
}
