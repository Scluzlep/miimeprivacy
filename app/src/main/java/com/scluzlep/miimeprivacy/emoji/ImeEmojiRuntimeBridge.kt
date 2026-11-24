package com.scluzlep.miimeprivacy.emoji

import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.os.Process
import androidx.core.content.ContextCompat
import com.scluzlep.miimeprivacy.model.ImeTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-IME command receiver attached inside main processes of Sogou and Baidu Xiaomi editions.
 */
object ImeEmojiRuntimeBridge {
    private val lock = Any()

    @Volatile
    private var attachedPackage: String? = null

    @Volatile
    private var attachedContext: Context? = null

    @Volatile
    private var receiver: BroadcastReceiver? = null

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var operationJob: Job? = null

    @Volatile
    private var lastPhase: PatchPhase = PatchPhase.READY

    @Volatile
    private var lastCurrent: Int = 0

    @Volatile
    private var lastTotal: Int = 0

    @Volatile
    private var lastMessage: String = ""

    fun detachOnHotReload() {
        synchronized(lock) {
            val ctx = attachedContext
            val rec = receiver
            if (ctx != null && rec != null) {
                runCatching { ctx.unregisterReceiver(rec) }
            }
            receiver = null
            attachedContext = null
            attachedPackage = null
            operationJob?.cancel()
            operationJob = null
            scope?.cancel()
            scope = null
        }
    }

