package com.scluzlep.miimeprivacy.emoji

import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Coordinates Noto archive & emoji-test caching, per-target read-only FileProvider grants,
 * and cross-process install/restore/order status across Sogou and Baidu Xiaomi IMEs.
 */
class EmojiPatchController private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val versionRepository = UnicodeVersionRepository()

    private val mutableConfig = MutableStateFlow(loadStoredConfig())
    val config: StateFlow<EmojiPatchConfig> = mutableConfig.asStateFlow()

    private val mutableVersions = MutableStateFlow(versionRepository.loadCachedVersions(appContext))
    val versions: StateFlow<List<UnicodeVersionOption>> = mutableVersions.asStateFlow()

    private val mutableRefreshingVersions = MutableStateFlow(false)
    val refreshingVersions: StateFlow<Boolean> = mutableRefreshingVersions.asStateFlow()

    private val mutableArchiveSummary = MutableStateFlow(NotoArchiveCache.cachedSummary(appContext))
    val archiveSummary: StateFlow<String?> = mutableArchiveSummary.asStateFlow()

    private val mutableEmojiTestCached = MutableStateFlow(
        versionRepository.isEmojiTestCached(
            appContext,
            UnicodeVersionOption(
                mutableConfig.value.unicodeFolder,
                mutableConfig.value.emojiVersion,
            ),
        ),
    )
    val emojiTestCached: StateFlow<Boolean> = mutableEmojiTestCached.asStateFlow()

    private val mutableArchiveBusy = MutableStateFlow(false)
    val archiveBusy: StateFlow<Boolean> = mutableArchiveBusy.asStateFlow()

    private val mutableArchiveMessage = MutableStateFlow("")
    val archiveMessage: StateFlow<String> = mutableArchiveMessage.asStateFlow()

    private val mutableSogouState = MutableStateFlow(
        EmojiTargetUiState(targetPackage = EmojiPatchProtocol.TARGET_SOGOU),
    )
    val sogouState: StateFlow<EmojiTargetUiState> = mutableSogouState.asStateFlow()

    private val mutableBaiduState = MutableStateFlow(
        EmojiTargetUiState(targetPackage = EmojiPatchProtocol.TARGET_BAIDU),
    )
    val baiduState: StateFlow<EmojiTargetUiState> = mutableBaiduState.asStateFlow()

    private val sessionsByTarget = hashMapOf(
        EmojiPatchProtocol.TARGET_SOGOU to UUID.randomUUID().toString(),
        EmojiPatchProtocol.TARGET_BAIDU to UUID.randomUUID().toString(),
    )
    private val grantedUrisByTarget = HashMap<String, List<Uri>>()

    private var registered = false
    private var uiSubscribers = 0
    private var pingJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != EmojiPatchProtocol.ACTION_STATUS) return
            val targetPkg = intent.getStringExtra(EmojiPatchProtocol.EXTRA_TARGET_PACKAGE) ?: return
            val expectedSession = sessionsByTarget[targetPkg] ?: return
            if (intent.getStringExtra(EmojiPatchProtocol.EXTRA_SESSION_ID) != expectedSession) return

            val phase = intent.getStringExtra(EmojiPatchProtocol.EXTRA_PHASE)
                ?.let { runCatching { PatchPhase.valueOf(it) }.getOrNull() }
                ?: return
            val current = intent.getIntExtra(EmojiPatchProtocol.EXTRA_CURRENT, 0)
            val total = intent.getIntExtra(EmojiPatchProtocol.EXTRA_TOTAL, 0)
            val message = intent.getStringExtra(EmojiPatchProtocol.EXTRA_MESSAGE).orEmpty()
            val installedVer = intent.getStringExtra(EmojiPatchProtocol.EXTRA_INSTALLED_VERSION)
            val installedCount = intent.getIntExtra(EmojiPatchProtocol.EXTRA_INSTALLED_COUNT, 0)
            val hasBackup = intent.getBooleanExtra(EmojiPatchProtocol.EXTRA_HAS_BACKUP, false)

            val flow = stateFlowFor(targetPkg) ?: return
            val prev = flow.value
            val groupCounts = intent
                .getIntArrayExtra(EmojiPatchProtocol.EXTRA_GROUP_ENTRY_COUNTS)
                ?.takeIf { it.size == EmojiCatalog.groups.size }
                ?.let { counts ->
                    EmojiCatalog.groups.mapIndexed { idx, group ->
                        group.packageId to counts[idx]
                    }.toMap()
                }
                ?: prev.groupEntryCounts
            val currentOrder = intent
                .getIntArrayExtra(EmojiPatchProtocol.EXTRA_GROUP_ORDER)
                ?.toList()
                ?.takeIf { it.size == ORDERABLE_IDS.size && it.toSet() == ORDERABLE_IDS }
                ?: prev.currentOrder

            val nextState = prev.copy(
                targetInstalled = true,
                hookReady = true,
                phase = phase,
                current = current,
                total = total,
                message = message,
                installedVersion = when (phase) {
                    PatchPhase.READY, PatchPhase.COMPLETE -> installedVer
                    else -> prev.installedVersion
                },
                installedCount = when (phase) {
                    PatchPhase.READY, PatchPhase.COMPLETE -> installedCount
                    else -> prev.installedCount
                },
                hasBackup = when (phase) {
                    PatchPhase.READY, PatchPhase.COMPLETE -> hasBackup
                    else -> prev.hasBackup
                },
                groupEntryCounts = groupCounts,
                currentOrder = currentOrder,
            )
            flow.value = nextState

            if (phase == PatchPhase.COMPLETE || phase == PatchPhase.ERROR) {
                revokeTargetGrants(targetPkg)
            }
            if (currentOrder != null && currentOrder != mutableConfig.value.groupOrder &&
                phase == PatchPhase.READY && prev.currentOrder == null
            ) {
                updateConfig(mutableConfig.value.copy(groupOrder = currentOrder))
            }
        }
    }

    fun attachUi() {
        uiSubscribers++
        if (!registered) {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(EmojiPatchProtocol.ACTION_STATUS),
                ContextCompat.RECEIVER_EXPORTED,
            )
            registered = true
        }
        refreshLocalCacheStatus()
        refreshTargets()
    }

    fun detachUi() {
        uiSubscribers = (uiSubscribers - 1).coerceAtLeast(0)
        if (uiSubscribers == 0) {
            pingJob?.cancel()
            pingJob = null
            if (registered) {
                runCatching { appContext.unregisterReceiver(receiver) }
                registered = false
            }
        }
    }

    fun refreshLocalCacheStatus() {
        mutableArchiveSummary.value = NotoArchiveCache.cachedSummary(appContext)
        mutableEmojiTestCached.value = versionRepository.isEmojiTestCached(
            appContext,
            UnicodeVersionOption(mutableConfig.value.unicodeFolder, mutableConfig.value.emojiVersion),
        )
    }

    fun refreshTargets() {
        refreshTargetAvailability(EmojiPatchProtocol.TARGET_SOGOU, mutableSogouState, "搜狗输入法小米版")
        refreshTargetAvailability(EmojiPatchProtocol.TARGET_BAIDU, mutableBaiduState, "百度输入法小米版")
        startPinging()
    }

    private fun refreshTargetAvailability(
        pkgName: String,
        flow: MutableStateFlow<EmojiTargetUiState>,
        label: String,
    ) {
        val installed = isPackageInstalled(pkgName)
        if (!installed) {
            flow.value = EmojiTargetUiState(
                targetPackage = pkgName,
                targetInstalled = false,
                hookReady = false,
                phase = PatchPhase.ERROR,
                message = "未安装$label",
            )
            return
        }
        val current = flow.value
        if (!current.isRunning) {
            flow.value = current.copy(
                targetInstalled = true,
                hookReady = false,
                phase = PatchPhase.WAITING_FOR_HOOK,
                current = 0,
                total = 0,
                message = "等待输入法进程中的 Hook 连接…",
            )
        }
    }

    fun selectVersion(option: UnicodeVersionOption) {
        val next = mutableConfig.value.copy(
            unicodeFolder = option.unicodeFolder,
            emojiVersion = option.emojiVersion,
        )
        updateConfig(next)
        refreshLocalCacheStatus()
    }

    fun setOnlySupportedGlyphs(onlySupported: Boolean) {
        updateConfig(mutableConfig.value.copy(includeUnsupportedSystemEmoji = !onlySupported))
    }

    fun moveGroupUp(packageId: Int) {
        val current = mutableConfig.value.groupOrder.toMutableList()
        val index = current.indexOf(packageId)
        if (index <= 0) return
        current[index] = current[index - 1].also { current[index - 1] = current[index] }
        updateConfig(mutableConfig.value.copy(groupOrder = current))
    }

    fun moveGroupDown(packageId: Int) {
        val current = mutableConfig.value.groupOrder.toMutableList()
        val index = current.indexOf(packageId)
        if (index < 0 || index >= current.lastIndex) return
        current[index] = current[index + 1].also { current[index + 1] = current[index] }
        updateConfig(mutableConfig.value.copy(groupOrder = current))
    }

    fun resetGroupOrder() {
        updateConfig(mutableConfig.value.copy(groupOrder = EmojiCatalog.orderGroups.map { it.packageId }))
    }

    fun refreshOnlineVersions() {
        if (mutableRefreshingVersions.value) return
        scope.launch {
            mutableRefreshingVersions.value = true
            runCatching {
                versionRepository.fetchStableVersions()
            }.onSuccess { fetched ->
                if (fetched.isNotEmpty()) {
                    versionRepository.saveCachedVersions(appContext, fetched)
                    mutableVersions.value = fetched
                    mutableArchiveMessage.value = "已刷新在线版本列表（${fetched.size} 个）"
                }
            }.onFailure { err ->
                mutableArchiveMessage.value = "在线版本刷新失败：${err.message ?: err.javaClass.simpleName}"
            }
            mutableRefreshingVersions.value = false
        }
    }

    fun downloadAssets() {
        if (mutableArchiveBusy.value) return
        scope.launch {
            mutableArchiveBusy.value = true
            runCatching {
                val cfg = mutableConfig.value
                val option = UnicodeVersionOption(cfg.unicodeFolder, cfg.emojiVersion)
                versionRepository.downloadEmojiTest(appContext, option) { msg ->
                    mutableArchiveMessage.value = msg
                }
                refreshLocalCacheStatus()
                NotoArchiveCache.download(appContext) { msg ->
                    mutableArchiveMessage.value = msg
                }
            }.onSuccess {
                refreshLocalCacheStatus()
                mutableArchiveMessage.value = "素材包与清单已就绪"
            }.onFailure { err ->
                refreshLocalCacheStatus()
                mutableArchiveMessage.value = "素材准备失败：${err.message ?: err.javaClass.simpleName}"
            }
            mutableArchiveBusy.value = false
        }
    }

    fun importArchiveZip(uri: Uri) {
        if (mutableArchiveBusy.value) return
        scope.launch {
            mutableArchiveBusy.value = true
            runCatching {
                NotoArchiveCache.importZip(appContext, uri) { msg ->
                    mutableArchiveMessage.value = msg
                }
            }.onSuccess {
                refreshLocalCacheStatus()
            }.onFailure { err ->
                refreshLocalCacheStatus()
                mutableArchiveMessage.value = "导入 ZIP 失败：${err.message ?: err.javaClass.simpleName}"
            }
            mutableArchiveBusy.value = false
        }
    }

    fun installTarget(targetPackage: String) {
        val flow = stateFlowFor(targetPackage) ?: return
        if (!flow.value.canAct) return

        val archiveUri = NotoArchiveCache.cached(appContext)
        if (archiveUri == null) {
            flow.value = flow.value.copy(
                phase = PatchPhase.ERROR,
                message = "请先下载或导入 Noto Emoji ZIP 素材包",
            )
            return
        }

        val cfg = mutableConfig.value
        val option = UnicodeVersionOption(cfg.unicodeFolder, cfg.emojiVersion)

        scope.launch {
            val emojiTestUri = versionRepository.cachedEmojiTestUri(appContext, option)
                ?: runCatching {
                    flow.value = flow.value.copy(
                        phase = PatchPhase.FETCHING_MANIFEST,
                        current = 0,
                        total = 0,
                        message = "正在下载 Unicode ${option.emojiVersion} 清单…",
                    )
                    versionRepository.downloadEmojiTest(appContext, option) { msg ->
                        flow.value = flow.value.copy(message = msg)
                    }
                }.getOrElse { err ->
                    flow.value = flow.value.copy(
                        phase = PatchPhase.ERROR,
                        message = "无法获取 emoji-test：${err.message ?: err.javaClass.simpleName}",
                    )
                    return@launch
                }

            refreshLocalCacheStatus()
            revokeTargetGrants(targetPackage)
            try {
                appContext.grantUriPermission(
                    targetPackage,
                    archiveUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                appContext.grantUriPermission(
                    targetPackage,
                    emojiTestUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                grantedUrisByTarget[targetPackage] = listOf(archiveUri, emojiTestUri)
            } catch (error: SecurityException) {
                revokeTargetGrants(targetPackage)
                flow.value = flow.value.copy(
                    phase = PatchPhase.ERROR,
                    message = "无法授权目标输入法读取素材：${error.message}",
                )
                return@launch
            }

            val newSession = UUID.randomUUID().toString()
            sessionsByTarget[targetPackage] = newSession
            flow.value = flow.value.copy(
                phase = PatchPhase.FETCHING_MANIFEST,
                current = 0,
                total = 0,
                message = "已下发安装指令，等待目标进程响应…",
            )
            sendCommand(
                targetPackage = targetPackage,
                sessionId = newSession,
                command = EmojiPatchProtocol.COMMAND_INSTALL,
                configuration = cfg,
                archiveUri = archiveUri,
                emojiTestUri = emojiTestUri,
            )
            startPinging()
        }
    }

    fun applySogouOrder() {
        val current = mutableSogouState.value
        if (!current.canAct) return
        val cfg = mutableConfig.value
        val newSession = UUID.randomUUID().toString()
        sessionsByTarget[EmojiPatchProtocol.TARGET_SOGOU] = newSession
        mutableSogouState.value = current.copy(
            phase = PatchPhase.ORDERING,
            current = 0,
            total = cfg.groupOrder.size,
            message = "正在保存搜狗分组排序…",
        )
        sendCommand(
            targetPackage = EmojiPatchProtocol.TARGET_SOGOU,
            sessionId = newSession,
            command = EmojiPatchProtocol.COMMAND_APPLY_ORDER,
            configuration = cfg,
        )
        startPinging()
    }

    fun restoreTarget(targetPackage: String) {
        val flow = stateFlowFor(targetPackage) ?: return
        if (!flow.value.canAct) return
        revokeTargetGrants(targetPackage)
        val newSession = UUID.randomUUID().toString()
        sessionsByTarget[targetPackage] = newSession
        flow.value = flow.value.copy(
            phase = PatchPhase.RESTORING,
            current = 0,
            total = 1,
            message = "正在发送恢复默认请求…",
        )
        sendCommand(
            targetPackage = targetPackage,
            sessionId = newSession,
            command = EmojiPatchProtocol.COMMAND_RESTORE,
        )
        startPinging()
    }

    private fun revokeTargetGrants(targetPackage: String) {
        val uris = grantedUrisByTarget.remove(targetPackage) ?: return
        uris.forEach { uri ->
            runCatching {
                appContext.revokeUriPermission(
                    targetPackage,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    private fun sendCommand(
        targetPackage: String,
        sessionId: String,
        command: String,
        configuration: EmojiPatchConfig? = null,
        archiveUri: Uri? = null,
        emojiTestUri: Uri? = null,
    ) {
        val intent = Intent(EmojiPatchProtocol.ACTION_COMMAND)
            .setPackage(targetPackage)
            .putExtra(EmojiPatchProtocol.EXTRA_TARGET_PACKAGE, targetPackage)
            .putExtra(EmojiPatchProtocol.EXTRA_COMMAND, command)
            .putExtra(EmojiPatchProtocol.EXTRA_SESSION_ID, sessionId)
        configuration?.putInto(intent)
        if (archiveUri != null && emojiTestUri != null) {
            intent.putExtra(EmojiPatchProtocol.EXTRA_ARCHIVE_URI, archiveUri)
            intent.putExtra(EmojiPatchProtocol.EXTRA_EMOJI_TEST_URI, emojiTestUri)
            val clipData = ClipData.newUri(appContext.contentResolver, "Noto ZIP", archiveUri)
            clipData.addItem(ClipData.Item(emojiTestUri))
            intent.clipData = clipData
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true)
            appContext.sendBroadcast(intent, null, options.toBundle())
        } else {
            appContext.sendBroadcast(intent)
        }
    }

    private fun startPinging() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (registered && shouldKeepPinging()) {
                pingIfNeeded(mutableSogouState.value)
                pingIfNeeded(mutableBaiduState.value)
                delay(PING_INTERVAL_MILLIS)
            }
        }
    }

    private fun pingIfNeeded(state: EmojiTargetUiState) {
        if (!state.targetInstalled) return
        if (state.hookReady && !state.isRunning) return
        val session = sessionsByTarget.getValue(state.targetPackage)
        sendCommand(
            targetPackage = state.targetPackage,
            sessionId = session,
            command = EmojiPatchProtocol.COMMAND_PING,
        )
    }

    private fun shouldKeepPinging(): Boolean {
        val s = mutableSogouState.value
        val b = mutableBaiduState.value
        val sNeeds = s.targetInstalled && (!s.hookReady || s.isRunning)
        val bNeeds = b.targetInstalled && (!b.hookReady || b.isRunning)
        return sNeeds || bNeeds
    }

    private fun stateFlowFor(targetPackage: String): MutableStateFlow<EmojiTargetUiState>? =
        when (targetPackage) {
            EmojiPatchProtocol.TARGET_SOGOU -> mutableSogouState
            EmojiPatchProtocol.TARGET_BAIDU -> mutableBaiduState
            else -> null
        }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun loadStoredConfig(): EmojiPatchConfig {
        val defaults = EmojiPatchConfig()
        val folder = prefs.getString(KEY_UNICODE_FOLDER, defaults.unicodeFolder)
            ?: defaults.unicodeFolder
        val version = prefs.getString(KEY_EMOJI_VERSION, defaults.emojiVersion)
            ?: defaults.emojiVersion
        val includeUnsupported = prefs.getBoolean(
            KEY_INCLUDE_UNSUPPORTED,
            defaults.includeUnsupportedSystemEmoji,
        )
        val orderRaw = prefs.getString(KEY_GROUP_ORDER, null).orEmpty()
        val order = orderRaw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .takeIf { it.size == ORDERABLE_IDS.size && it.toSet() == ORDERABLE_IDS }
            ?: defaults.groupOrder
        return runCatching {
            EmojiPatchConfig(
                unicodeFolder = folder,
                emojiVersion = version,
                includeUnsupportedSystemEmoji = includeUnsupported,
                groupOrder = order,
            )
        }.getOrDefault(defaults)
    }

    private fun updateConfig(newConfig: EmojiPatchConfig) {
        mutableConfig.value = newConfig
        prefs.edit()
            .putString(KEY_UNICODE_FOLDER, newConfig.unicodeFolder)
            .putString(KEY_EMOJI_VERSION, newConfig.emojiVersion)
            .putBoolean(KEY_INCLUDE_UNSUPPORTED, newConfig.includeUnsupportedSystemEmoji)
            .putString(KEY_GROUP_ORDER, newConfig.groupOrder.joinToString(","))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "miime_emoji_patch"
        private const val KEY_UNICODE_FOLDER = "unicode_folder"
        private const val KEY_EMOJI_VERSION = "emoji_version"
        private const val KEY_INCLUDE_UNSUPPORTED = "include_unsupported"
        private const val KEY_GROUP_ORDER = "group_order"
        private const val PING_INTERVAL_MILLIS = 1_000L
        private val ORDERABLE_IDS = EmojiCatalog.orderGroups.map { it.packageId }.toSet()

        @Volatile
        private var instance: EmojiPatchController? = null

        fun getInstance(context: Context): EmojiPatchController {
            return instance ?: synchronized(this) {
                instance ?: EmojiPatchController(context).also { instance = it }
            }
        }
    }
}
