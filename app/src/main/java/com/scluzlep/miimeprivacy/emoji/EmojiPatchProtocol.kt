package com.scluzlep.miimeprivacy.emoji

import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.model.ImeTarget

object EmojiPatchProtocol {
    const val MODULE_PACKAGE = ConfigContract.MODULE_PACKAGE
    val TARGET_SOGOU = ImeTarget.SOGOU.packageName
    val TARGET_BAIDU = ImeTarget.BAIDU.packageName

    const val ACTION_COMMAND = "$MODULE_PACKAGE.action.EMOJI_PATCH_COMMAND"
    const val ACTION_STATUS = "$MODULE_PACKAGE.action.EMOJI_PATCH_STATUS"
    const val ARCHIVE_PROVIDER_AUTHORITY = "$MODULE_PACKAGE.archiveprovider"
    const val ACTION_CALLER_PROOF_INTERNAL = "$MODULE_PACKAGE.internal.CALLER_PROOF"
    const val ACTION_STATUS_PROOF_INTERNAL = "$MODULE_PACKAGE.internal.STATUS_PROOF"
    private const val API_UPSIDE_DOWN_CAKE = 34

    const val EXTRA_TARGET_PACKAGE = "target_package"
    const val EXTRA_CALLER_PROOF = "caller_proof"
    const val EXTRA_ARCHIVE_URI = "noto_archive_uri"
    const val EXTRA_EMOJI_TEST_URI = "emoji_test_uri"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_SESSION_ID = "session_id"
    const val EXTRA_PHASE = "phase"
    const val EXTRA_CURRENT = "current"
    const val EXTRA_TOTAL = "total"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_UNICODE_FOLDER = "unicode_folder"
    const val EXTRA_EMOJI_VERSION = "emoji_version"
    const val EXTRA_INCLUDE_NON_FULLY_QUALIFIED = "include_non_fully_qualified"
    const val EXTRA_INCLUDE_UNSUPPORTED_SYSTEM_EMOJI = "include_unsupported_system_emoji"
    const val EXTRA_NOTO_SOURCE = "noto_source"
    const val EXTRA_GROUP_ORDER = "group_order"
    const val EXTRA_GROUP_ENTRY_COUNTS = "group_entry_counts"
    const val EXTRA_INSTALLED_VERSION = "installed_version"
    const val EXTRA_INSTALLED_COUNT = "installed_count"
    const val EXTRA_HAS_BACKUP = "has_backup"

    const val COMMAND_PING = "ping"
    const val COMMAND_INSTALL = "install"
    const val COMMAND_APPLY_ORDER = "apply_order"
    const val COMMAND_RESTORE = "restore"

    fun isTrustedArchiveUri(scheme: String?, authority: String?): Boolean =
        scheme == "content" && authority == ARCHIVE_PROVIDER_AUTHORITY

    fun isTrustedModuleCaller(
        apiLevel: Int,
        sentFromPackage: String?,
        proofCreatorPackage: String?,
    ): Boolean {
        if (proofCreatorPackage != MODULE_PACKAGE) return false
        if (apiLevel >= API_UPSIDE_DOWN_CAKE && sentFromPackage != MODULE_PACKAGE) return false
        return true
    }

    fun isTrustedTargetSender(
        apiLevel: Int,
        sentFromPackage: String?,
        declaredTargetPackage: String?,
        proofCreatorPackage: String?,
    ): Boolean {
        if (declaredTargetPackage != TARGET_SOGOU && declaredTargetPackage != TARGET_BAIDU) return false
        if (proofCreatorPackage != declaredTargetPackage) return false
        if (apiLevel >= API_UPSIDE_DOWN_CAKE && sentFromPackage != declaredTargetPackage) return false
        return true
    }
}

enum class PatchPhase {
    IDLE,
    WAITING_FOR_HOOK,
    READY,
    FETCHING_MANIFEST,
    PREPARING,
    DOWNLOADING,
    INSTALLING,
    ORDERING,
    RESTORING,
    COMPLETE,
    ERROR,
}

data class PatchUpdate(
    val phase: PatchPhase,
    val current: Int,
    val total: Int,
    val message: String,
    val installedVersion: String? = null,
    val installedCount: Int = 0,
    val hasBackup: Boolean = false,
)

data class EmojiTargetUiState(
    val targetPackage: String,
    val targetInstalled: Boolean = false,
    val hookReady: Boolean = false,
    val phase: PatchPhase = PatchPhase.IDLE,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = "正在检查状态…",
    val installedVersion: String? = null,
    val installedCount: Int = 0,
    val hasBackup: Boolean = false,
    val groupEntryCounts: Map<Int, Int> = emptyMap(),
    val currentOrder: List<Int>? = null,
) {
    val isRunning: Boolean
        get() = phase in setOf(
            PatchPhase.FETCHING_MANIFEST,
            PatchPhase.PREPARING,
            PatchPhase.DOWNLOADING,
            PatchPhase.INSTALLING,
            PatchPhase.ORDERING,
            PatchPhase.RESTORING,
        )

    val canAct: Boolean
        get() = targetInstalled && hookReady && !isRunning

    val progress: Float
        get() = if (total > 0) {
            (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