    fun attachIfMainProcess(
        target: ImeTarget,
        context: Context,
        processName: String,
    ) {
        if (target != ImeTarget.SOGOU && target != ImeTarget.BAIDU) return
        if (processName != target.packageName) return
        val appContext = context.applicationContext ?: context
        if (appContext.packageName != target.packageName) return

        synchronized(lock) {
            if (receiver != null && attachedPackage == target.packageName) {
                return
            }
            detachOnHotReload()

            val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = bridgeScope
            attachedContext = appContext
            attachedPackage = target.packageName
            lastPhase = PatchPhase.READY
            lastCurrent = 0
            lastTotal = 0
            lastMessage = buildReadyStatus(target, appContext).message

            val cmdReceiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    if (intent.action != EmojiPatchProtocol.ACTION_COMMAND) return
                    val targetPkg = intent.getStringExtra(EmojiPatchProtocol.EXTRA_TARGET_PACKAGE)
                    if (targetPkg != appContext.packageName) return
                    val senderPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        sentFromPackage
                    } else {
                        null
                    }
                    val callerProof = extractParcelableExtra(
                        intent,
                        EmojiPatchProtocol.EXTRA_CALLER_PROOF,
                        PendingIntent::class.java,
                    )
                    if (!EmojiPatchProtocol.isTrustedModuleCaller(
                            apiLevel = Build.VERSION.SDK_INT,
                            sentFromPackage = senderPkg,
                            proofCreatorPackage = callerProof?.creatorPackage,
                        )
                    ) {
                        return
                    }

                    val command = intent.getStringExtra(EmojiPatchProtocol.EXTRA_COMMAND) ?: return
                    val sessionId = intent.getStringExtra(EmojiPatchProtocol.EXTRA_SESSION_ID)
                        ?.takeIf { it.isNotBlank() }
                        ?: return

                    handleCommand(
                        target = target,
                        appContext = appContext,
                        bridgeScope = bridgeScope,
                        intent = intent,
                        command = command,
                        sessionId = sessionId,
                    )
                }
            }

            runCatching {
                ContextCompat.registerReceiver(
                    appContext,
                    cmdReceiver,
                    IntentFilter(EmojiPatchProtocol.ACTION_COMMAND),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                receiver = cmdReceiver
            }.onFailure {
                bridgeScope.cancel()
                scope = null
                attachedContext = null
                attachedPackage = null
            }
        }
    }

    private fun handleCommand(
        target: ImeTarget,
        appContext: Context,
        bridgeScope: CoroutineScope,
        intent: Intent,
        command: String,
        sessionId: String,
    ) {
        when (command) {
            EmojiPatchProtocol.COMMAND_PING -> {
                if (operationJob?.isActive == true) {
                    sendStatus(
                        context = appContext,
                        sessionId = sessionId,
                        phase = lastPhase,
                        current = lastCurrent,
                        total = lastTotal,
                        message = lastMessage,
                    )
                    return
                }
                val status = buildReadyStatus(target, appContext)
                lastPhase = PatchPhase.READY
                lastCurrent = 0
                lastTotal = 0
                lastMessage = status.message
                sendStatus(
                    context = appContext,
                    sessionId = sessionId,
                    phase = PatchPhase.READY,
                    current = 0,
                    total = 0,
                    message = status.message,
                    installedVersion = status.installedVersion,
                    installedCount = status.installedCount,
                    hasBackup = status.hasBackup,
                )
            }

            EmojiPatchProtocol.COMMAND_INSTALL -> {
                if (operationJob?.isActive == true) {
                    sendStatus(appContext, sessionId, lastPhase, lastCurrent, lastTotal, lastMessage)
                    return
                }
                val configuration = EmojiPatchConfig.fromIntent(intent)
                val archiveUri = extractParcelableExtra(intent, EmojiPatchProtocol.EXTRA_ARCHIVE_URI, Uri::class.java)
                val emojiTestUri = extractParcelableExtra(intent, EmojiPatchProtocol.EXTRA_EMOJI_TEST_URI, Uri::class.java)
                val archiveFd = runCatching {
                    val uri = requireNotNull(archiveUri) { "未提供 Noto ZIP URI" }
                    require(
                        uri.scheme == ContentResolver.SCHEME_CONTENT &&
                            EmojiPatchProtocol.isTrustedArchiveUri(uri.scheme, uri.authority),
                    ) { "不受信任的 Noto ZIP URI" }
                    requireNotNull(appContext.contentResolver.openFileDescriptor(uri, "r")) {
                        "无法打开 Noto ZIP"
                    }
                }.getOrElse { error ->
                    sendStatus(
                        context = appContext,
                        sessionId = sessionId,
                        phase = PatchPhase.ERROR,
                        current = 0,
                        total = 0,
                        message = "读取素材失败：${error.message}",
                    )
                    return
                }
                val emojiTestFd = runCatching {
                    val uri = requireNotNull(emojiTestUri) { "未提供 emoji-test 清单 URI" }
                    require(
                        uri.scheme == ContentResolver.SCHEME_CONTENT &&
                            EmojiPatchProtocol.isTrustedArchiveUri(uri.scheme, uri.authority),
                    ) { "不受信任的 emoji-test 清单 URI" }
                    requireNotNull(appContext.contentResolver.openFileDescriptor(uri, "r")) {
                        "无法打开 emoji-test 清单"
                    }
                }.getOrElse { error ->
                    runCatching { archiveFd.close() }
                    sendStatus(
                        context = appContext,
                        sessionId = sessionId,
                        phase = PatchPhase.ERROR,
                        current = 0,
                        total = 0,
                        message = "读取清单失败：${error.message}",
                    )
                    return
                }

                operationJob = bridgeScope.launch {
                    val result = runCatching {
                        archiveFd.use { aFd ->
                            emojiTestFd.use { tFd ->
                                val progressCallback: (PatchUpdate) -> Unit = { update ->
                                    lastPhase = update.phase
                                    lastCurrent = update.current
                                    lastTotal = update.total
                                    lastMessage = update.message
                                    sendStatus(
                                        context = appContext,
                                        sessionId = sessionId,
                                        phase = update.phase,
                                        current = update.current,
                                        total = update.total,
                                        message = update.message,
                                        installedVersion = update.installedVersion,
                                        installedCount = update.installedCount,
                                        hasBackup = update.hasBackup,
                                    )
                                }
                                when (target) {
                                    ImeTarget.SOGOU -> SogouEmojiInstaller(
                                        appContext,
                                        configuration,
                                    ).install(aFd, tFd, progressCallback)

                                    ImeTarget.BAIDU -> BaiduEmojiInstaller(
                                        appContext,
                                        configuration,
                                    ).install(aFd, tFd, progressCallback)

                                    ImeTarget.XIAOAI -> error("XiaoAi does not require patching")
                                }
                            }
                        }
                    }
                    result.onFailure { error ->
                        lastPhase = PatchPhase.ERROR
                        lastMessage = error.message ?: error.javaClass.simpleName
                        sendStatus(
                            context = appContext,
                            sessionId = sessionId,
                            phase = PatchPhase.ERROR,
                            current = lastCurrent,
                            total = lastTotal,
                            message = lastMessage,
                        )
                    }
                    if (result.isSuccess && lastPhase == PatchPhase.COMPLETE) {
                        if (target == ImeTarget.SOGOU) {
                            runCatching {
                                SogouEmojiOrderInstaller(appContext, configuration).apply()
                            }
                        }
                        delay(PROCESS_EXIT_DELAY_MILLIS)
                        Process.killProcess(Process.myPid())
                    }
                }
            }

            EmojiPatchProtocol.COMMAND_APPLY_ORDER -> {
                if (target != ImeTarget.SOGOU || operationJob?.isActive == true) return
                val configuration = EmojiPatchConfig.fromIntent(intent)
                operationJob = bridgeScope.launch {
                    lastPhase = PatchPhase.ORDERING
                    lastCurrent = 0
                    lastTotal = configuration.groupOrder.size
                    lastMessage = "正在应用搜狗分组排序…"
                    sendStatus(
                        context = appContext,
                        sessionId = sessionId,
                        phase = lastPhase,
                        current = lastCurrent,
                        total = lastTotal,
                        message = lastMessage,
                    )
                    runCatching {
                        SogouEmojiOrderInstaller(appContext, configuration).apply()
                    }.onSuccess {
                        val ready = buildReadyStatus(target, appContext)
                        lastPhase = PatchPhase.COMPLETE
                        lastCurrent = configuration.groupOrder.size
                        lastTotal = configuration.groupOrder.size
                        lastMessage = "分组排序已保存，搜狗即将重载"
                        sendStatus(
                            context = appContext,
                            sessionId = sessionId,
                            phase = lastPhase,
                            current = lastCurrent,
                            total = lastTotal,
                            message = lastMessage,
                            installedVersion = ready.installedVersion,
                            installedCount = ready.installedCount,
                            hasBackup = ready.hasBackup,
                        )
                        delay(PROCESS_EXIT_DELAY_MILLIS)
                        Process.killProcess(Process.myPid())
                    }.onFailure { error ->
                        lastPhase = PatchPhase.ERROR
                        lastMessage = error.message ?: error.javaClass.simpleName
                        sendStatus(
                            context = appContext,
                            sessionId = sessionId,
                            phase = lastPhase,
                            current = 0,
                            total = configuration.groupOrder.size,
                            message = lastMessage,
                        )
                    }
                }
            }

            EmojiPatchProtocol.COMMAND_RESTORE -> {
                if (operationJob?.isActive == true) return
                operationJob = bridgeScope.launch {
                    lastPhase = PatchPhase.RESTORING
                    lastCurrent = 0
                    lastTotal = 1
                    lastMessage = "正在恢复默认 Emoji…"
                    sendStatus(appContext, sessionId, lastPhase, 0, 1, lastMessage)

                    runCatching {
                        when (target) {
                            ImeTarget.SOGOU -> SogouEmojiInstaller.restore(appContext)
                            ImeTarget.BAIDU -> BaiduEmojiInstaller.restore(appContext)
                            ImeTarget.XIAOAI -> error("XiaoAi has no restore")
                        }
                    }.onSuccess {
                        val status = buildReadyStatus(target, appContext)
                        lastPhase = PatchPhase.COMPLETE
                        lastCurrent = 1
                        lastTotal = 1
                        lastMessage = "已恢复默认配置，输入法即将重载"
                        sendStatus(
                            context = appContext,
                            sessionId = sessionId,
                            phase = lastPhase,
                            current = 1,
                            total = 1,
                            message = lastMessage,
                            installedVersion = status.installedVersion,
                            installedCount = status.installedCount,
                            hasBackup = status.hasBackup,
                        )
                        delay(PROCESS_EXIT_DELAY_MILLIS)
                        Process.killProcess(Process.myPid())
                    }.onFailure { error ->
                        lastPhase = PatchPhase.ERROR
                        lastMessage = error.message ?: error.javaClass.simpleName
                        sendStatus(
                            context = appContext,
                            sessionId = sessionId,
                            phase = lastPhase,
                            current = 0,
                            total = 1,
                            message = lastMessage,
                        )
                    }
                }
            }
        }
    }

    private data class ReadyStatusSnapshot(
        val installedVersion: String?,
        val installedCount: Int,
        val hasBackup: Boolean,
        val message: String,
    )

    private fun buildReadyStatus(target: ImeTarget, context: Context): ReadyStatusSnapshot =
        when (target) {
            ImeTarget.SOGOU -> {
                val st = SogouEmojiInstaller.readInstalledStatus(context)
                val msg = if (st.installedVersion != null) {
                    "已连接 · 已安装 Emoji ${st.installedVersion}，共 ${st.installedCount} 项"
                } else {
                    "已连接 · 当前为原版配置"
                }
                ReadyStatusSnapshot(st.installedVersion, st.installedCount, st.hasBackup, msg)
            }

            ImeTarget.BAIDU -> {
                val st = BaiduEmojiInstaller.readInstalledStatus(context)
                val msg = if (st.installedVersion != null) {
                    "已连接 · 已安装 Emoji ${st.installedVersion}，共 ${st.installedCount} 项"
                } else {
                    "已连接 · 当前内置仅 Emoji 5.0"
                }
                ReadyStatusSnapshot(st.installedVersion, st.installedCount, st.hasBackup, msg)
            }

            ImeTarget.XIAOAI -> ReadyStatusSnapshot(null, 0, false, "无需补全")
        }

    private fun <T : Parcelable> extractParcelableExtra(
        intent: Intent,
        key: String,
        clazz: Class<T>,
    ): T? {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, clazz)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    private fun sendStatus(
        context: Context,
        sessionId: String,
        phase: PatchPhase,
        current: Int,
        total: Int,
        message: String,
        installedVersion: String? = null,
        installedCount: Int = 0,
        hasBackup: Boolean = false,
    ) {
        val pkgName = context.packageName
        val proofIntent = Intent(EmojiPatchProtocol.ACTION_STATUS_PROOF_INTERNAL)
            .setPackage(pkgName)
        val proof = PendingIntent.getBroadcast(
            context,
            0,
            proofIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        val intent = Intent(EmojiPatchProtocol.ACTION_STATUS)
            .setPackage(EmojiPatchProtocol.MODULE_PACKAGE)
            .putExtra(EmojiPatchProtocol.EXTRA_TARGET_PACKAGE, pkgName)
            .putExtra(EmojiPatchProtocol.EXTRA_CALLER_PROOF, proof)
            .putExtra(EmojiPatchProtocol.EXTRA_SESSION_ID, sessionId)
            .putExtra(EmojiPatchProtocol.EXTRA_PHASE, phase.name)
            .putExtra(EmojiPatchProtocol.EXTRA_CURRENT, current)
            .putExtra(EmojiPatchProtocol.EXTRA_TOTAL, total)
            .putExtra(EmojiPatchProtocol.EXTRA_MESSAGE, message)
            .putExtra(EmojiPatchProtocol.EXTRA_INSTALLED_COUNT, installedCount)
            .putExtra(EmojiPatchProtocol.EXTRA_HAS_BACKUP, hasBackup)
        if (!installedVersion.isNullOrBlank()) {
            intent.putExtra(EmojiPatchProtocol.EXTRA_INSTALLED_VERSION, installedVersion)
        }
        if (pkgName == EmojiPatchProtocol.TARGET_SOGOU &&
            phase in setOf(PatchPhase.READY, PatchPhase.COMPLETE, PatchPhase.ERROR)
        ) {
            intent.putExtra(
                EmojiPatchProtocol.EXTRA_GROUP_ENTRY_COUNTS,
                InstalledEmojiGroupCounts.read(context),
            )
            SogouEmojiOrderInstaller.readCurrentOrder(context)?.let { order ->
                intent.putExtra(EmojiPatchProtocol.EXTRA_GROUP_ORDER, order)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true)
            context.sendBroadcast(intent, null, options.toBundle())
        } else {
            context.sendBroadcast(intent)
        }
    }

    private const val PROCESS_EXIT_DELAY_MILLIS = 750L
}
