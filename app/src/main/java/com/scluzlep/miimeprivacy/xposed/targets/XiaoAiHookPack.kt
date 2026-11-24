package com.scluzlep.miimeprivacy.xposed.targets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.inputmethod.EditorInfo
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ImeHookPack
import com.scluzlep.miimeprivacy.xposed.ImePackInstallResult
import com.scluzlep.miimeprivacy.xposed.ResolveResult
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Verified class/method names and DexKit anchor strings extracted from
 * `com.xiaomi.type` (versionCode 21363) decompiled sources.
 */
internal object XiaoAiAnchors {
    const val CLASS_CTA_CONSENT_ACTIVITY = "com.mi.ime.CtaConsentActivity"
    const val METHOD_CTA_ON_ACCEPTED = "onCtaAccepted"
    const val METHOD_CTA_REQUEST_DIALOG = "requestCtaDialog"
    const val METHOD_CTA_ON_ACTIVITY_RESULT = "onActivityResult"
    const val CTA_REQUEST_CODE = 1001
    const val CTA_RESULT_ACCEPTED = 1
    val CTA_ACTIVITY_STRINGS = listOf("Failed to start CTA dialog", "CTA no permission to launch")
    val CTA_DIALOG_STRINGS = listOf("Failed to start CTA dialog")
    val CTA_CONSENTED_STRINGS = listOf("cta_consented")
    val CTA_RESULT_STRINGS = listOf("CTA missing required info")

    const val CLASS_ONETRACK_SDK = "com.xiaomi.onetrack.OneTrack"
    const val METHOD_ONETRACK_SET_NET = "setAccessNetworkEnable"
    const val METHOD_ONETRACK_SET_DISABLE = "setDisable"
    val ONETRACK_SDK_TRACK_METHODS = listOf(
        "track",
        "trackByFileOnlySystemApp",
        "trackEventFromH5",
        "trackException",
        "trackPluginEvent",
        "trackServiceQualityEvent",
        "adTrack"
    )
    const val CLASS_ONETRACK_HELPER = "v7.f"
    val ONETRACK_HELPER_STRINGS = listOf("OneTrackHelper", "Failed to restore OneTrack SDK state")
    val ONETRACK_HELPER_ERROR_STRINGS = listOf("ime_error")

    const val CLASS_ONETRACK_IMP = "com.xiaomi.onetrack.api.au"
    val ONETRACK_IMP_STRINGS = listOf("OneTrackImp")
    const val CLASS_ONETRACK_SYSTEM_IMP = "com.xiaomi.onetrack.api.ay"
    val ONETRACK_SYSTEM_IMP_STRINGS = listOf("OneTrackSystemImp", "track mIOneTrackService is null!")
    const val CLASS_ONETRACK_LOCAL_IMP = "com.xiaomi.onetrack.api.av"
    val ONETRACK_LOCAL_IMP_STRINGS = listOf(
        "OneTrackLocalImp",
        "Tracking data is disabled or onetrack use system net traffic only, skip it."
    )
    const val CLASS_ONETRACK_SERVICE_CONN = "com.xiaomi.onetrack.api.ba"
    val ONETRACK_SERVICE_CONN_STRINGS = listOf(
        "ServiceConnectManager",
        "com.miui.analytics.onetrack.OneTrackService"
    )

    const val CLASS_SESSION_COLLECTOR = "v7.e"
    const val CLASS_SESSION_HOLDER = "v7.g"
    const val CLASS_SESSION_CACHE = "v7.c"
    const val FIELD_SESSION_HOLDER = "f"
    const val FIELD_SESSION_PASSWORD = "j"
    val SESSION_COLLECTOR_STRINGS = listOf("SessionActionCollector", "duration_s", "key_cnt", "assoc_show_cnt")
    val SESSION_HOLDER_STRINGS = listOf("newline", "search", "send", "go")
    val SESSION_ACTION_BACK_STRINGS = listOf("type=back")
    val SESSION_ACTION_DEL_STRINGS = listOf("type=delete")
    val SESSION_ACTION_OTHER_STRINGS = listOf("source_app")
    val SESSION_CACHE_STRINGS = listOf("candidate_content", "generate_result")

    const val CLASS_AGENT_CONNECTION = "k8.o"
    val AGENT_CONNECTION_STRINGS = listOf(
        "AgentServiceConnection",
        "com.aios.osbot.action.EXTERNAL_AGENT",
        "com.miui.voiceassist"
    )
    val AGENT_BIND_STRINGS = listOf("com.aios.osbot.action.EXTERNAL_AGENT")
    const val CLASS_VOICEASSIST_LAUNCH = "rh.b"
    val VOICEASSIST_ASSIST_STRINGS = listOf("android.intent.action.ASSIST", "com.miui.voiceassist")

    const val CLASS_MICLAW_MANAGER = "k8.n0"
    const val CLASS_MICLAW_SESSION = "k8.d0"
    const val CLASS_MICLAW_FAILED = "k8.g"
    val MICLAW_MANAGER_STRINGS = listOf("MiclawAgentManager", "mi_ime")
    val MICLAW_FAILED_STRINGS = listOf("Failed(message=", ", code=")

    const val CLASS_SCREENSHOT_CAPTURE = "eb.r"
    val SCREENSHOT_CAPTURE_STRINGS = listOf("ScreenshotCapture", "android.window.ScreenCapture")

    const val CLASS_TRANSLATE_ENGINE = "nc.r"
    const val CLASS_TRANSLATE_START = "nc.c"
    const val CLASS_TRANSLATE_IDLE = "nc.x"
    const val CLASS_TRANSLATE_ERROR = "mc.c"
    val TRANSLATE_ENGINE_STRINGS = listOf("TranslateEngine", "TokenProvider returned blank baseUrl")
    val TRANSLATE_START_STRINGS = listOf("Start(textLen=", ", stream=false)")
    val TRANSLATE_IDLE_STRINGS = listOf("Idle")
    val TRANSLATE_ERROR_STRINGS = listOf("TranslateError(code=")

    const val CLASS_SPEECH_ASR_MANAGER = "m8.h"
    val SPEECH_ASR_STRINGS = listOf("SpeechAsrManager", "hotwordsJson")

    const val CLASS_TAIYI_SERVICE = "rc.j"
    val TAIYI_SERVICE_STRINGS = listOf("init bind service SecurityException. ", "ServiceConnection(")
    val TAIYI_CONNECT_STRINGS = listOf("init service bind start")
    const val CLASS_TAIYI_MANAGER = "rc.b"
    const val CLASS_TAIYI_REQUEST = "com.xiaomi.taiyi.sdk.transfer.data.AIRequest"
    const val CLASS_TAIYI_RESPONSE = "com.xiaomi.taiyi.sdk.transfer.data.AIResponse"
    val TAIYI_MANAGER_STRINGS = listOf("AIManager", "execute must on work thread!")
    const val TAIYI_BLOCKED_CODE = 10103

    const val CLASS_CLIPBOARD_MONITOR = "fb.d0"
    val CLIPBOARD_MONITOR_STRINGS = listOf("ClipboardMonitor", "sensitive clip ignored", "clip accepted: rawLen=")

    const val CLASS_HOTWORD_CLIENT = "ca.w"
    val HOTWORD_CLIENT_STRINGS = listOf("HotwordRemoteClient", "com.xiaomi.aicr", "ime_hotword_service_version")
    const val CLASS_HOTWORD_EDIT_TRACKER = "ba.b"
    val HOTWORD_TRACKER_STRINGS = listOf("HotwordEditTracker", "textProvider 返回 null，无法初始化 ShadowBuffer")
}

/**
 * Pure, JVM-testable decision and payload-sanitization policies for XiaoAi IME.
 * Hooks evaluate [ConfigBridge.current] dynamically through these helpers so live settings
 * take effect immediately without restarting the IME process.
 */
internal object XiaoAiPrivacyPolicy {

    private val AGENT_SECTION_PREFIXES = listOf("[user_text:", "[app:", "[peer_text:", "[has_screenshot:")

    fun shouldAllowCtaAccept(config: PrivacyConfig, hasExplicitUserAccept: Boolean): Boolean {
        if (!config.xiaoaiManaged || !config.blockTelemetry) {
            return true
        }
        return hasExplicitUserAccept
    }

    fun shouldForceOneTrackNetworkDisabled(config: PrivacyConfig): Boolean =
        config.xiaoaiManaged && config.blockTelemetry

    fun shouldBlockOneTrackEvent(config: PrivacyConfig, isPasswordSession: Boolean): Boolean =
        config.xiaoaiManaged && (config.blockTelemetry || (config.blockPasswordStats && isPasswordSession))

    fun shouldBlockSessionCounters(config: PrivacyConfig, isPasswordSession: Boolean): Boolean =
        config.xiaoaiManaged && (config.blockTelemetry || (config.blockPasswordStats && isPasswordSession))

    fun shouldBlockCloudAiIpc(config: PrivacyConfig): Boolean =
        config.xiaoaiManaged && !config.effectiveCloudEnabled(ImeTarget.XIAOAI)

    fun shouldSanitizeVoiceAssistPayload(config: PrivacyConfig): Boolean =
        config.xiaoaiManaged &&
            config.effectiveCloudEnabled(ImeTarget.XIAOAI) &&
            (config.sanitizeCloudPayload || config.stripCursorContext || config.maskHostPackage)

    fun shouldStripScreenCapture(config: PrivacyConfig): Boolean =
        config.xiaoaiManaged &&
            (!config.effectiveCloudEnabled(ImeTarget.XIAOAI) || config.sanitizeCloudPayload || config.stripCursorContext)

    fun shouldMaskSpeechHostPackage(config: PrivacyConfig): Boolean =
        config.xiaoaiManaged &&
            config.effectiveCloudEnabled(ImeTarget.XIAOAI) &&
            config.maskHostPackage

    fun shouldBlockBackgroundClipboardRead(config: PrivacyConfig): Boolean =
        config.xiaoaiManaged && config.clipboardPrivacy

    fun shouldBlockHotwordTracker(config: PrivacyConfig): Boolean =
        config.xiaoaiManaged &&
            (config.blockXiaoAiHotwordTracker || !config.effectiveCloudEnabled(ImeTarget.XIAOAI))

    /**
     * Strips sensitive `[app:...]`, `[peer_text:...]`, and `[has_screenshot:...]` sections
     * built by `jb.q` / `lb.a` without mangling multiline chat content containing `]`.
     */
    fun sanitizeAgentPrompt(prompt: String, config: PrivacyConfig): String {
        if (prompt.isEmpty()) return prompt
        if (!config.xiaoaiManaged) return prompt

        val dropApp = config.maskHostPackage
        val dropPeer = config.stripCursorContext || config.sanitizeCloudPayload
        val dropScreenshotFlag = config.sanitizeCloudPayload
        if (!dropApp && !dropPeer && !dropScreenshotFlag) return prompt

        val lines = prompt.split('\n')
        if (lines.none { line -> AGENT_SECTION_PREFIXES.any { prefix -> line.startsWith(prefix) } }) {
            return prompt
        }

        val keptSections = ArrayList<StringBuilder>()
        var currentTag: String? = null
        var currentBuffer: StringBuilder? = null

        fun flushSection() {
            val buf = currentBuffer ?: return
            val drop = when (currentTag) {
                "[app:" -> dropApp
                "[peer_text:" -> dropPeer
                "[has_screenshot:" -> dropScreenshotFlag
                else -> false
            }
            if (!drop) {
                keptSections.add(buf)
            }
            currentBuffer = null
            currentTag = null
        }

        for (line in lines) {
            val matchedTag = AGENT_SECTION_PREFIXES.firstOrNull { line.startsWith(it) }
            if (matchedTag != null) {
                flushSection()
                currentTag = matchedTag
                currentBuffer = StringBuilder(line)
            } else if (currentBuffer != null) {
                currentBuffer!!.append('\n').append(line)
            } else {
                currentTag = null
                currentBuffer = StringBuilder(line)
            }
        }
        flushSection()

        return keptSections.joinToString("\n") { it.toString() }
    }
}

/**
 * Pure JVM-testable guard helpers for XiaoAi's OneTrack initialization and `ar` dispatch paths.
 */
internal object XiaoAiOneTrackGuard {

    private val forcedDisabled = AtomicBoolean(false)

    internal fun resetForTest() {
        forcedDisabled.set(false)
    }

    fun defaultReturnValue(returnType: Class<*>): Any? = when (returnType) {
        Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
        Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
        Float::class.javaPrimitiveType, Float::class.javaObjectType -> 0f
        Double::class.javaPrimitiveType, Double::class.javaObjectType -> 0.0
        else -> null
    }

    fun invokeSetDisable(oneTrackSdkClass: Class<*>?, disabled: Boolean): Boolean {
        val method = SymbolResolver.findDeclaredMethodOrNull(
            oneTrackSdkClass,
            XiaoAiAnchors.METHOD_ONETRACK_SET_DISABLE,
            Boolean::class.javaPrimitiveType!!
        ) ?: return false
        return runCatching {
            method.isAccessible = true
            method.invoke(null, disabled)
            true
        }.getOrDefault(false)
    }

    fun handleOneTrackImpInit(
        config: PrivacyConfig,
        oneTrackSdkClass: Class<*>?,
        proceed: () -> Any?,
        incrementBlocked: () -> Unit
    ): Any? {
        val result = proceed()
        if (XiaoAiPrivacyPolicy.shouldForceOneTrackNetworkDisabled(config)) {
            if (invokeSetDisable(oneTrackSdkClass, true)) {
                forcedDisabled.set(true)
            }
            incrementBlocked()
        } else if (forcedDisabled.compareAndSet(true, false)) {
            invokeSetDisable(oneTrackSdkClass, false)
        }
        return result
    }

    fun syncOneTrackDisabledIfNeeded(config: PrivacyConfig, oneTrackSdkClass: Class<*>?) {
        if (XiaoAiPrivacyPolicy.shouldForceOneTrackNetworkDisabled(config)) {
            if (forcedDisabled.compareAndSet(false, true)) {
                if (!invokeSetDisable(oneTrackSdkClass, true)) {
                    forcedDisabled.set(false)
                }
            }
        } else if (forcedDisabled.compareAndSet(true, false)) {
            invokeSetDisable(oneTrackSdkClass, false)
        }
    }

    fun handleOneTrackArMethod(
        config: PrivacyConfig,
        isPasswordSession: Boolean,
        returnType: Class<*>,
        proceed: () -> Any?,
        incrementBlocked: () -> Unit
    ): Any? {
        if (XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(config, isPasswordSession)) {
            incrementBlocked()
            return defaultReturnValue(returnType)
        }
        return proceed()
    }

    fun safeDeclaredMethods(clazz: Class<*>): List<Method> =
        runCatching { clazz.declaredMethods.toList() }.getOrElse { emptyList() }

    fun safeDeclaredFields(clazz: Class<*>): List<Field> =
        runCatching { clazz.declaredFields.toList() }.getOrElse { emptyList() }

    fun findPublicArInterfaceMethods(impClass: Class<*>?): List<Method> {
        impClass ?: return emptyList()
        return safeDeclaredMethods(impClass).filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                Modifier.isPublic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                (method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType!!)) ||
                    method.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java)) ||
                    method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType!!)))
        }
    }
}

/**
 * XiaoAi IME (`com.xiaomi.type`) Xposed hook pack.
 *
 * Covers the 5 core privacy rules in `HookRuleCatalog.rulesFor(ImeTarget.XIAOAI)`
 * (excluding `xiaoai.overlay_pill`, owned by the overlay subsystem).
 * Explicitly keeps `j9.e.n()` (`cta_consented`) and `DictPack` (`m9.a0`, `j5.v`, `r9.v`) untouched.
 */
object XiaoAiHookPack : ImeHookPack {

    override val target: ImeTarget = ImeTarget.XIAOAI

    private val explicitCtaConsentInProgress = ThreadLocal.withInitial { false }

    override fun install(
        context: Context?,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ): ImePackInstallResult {
        val rules = HookRuleCatalog.rulesFor(target)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL }

        registry.createSymbolResolver(classLoader).use { resolver ->
            runSafely(registry, "CTA fail-open gate") {
                installCtaFailOpenGate(classLoader, resolver, registry)
            }
            runSafely(registry, "OneTrack & password session stats") {
                installOneTrackAndSessionHooks(classLoader, resolver, registry)
            }
            runSafely(registry, "AI cloud & VoiceAssist IPC") {
                installAiCloudAndIpcHooks(classLoader, resolver, registry)
            }
            runSafely(registry, "Clipboard background guard") {
                installClipboardPrivacyHooks(classLoader, resolver, registry)
            }
            runSafely(registry, "Hotword edit tracker") {
                installHotwordTrackerHooks(classLoader, resolver, registry)
            }
        }

        val reports = rules.mapNotNull { registry.getReport(it.ruleId) }
        val activeCount = reports.count { it.status == HookMatchStatus.ACTIVE }
        return ImePackInstallResult(
            target = target,
            reports = reports,
            note = "XiaoAiHookPack installed: $activeCount/${rules.size} rules active (pkg=${appInfo.packageName})"
        )
    }

    // -------------------------------------------------------------------------
    // Rule 1: RULE_XIAOAI_CTA_GATE
    // -------------------------------------------------------------------------

    private fun installCtaFailOpenGate(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CTA_GATE
        val ctaResult = resolver.resolveClass(
            cacheKey = "xiaoai.cta_activity",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.CTA_ACTIVITY_STRINGS)
            }
        )
        val ctaClass = ctaResult.value

        val onActivityResultMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.cta_on_activity_result",
            targetClass = ctaClass,
            fastPathName = XiaoAiAnchors.METHOD_CTA_ON_ACTIVITY_RESULT,
            paramTypes = arrayOf(
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Intent::class.java
            ),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.CTA_RESULT_STRINGS)
                    .paramTypes(
                        Int::class.javaPrimitiveType!!,
                        Int::class.javaPrimitiveType!!,
                        Intent::class.java
                    )
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterTypes.contentEquals(
                            arrayOf(
                                Int::class.javaPrimitiveType!!,
                                Int::class.javaPrimitiveType!!,
                                Intent::class.java
                            )
                        )
                }
            }
        )

        val requestDialogMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.cta_request_dialog",
            targetClass = ctaClass,
            fastPathName = XiaoAiAnchors.METHOD_CTA_REQUEST_DIALOG,
            paramTypes = emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.CTA_DIALOG_STRINGS)
                    .paramCount(0)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isPrivate(method.modifiers) &&
                        method.parameterCount == 0 &&
                        method.returnType == Void.TYPE &&
                        method.name != XiaoAiAnchors.METHOD_CTA_ON_ACCEPTED
                }
            }
        )

        val onCtaAcceptedMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.cta_on_accepted",
            targetClass = ctaClass,
            fastPathName = XiaoAiAnchors.METHOD_CTA_ON_ACCEPTED,
            paramTypes = emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.CTA_CONSENTED_STRINGS)
                    .paramCount(0)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isPrivate(method.modifiers) &&
                        method.parameterCount == 0 &&
                        method.returnType == Void.TYPE &&
                        method.name != requestDialogMethod.value?.name
                }
            }
        )

        registry.hookExecutable(ruleId, "cta_on_activity_result", onActivityResultMethod) { chain ->
            val requestCode = chain.getArg(0) as? Int
            val resultCode = chain.getArg(1) as? Int
            val isUserAgree = requestCode == XiaoAiAnchors.CTA_REQUEST_CODE &&
                resultCode == XiaoAiAnchors.CTA_RESULT_ACCEPTED
            val prev = explicitCtaConsentInProgress.get() == true
            explicitCtaConsentInProgress.set(isUserAgree)
            try {
                chain.proceed()
            } finally {
                explicitCtaConsentInProgress.set(prev)
            }
        }

        registry.hookExecutable(ruleId, "cta_request_dialog", requestDialogMethod) { chain ->
            val prev = explicitCtaConsentInProgress.get() == true
            explicitCtaConsentInProgress.set(false)
            try {
                chain.proceed()
            } finally {
                explicitCtaConsentInProgress.set(prev)
            }
        }

        registry.hookExecutable(ruleId, "cta_on_accepted", onCtaAcceptedMethod) { chain ->
            val allow = XiaoAiPrivacyPolicy.shouldAllowCtaAccept(
                config = ConfigBridge.current,
                hasExplicitUserAccept = explicitCtaConsentInProgress.get() == true
            )
            if (!allow) {
                registry.incrementBlocked(ruleId)
                (chain.thisObject as? Activity)?.takeIf { !it.isFinishing }?.finish()
                return@hookExecutable null
            }
            chain.proceed()
        }
    }

    // -------------------------------------------------------------------------
    // Rule 2: RULE_XIAOAI_ONETRACK
    // -------------------------------------------------------------------------

    private fun installOneTrackAndSessionHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_ONETRACK

        val collectorResult = resolver.resolveClass(
            cacheKey = "xiaoai.session_collector",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SESSION_COLLECTOR),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.SESSION_COLLECTOR_STRINGS)
            }
        )
        val collectorClass = collectorResult.value
        val holderField = findSessionHolderField(collectorClass)
        val holderClass = holderField?.type
            ?: resolver.resolveClass(
                cacheKey = "xiaoai.session_holder",
                fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SESSION_HOLDER),
                dexKitFinder = { bridge ->
                    findClassDescriptorByStrings(bridge, XiaoAiAnchors.SESSION_HOLDER_STRINGS)
                }
            ).value
        val sessionAccess = SessionAccessHelper(holderField, holderClass)

        installOneTrackSdkHooks(ruleId, classLoader, resolver, registry, sessionAccess)
        installOneTrackHelperHooks(ruleId, classLoader, resolver, registry, sessionAccess)
        installOneTrackIpcHooks(ruleId, classLoader, resolver, registry, sessionAccess)
        installSessionActionHooks(ruleId, classLoader, resolver, registry, collectorClass, holderClass, sessionAccess)
    }

    private fun installOneTrackSdkHooks(
        ruleId: String,
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        sessionAccess: SessionAccessHelper
    ) {
        val sdkClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_sdk",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_SDK)
        ).value

        val setNetMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.onetrack_set_net",
            targetClass = sdkClass,
            fastPathName = XiaoAiAnchors.METHOD_ONETRACK_SET_NET,
            paramTypes = arrayOf(Context::class.java, Boolean::class.javaPrimitiveType!!),
            dexKitMatcher = null,
            structuralFallback = null
        )
        registry.hookExecutable(ruleId, "onetrack_set_net", setNetMethod) { chain ->
            val cfg = ConfigBridge.current
            XiaoAiOneTrackGuard.syncOneTrackDisabledIfNeeded(cfg, sdkClass)
            if (XiaoAiPrivacyPolicy.shouldForceOneTrackNetworkDisabled(cfg)) {
                val originalArgs = chain.args
                if (originalArgs.size >= 2 && originalArgs[1] == true) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable chain.proceed(arrayOf(originalArgs[0], false))
                }
            }
            chain.proceed()
        }

        val trackMethodNames = if (sdkClass != null) {
            val present = safeDeclaredMethods(sdkClass).mapTo(HashSet()) { it.name }
            val matched = XiaoAiAnchors.ONETRACK_SDK_TRACK_METHODS.filter { it in present }
            if (matched.isEmpty()) listOf("track") else matched
        } else {
            listOf("track")
        }

        for (methodName in trackMethodNames) {
            registry.hookAllMethods(
                ruleId = ruleId,
                hookIdPrefix = "onetrack_sdk_$methodName",
                clazz = sdkClass,
                methodName = methodName
            ) { chain ->
                val isPwd = sessionAccess.isPasswordSession()
                if (XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(ConfigBridge.current, isPwd)) {
                    registry.incrementBlocked(ruleId)
                    return@hookAllMethods null
                }
                chain.proceed()
            }
        }
    }

    private fun installOneTrackHelperHooks(
        ruleId: String,
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        sessionAccess: SessionAccessHelper
    ) {
        val helperClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_helper",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_HELPER),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_HELPER_STRINGS)
            }
        ).value

        val sdkClass = SymbolResolver.loadClassOrNull(XiaoAiAnchors.CLASS_ONETRACK_SDK, classLoader)

        val flushEarlyMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.onetrack_helper_flush",
            targetClass = helperClass,
            fastPathName = "b",
            paramTypes = if (sdkClass != null) arrayOf(sdkClass) else emptyArray(),
            dexKitMatcher = if (sdkClass != null) {
                { cls ->
                    MethodMatcher.create()
                        .declaredClass(cls.name)
                        .paramTypes(sdkClass)
                        .returnType(Void.TYPE)
                }
            } else null,
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0].name == XiaoAiAnchors.CLASS_ONETRACK_SDK &&
                        method.returnType == Void.TYPE
                }
            }
        )

        val trackErrorMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.onetrack_helper_error",
            targetClass = helperClass,
            fastPathName = "f",
            paramTypes = arrayOf(String::class.java, Map::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.ONETRACK_HELPER_ERROR_STRINGS)
                    .paramTypes(String::class.java, Map::class.java)
            },
            structuralFallback = { cls ->
                val methods = safeDeclaredMethods(cls)
                methods.firstOrNull { method ->
                    method.name == "f" &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java, Map::class.java))
                } ?: methods.firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java, Map::class.java)) &&
                        method.name != "e"
                }
            }
        )

        val trackEventMethod = resolver.resolveMethod(
            cacheKey = "xiaoai.onetrack_helper_track",
            fastPath = { cl ->
                val cls = helperClass ?: return@resolveMethod null
                val resolvedCls = if (cls.classLoader === cl) cls else SymbolResolver.loadClassOrNull(cls.name, cl)
                    ?: return@resolveMethod null
                SymbolResolver.findDeclaredMethodOrNull(resolvedCls, "e", String::class.java, Map::class.java)
            },
            dexKitFinder = { bridge ->
                val cls = helperClass ?: return@resolveMethod null
                val errName = trackErrorMethod.value?.name
                bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create()
                            .declaredClass(cls.name)
                            .paramTypes(String::class.java, Map::class.java)
                            .returnType(Void.TYPE)
                    )
                ).firstOrNull { it.name != errName }?.descriptor
            },
            structuralFallback = { _ ->
                helperClass?.let { cls ->
                    safeDeclaredMethods(cls).firstOrNull { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.parameterTypes.contentEquals(arrayOf(String::class.java, Map::class.java)) &&
                            method.name != trackErrorMethod.value?.name
                    }
                }
            }
        )

        registry.hookExecutable(ruleId, "helper_flush_early", flushEarlyMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(ConfigBridge.current, sessionAccess.isPasswordSession())) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        registry.hookExecutable(ruleId, "helper_track_event", trackEventMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(ConfigBridge.current, sessionAccess.isPasswordSession())) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        registry.hookExecutable(ruleId, "helper_track_error", trackErrorMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(ConfigBridge.current, sessionAccess.isPasswordSession())) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }
    }

    private fun installOneTrackIpcHooks(
        ruleId: String,
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        sessionAccess: SessionAccessHelper
    ) {
        val sdkClass = SymbolResolver.loadClassOrNull(XiaoAiAnchors.CLASS_ONETRACK_SDK, classLoader)

        val impClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_imp",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_IMP),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_IMP_STRINGS)
            }
        ).value

        val impInitMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.onetrack_imp_init",
            targetClass = impClass,
            fastPathName = "a",
            paramTypes = arrayOf(Context::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(Context::class.java)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
            }
        )

        registry.hookExecutable(ruleId, "onetrack_imp_init", impInitMethod) { chain ->
            XiaoAiOneTrackGuard.handleOneTrackImpInit(
                config = ConfigBridge.current,
                oneTrackSdkClass = sdkClass,
                proceed = { chain.proceed() },
                incrementBlocked = { registry.incrementBlocked(ruleId) }
            )
        }

        if (impClass != null) {
            val initMethod = impInitMethod.value
            val trackDelegates = safeDeclaredMethods(impClass).filter { method ->
                method != initMethod &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.any { it == String::class.java }
            }
            for ((index, method) in trackDelegates.withIndex()) {
                registry.hookMethod(ruleId, "onetrack_imp_track_$index", method) { chain ->
                    XiaoAiOneTrackGuard.handleOneTrackArMethod(
                        config = ConfigBridge.current,
                        isPasswordSession = sessionAccess.isPasswordSession(),
                        returnType = method.returnType,
                        proceed = { chain.proceed() },
                        incrementBlocked = { registry.incrementBlocked(ruleId) }
                    )
                }
            }
        }

        val sysImpClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_sys_imp",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_SYSTEM_IMP),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_SYSTEM_IMP_STRINGS)
            }
        ).value
        installOneTrackArHooks(
            ruleId = ruleId,
            cachePrefix = "xiaoai.onetrack_sys_imp",
            hookIdPrefix = "onetrack_sys_imp",
            impClass = sysImpClass,
            classLoader = classLoader,
            resolver = resolver,
            registry = registry,
            sessionAccess = sessionAccess
        )

        val localImpClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_local_imp",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_LOCAL_IMP),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_LOCAL_IMP_STRINGS)
            }
        ).value
        installOneTrackArHooks(
            ruleId = ruleId,
            cachePrefix = "xiaoai.onetrack_local_imp",
            hookIdPrefix = "onetrack_local_imp",
            impClass = localImpClass,
            classLoader = classLoader,
            resolver = resolver,
            registry = registry,
            sessionAccess = sessionAccess
        )

        val serviceConnClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_service_conn",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_SERVICE_CONN),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_SERVICE_CONN_STRINGS)
            }
        ).value
        val threeStrings: Array<Class<*>> = Array(3) { String::class.java }
        val sevenStrings: Array<Class<*>> = Array(7) { String::class.java }
        val ipcThreeMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.onetrack_ipc_3",
            targetClass = serviceConnClass,
            fastPathName = "a",
            paramTypes = threeStrings,
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(*threeStrings)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(threeStrings)
                }
            }
        )
        val ipcSevenAMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.onetrack_ipc_7a",
            targetClass = serviceConnClass,
            fastPathName = "a",
            paramTypes = sevenStrings,
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(*sevenStrings)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(sevenStrings)
                }
            }
        )
        val ipcSevenBMethod = resolver.resolveMethod(
            cacheKey = "xiaoai.onetrack_ipc_7b",
            fastPath = { cl ->
                val cls = serviceConnClass ?: return@resolveMethod null
                val resolvedCls = if (cls.classLoader === cl) cls else SymbolResolver.loadClassOrNull(cls.name, cl)
                    ?: return@resolveMethod null
                SymbolResolver.findDeclaredMethodOrNull(resolvedCls, "b", *sevenStrings)
            },
            dexKitFinder = { bridge ->
                val cls = serviceConnClass ?: return@resolveMethod null
                val sevenAName = ipcSevenAMethod.value?.name
                bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create()
                            .declaredClass(cls.name)
                            .paramTypes(*sevenStrings)
                    )
                ).firstOrNull { it.name != sevenAName }?.descriptor
            },
            structuralFallback = { _ ->
                val cls = serviceConnClass ?: return@resolveMethod null
                val sevenAName = ipcSevenAMethod.value?.name
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(sevenStrings) &&
                        method.name != sevenAName
                }
            }
        )
        for ((hookId, methodRes) in listOf(
            "onetrack_ipc_3" to ipcThreeMethod,
            "onetrack_ipc_7a" to ipcSevenAMethod,
            "onetrack_ipc_7b" to ipcSevenBMethod
        )) {
            registry.hookExecutable(ruleId, hookId, methodRes) { chain ->
                val returnType = methodRes.value?.returnType ?: Boolean::class.javaPrimitiveType!!
                XiaoAiOneTrackGuard.handleOneTrackArMethod(
                    config = ConfigBridge.current,
                    isPasswordSession = sessionAccess.isPasswordSession(),
                    returnType = returnType,
                    proceed = { chain.proceed() },
                    incrementBlocked = { registry.incrementBlocked(ruleId) }
                )
            }
        }
    }

    private fun installOneTrackArHooks(
        ruleId: String,
        cachePrefix: String,
        hookIdPrefix: String,
        impClass: Class<*>?,
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        sessionAccess: SessionAccessHelper
    ) {
        val lifecycleMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "$cachePrefix.lifecycle",
            targetClass = impClass,
            fastPathName = "a",
            paramTypes = arrayOf(Int::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(Int::class.javaPrimitiveType!!)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        Modifier.isPublic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType!!))
                }
            }
        )

        val trackAMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "$cachePrefix.track_a",
            targetClass = impClass,
            fastPathName = "a",
            paramTypes = arrayOf(String::class.java, String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(String::class.java, String::class.java)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        Modifier.isPublic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
                }
            }
        )

        val trackBMethod = resolver.resolveMethod(
            cacheKey = "$cachePrefix.track_b",
            fastPath = { cl ->
                val cls = impClass ?: return@resolveMethod null
                val resolvedCls = if (cls.classLoader === cl) cls else SymbolResolver.loadClassOrNull(cls.name, cl)
                    ?: return@resolveMethod null
                SymbolResolver.findDeclaredMethodOrNull(resolvedCls, "b", String::class.java, String::class.java)
            },
            dexKitFinder = { bridge ->
                val cls = impClass ?: return@resolveMethod null
                val trackAName = trackAMethod.value?.name
                bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create()
                            .declaredClass(cls.name)
                            .paramTypes(String::class.java, String::class.java)
                            .returnType(Void.TYPE)
                    )
                ).firstOrNull { it.name != trackAName }?.descriptor
            },
            structuralFallback = { _ ->
                val cls = impClass ?: return@resolveMethod null
                val trackAName = trackAMethod.value?.name
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        Modifier.isPublic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java)) &&
                        method.name != trackAName
                }
            }
        )

        val flushFlagMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "$cachePrefix.flush",
            targetClass = impClass,
            fastPathName = "a",
            paramTypes = arrayOf(Boolean::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(Boolean::class.javaPrimitiveType!!)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        Modifier.isPublic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType!!))
                }
            }
        )

        for ((suffix, methodRes) in listOf(
            "lifecycle" to lifecycleMethod,
            "track_a" to trackAMethod,
            "track_b" to trackBMethod,
            "flush" to flushFlagMethod
        )) {
            registry.hookExecutable(ruleId, "${hookIdPrefix}_$suffix", methodRes) { chain ->
                val returnType = methodRes.value?.returnType ?: Void.TYPE
                XiaoAiOneTrackGuard.handleOneTrackArMethod(
                    config = ConfigBridge.current,
                    isPasswordSession = sessionAccess.isPasswordSession(),
                    returnType = returnType,
                    proceed = { chain.proceed() },
                    incrementBlocked = { registry.incrementBlocked(ruleId) }
                )
            }
        }
    }

    private fun installSessionActionHooks(
        ruleId: String,
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        collectorClass: Class<*>?,
        holderClass: Class<*>?,
        sessionAccess: SessionAccessHelper
    ) {
        val holderSetActiveMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.session_holder_active",
            targetClass = holderClass,
            fastPathName = "a",
            paramTypes = arrayOf(Boolean::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(Boolean::class.javaPrimitiveType!!)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType!!)) &&
                        method.returnType == Void.TYPE
                }
            }
        )

        val buildMapMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.session_build_map",
            targetClass = collectorClass,
            fastPathName = "a",
            paramTypes = arrayOf(List::class.java, Int::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(List::class.java, Int::class.javaPrimitiveType!!)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(List::class.java, Int::class.javaPrimitiveType!!))
                }
            }
        )

        val backActionMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.session_action_back",
            targetClass = collectorClass,
            fastPathName = "b",
            paramTypes = arrayOf(String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.SESSION_ACTION_BACK_STRINGS)
                    .paramTypes(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    method.name == "b" &&
                        Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
            }
        )

        val delActionMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.session_action_del",
            targetClass = collectorClass,
            fastPathName = "c",
            paramTypes = arrayOf(String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.SESSION_ACTION_DEL_STRINGS)
                    .paramTypes(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    method.name == "c" &&
                        Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
            }
        )

        val otherActionMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.session_action_d",
            targetClass = collectorClass,
            fastPathName = "d",
            paramTypes = arrayOf(String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.SESSION_ACTION_OTHER_STRINGS)
                    .paramTypes(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                        method.name != backActionMethod.value?.name &&
                        method.name != delActionMethod.value?.name
                }
            }
        )

        val emitSessionMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.session_emit",
            targetClass = collectorClass,
            fastPathName = "e",
            paramTypes = arrayOf(Long::class.javaPrimitiveType!!, String::class.java, Map::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(Long::class.javaPrimitiveType!!, String::class.java, Map::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(
                            arrayOf(Long::class.javaPrimitiveType!!, String::class.java, Map::class.java)
                        )
                }
            }
        )

        val cacheClass = resolver.resolveClass(
            cacheKey = "xiaoai.session_cache",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SESSION_CACHE),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.SESSION_CACHE_STRINGS)
            }
        ).value
        val cacheReportMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.session_cache_report",
            targetClass = cacheClass,
            fastPathName = "b",
            paramTypes = arrayOf(String::class.java, Map::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.SESSION_CACHE_STRINGS)
                    .paramTypes(String::class.java, Map::class.java)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java, Map::class.java))
                }
            }
        )

        registry.hookExecutable(ruleId, "session_holder_active", holderSetActiveMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockSessionCounters(ConfigBridge.current, sessionAccess.isPasswordSession())) {
                val res = chain.proceed(arrayOf(false))
                sessionAccess.zeroCounters(chain.thisObject)
                return@hookExecutable res
            }
            chain.proceed()
        }

        registry.hookExecutable(ruleId, "session_build_map", buildMapMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockSessionCounters(ConfigBridge.current, sessionAccess.isPasswordSession())) {
                sessionAccess.zeroCounters()
                registry.incrementBlocked(ruleId)
                return@hookExecutable LinkedHashMap<Any?, Any?>()
            }
            chain.proceed()
        }

        for ((hookId, methodRes) in listOf(
            "session_action_back" to backActionMethod,
            "session_action_del" to delActionMethod,
            "session_action_other" to otherActionMethod,
            "session_emit" to emitSessionMethod
        )) {
            registry.hookExecutable(ruleId, hookId, methodRes) { chain ->
                if (XiaoAiPrivacyPolicy.shouldBlockSessionCounters(ConfigBridge.current, sessionAccess.isPasswordSession())) {
                    if (hookId == "session_emit") {
                        sessionAccess.zeroCounters()
                    }
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }

        if (cacheReportMethod.isSuccess) {
            registry.hookExecutable(
                ruleId = ruleId,
                hookId = "session_cache_report",
                resolveResult = cacheReportMethod,
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                if (XiaoAiPrivacyPolicy.shouldBlockSessionCounters(ConfigBridge.current, sessionAccess.isPasswordSession())) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rule 3: RULE_XIAOAI_AI_CLOUD_IPC
    // -------------------------------------------------------------------------

    private fun installAiCloudAndIpcHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_AI_CLOUD_IPC

        // 3a. VoiceAssist ExternalAgent bind (k8.o#a)
        val agentConnClass = resolver.resolveClass(
            cacheKey = "xiaoai.agent_conn",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_AGENT_CONNECTION),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.AGENT_CONNECTION_STRINGS)
            }
        ).value
        val agentBindMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.agent_conn_bind",
            targetClass = agentConnClass,
            fastPathName = "a",
            paramTypes = if (agentConnClass != null) arrayOf(agentConnClass) else emptyArray(),
            dexKitMatcher = if (agentConnClass != null) {
                { cls ->
                    MethodMatcher.create()
                        .declaredClass(cls.name)
                        .usingStrings(XiaoAiAnchors.AGENT_BIND_STRINGS)
                        .paramTypes(cls)
                        .returnType(Void.TYPE)
                }
            } else null,
            structuralFallback = { cls ->
                val methods = safeDeclaredMethods(cls)
                methods.firstOrNull { method ->
                    method.name == "a" &&
                        Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == cls &&
                        method.returnType == Void.TYPE
                } ?: methods.firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == cls &&
                        method.returnType == Void.TYPE
                }
            }
        )
        registry.hookExecutable(ruleId, "voiceassist_bind", agentBindMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(ConfigBridge.current)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        // 3b. VoiceAssist ACTION_ASSIST launcher (rh.b#L)
        val assistLaunchClass = resolver.resolveClass(
            cacheKey = "xiaoai.voiceassist_launcher_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_VOICEASSIST_LAUNCH),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.VOICEASSIST_ASSIST_STRINGS)
            }
        ).value
        val assistLaunchMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.voiceassist_launch",
            targetClass = assistLaunchClass,
            fastPathName = "L",
            paramTypes = arrayOf(Context::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.VOICEASSIST_ASSIST_STRINGS)
                    .paramTypes(Context::class.java)
                    .returnType(Boolean::class.javaPrimitiveType!!)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    method.name == "L" &&
                        Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
            }
        )
        registry.hookExecutable(ruleId, "voiceassist_assist_intent", assistLaunchMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(ConfigBridge.current)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable defaultReturnValue(
                    assistLaunchMethod.value?.returnType ?: Boolean::class.javaPrimitiveType!!
                )
            }
            chain.proceed()
        }

        // 3c. MiclawAgentManager#q (submitText & prompt/screenshot attachment sanitization)
        val miclawClass = resolver.resolveClass(
            cacheKey = "xiaoai.miclaw_manager",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_MICLAW_MANAGER),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.MICLAW_MANAGER_STRINGS)
            }
        ).value
        val submitTextMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.miclaw_submit_text",
            targetClass = miclawClass,
            fastPathName = "q",
            paramTypes = arrayOf(
                String::class.java,
                String::class.java,
                String::class.java,
                List::class.java
            ),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(
                        String::class.java,
                        String::class.java,
                        String::class.java,
                        List::class.java
                    )
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.returnType != Void.TYPE &&
                        method.parameterTypes.contentEquals(
                            arrayOf(
                                String::class.java,
                                String::class.java,
                                String::class.java,
                                List::class.java
                            )
                        )
                }
            }
        )
        val miclawFailedClass = resolver.resolveClass(
            cacheKey = "xiaoai.miclaw_failed",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_MICLAW_FAILED),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.MICLAW_FAILED_STRINGS)
            }
        ).value
        val miclawHelper = MiclawFailureHelper(
            managerClass = miclawClass,
            sessionClass = submitTextMethod.value?.returnType
                ?: SymbolResolver.loadClassOrNull(XiaoAiAnchors.CLASS_MICLAW_SESSION, classLoader),
            failedClass = miclawFailedClass
        )
        registry.hookExecutable(ruleId, "miclaw_submit_text", submitTextMethod) { chain ->
            val cfg = ConfigBridge.current
            val text = chain.getArg(0) as? String ?: ""
            val tts = chain.getArg(1) as? String
            val featureId = chain.getArg(2) as? String
            val attachments = chain.getArg(3) as? List<*>

            if (XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(cfg)) {
                val failedSession = miclawHelper.buildFailedSession(chain.thisObject, text, tts, featureId)
                if (failedSession != null) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable failedSession
                }
                return@hookExecutable chain.proceed()
            }

            if (XiaoAiPrivacyPolicy.shouldSanitizeVoiceAssistPayload(cfg)) {
                val sanitizedText = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(text, cfg)
                val stripAttachments = cfg.sanitizeCloudPayload && !attachments.isNullOrEmpty()
                if (sanitizedText != text || stripAttachments) {
                    registry.incrementBlocked(ruleId)
                    val modifiedArgs = chain.args.toTypedArray()
                    modifiedArgs[0] = sanitizedText
                    if (stripAttachments && modifiedArgs.size >= 4) {
                        modifiedArgs[3] = emptyList<Any?>()
                    }
                    return@hookExecutable chain.proceed(modifiedArgs)
                }
            }

            chain.proceed()
        }

        // 3d. ScreenshotCapture#c (eb.r)
        val screenshotClass = resolver.resolveClass(
            cacheKey = "xiaoai.screenshot_capture",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SCREENSHOT_CAPTURE),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.SCREENSHOT_CAPTURE_STRINGS)
            }
        ).value
        val captureMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.screenshot_capture_call",
            targetClass = screenshotClass,
            fastPathName = "c",
            paramTypes = emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(listOf("android.window.ScreenCapture"))
                    .paramCount(2)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        method.returnType == Void.TYPE
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull { method ->
                method.name == name &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.returnType == Void.TYPE
            }
        }
        registry.hookExecutable(ruleId, "screenshot_capture", captureMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldStripScreenCapture(ConfigBridge.current)) {
                val callbackWrapper = chain.getArg(1)
                if (completeScreenshotCallbackWithNull(callbackWrapper)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
            }
            chain.proceed()
        }

        // 3e. TranslateEngine#f (nc.r -> ai.engine.miui.com)
        val translateClass = resolver.resolveClass(
            cacheKey = "xiaoai.translate_engine",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TRANSLATE_ENGINE),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.TRANSLATE_ENGINE_STRINGS)
            }
        ).value
        val translateStartClass = resolver.resolveClass(
            cacheKey = "xiaoai.translate_start_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TRANSLATE_START),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.TRANSLATE_START_STRINGS)
            }
        ).value
        val translateMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.translate_start",
            targetClass = translateClass,
            fastPathName = "f",
            paramTypes = if (translateStartClass != null) arrayOf(translateStartClass) else emptyArray(),
            dexKitMatcher = if (translateStartClass != null) {
                { cls ->
                    MethodMatcher.create()
                        .declaredClass(cls.name)
                        .paramTypes(translateStartClass)
                }
            } else null,
            structuralFallback = { cls ->
                val methods = safeDeclaredMethods(cls)
                methods.firstOrNull { method ->
                    method.name == "f" &&
                        !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 1 &&
                        method.returnType != Void.TYPE
                } ?: methods.firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 1 &&
                        translateStartClass != null &&
                        method.parameterTypes[0] == translateStartClass &&
                        method.returnType != Void.TYPE
                }
            }
        )
        val translateStateInterface = translateMethod.value?.returnType
        val translateIdleClass = resolver.resolveClass(
            cacheKey = "xiaoai.translate_idle_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TRANSLATE_IDLE),
            dexKitFinder = { bridge ->
                val matcher = ClassMatcher.create().usingStrings(XiaoAiAnchors.TRANSLATE_IDLE_STRINGS)
                if (translateStateInterface != null && translateStateInterface.isInterface) {
                    matcher.addInterface(translateStateInterface.name)
                }
                bridge.findClass(FindClass.create().matcher(matcher)).firstOrNull()?.descriptor
            }
        ).value
        val translateErrorClass = resolver.resolveClass(
            cacheKey = "xiaoai.translate_error_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TRANSLATE_ERROR),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.TRANSLATE_ERROR_STRINGS)
            }
        ).value
        val translateHelper = TranslateBlockHelper(translateIdleClass, translateErrorClass)
        registry.hookExecutable(ruleId, "translate_engine", translateMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(ConfigBridge.current)) {
                val idleState = translateHelper.failAndReturnIdle(chain.getArg(0))
                if (idleState != null) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable idleState
                }
            }
            chain.proceed()
        }

        // 3f. SpeechAsrManager#b (m8.h -> com.xiaomi.speech)
        val speechClass = resolver.resolveClass(
            cacheKey = "xiaoai.speech_asr",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SPEECH_ASR_MANAGER),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.SPEECH_ASR_STRINGS)
            }
        ).value
        val speechPrepareMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.speech_asr_prepare",
            targetClass = speechClass,
            fastPathName = "b",
            paramTypes = if (speechClass != null) {
                arrayOf(
                    speechClass,
                    Int::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                    String::class.java
                )
            } else emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(listOf("hotwordsJson"))
                    .paramTypes(
                        cls,
                        Int::class.javaPrimitiveType!!,
                        Boolean::class.javaPrimitiveType!!,
                        String::class.java
                    )
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(
                            arrayOf(
                                cls,
                                Int::class.javaPrimitiveType!!,
                                Boolean::class.javaPrimitiveType!!,
                                String::class.java
                            )
                        )
                }
            }
        )
        registry.hookExecutable(ruleId, "speech_asr_prepare", speechPrepareMethod) { chain ->
            val cfg = ConfigBridge.current
            if (XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(cfg)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            if (XiaoAiPrivacyPolicy.shouldMaskSpeechHostPackage(cfg)) {
                val args = chain.args
                if (args.size >= 4) {
                    val hostApp = args[3] as? String
                    if (!hostApp.isNullOrEmpty()) {
                        registry.incrementBlocked(ruleId)
                        val modified = args.toTypedArray()
                        modified[3] = ""
                        return@hookExecutable chain.proceed(modified)
                    }
                }
            }
            chain.proceed()
        }

        // 3g. Taiyi AIService connection & execution (rc.j#d & rc.b#e)
        val taiyiServiceClass = resolver.resolveClass(
            cacheKey = "xiaoai.taiyi_service",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TAIYI_SERVICE),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.TAIYI_SERVICE_STRINGS)
            }
        ).value
        val taiyiConnectMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.taiyi_connect",
            targetClass = taiyiServiceClass,
            fastPathName = "d",
            paramTypes = emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.TAIYI_CONNECT_STRINGS)
                    .paramCount(2)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0] == Context::class.java
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull { method ->
                method.name == name &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[0] == Context::class.java
            }
        }
        registry.hookExecutable(ruleId, "taiyi_aiservice_connect", taiyiConnectMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(ConfigBridge.current)) {
                notifyConnectCallbackFailed(chain.getArg(1), XiaoAiAnchors.TAIYI_BLOCKED_CODE, "Cloud AI blocked")
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        val taiyiManagerClass = resolver.resolveClass(
            cacheKey = "xiaoai.taiyi_manager",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TAIYI_MANAGER),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.TAIYI_MANAGER_STRINGS)
            }
        ).value
        val aiRequestClass = SymbolResolver.loadClassOrNull(XiaoAiAnchors.CLASS_TAIYI_REQUEST, classLoader)
        val taiyiExecMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.taiyi_exec",
            targetClass = taiyiManagerClass,
            fastPathName = "e",
            paramTypes = if (aiRequestClass != null) arrayOf(aiRequestClass) else emptyArray(),
            dexKitMatcher = if (aiRequestClass != null) {
                { cls ->
                    MethodMatcher.create()
                        .declaredClass(cls.name)
                        .paramTypes(aiRequestClass)
                }
            } else null,
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0].name == XiaoAiAnchors.CLASS_TAIYI_REQUEST &&
                        method.returnType.name == XiaoAiAnchors.CLASS_TAIYI_RESPONSE
                }
            }
        )
        val aiResponseHelper = TaiyiErrorResponseHelper(classLoader)
        registry.hookExecutable(ruleId, "taiyi_aiservice_exec", taiyiExecMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(ConfigBridge.current)) {
                val errResp = aiResponseHelper.createBlockedResponse(chain.getArg(0), XiaoAiAnchors.TAIYI_BLOCKED_CODE)
                if (errResp != null) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable errResp
                }
            }
            chain.proceed()
        }
    }

    // -------------------------------------------------------------------------
    // Rule 4: RULE_XIAOAI_CLIPBOARD
    // -------------------------------------------------------------------------

    private fun installClipboardPrivacyHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CLIPBOARD
        val clipboardMonitorClass = resolver.resolveClass(
            cacheKey = "xiaoai.clipboard_monitor",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_CLIPBOARD_MONITOR),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.CLIPBOARD_MONITOR_STRINGS)
            }
        ).value

        val onClipChangedMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.clipboard_monitor_changed",
            targetClass = clipboardMonitorClass,
            fastPathName = "c",
            paramTypes = emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(listOf("sensitive clip ignored"))
                    .paramCount(0)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 0 &&
                        method.returnType == Void.TYPE &&
                        method.name == "c"
                }
            }
        )

        registry.hookExecutable(ruleId, "clipboard_monitor_changed", onClipChangedMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockBackgroundClipboardRead(ConfigBridge.current)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }
    }

    // -------------------------------------------------------------------------
    // Rule 5: RULE_XIAOAI_HOTWORD_TRACKER
    // -------------------------------------------------------------------------

    private fun installHotwordTrackerHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_HOTWORD_TRACKER

        val hotwordClientClass = resolver.resolveClass(
            cacheKey = "xiaoai.hotword_client",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_HOTWORD_CLIENT),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.HOTWORD_CLIENT_STRINGS)
            }
        ).value

        val hotwordInitMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.hotword_client_init",
            targetClass = hotwordClientClass,
            fastPathName = "a",
            paramTypes = arrayOf(Context::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(Context::class.java)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(Context::class.java)) &&
                        method.returnType == Void.TYPE
                }
            }
        )

        val hotwordBinderMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.hotword_client_binder",
            targetClass = hotwordClientClass,
            fastPathName = "b",
            paramTypes = arrayOf(String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                        method.returnType != Void.TYPE
                }
            }
        )

        val deferredHotwordContext = AtomicReference<WeakReference<Context>?>(null)

        registry.hookExecutable(ruleId, "hotword_client_init", hotwordInitMethod) { chain ->
            val ctx = chain.getArg(0) as? Context
            if (XiaoAiPrivacyPolicy.shouldBlockHotwordTracker(ConfigBridge.current)) {
                if (ctx != null) {
                    deferredHotwordContext.set(WeakReference(ctx.applicationContext ?: ctx))
                }
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            deferredHotwordContext.set(null)
            chain.proceed()
        }

        registry.hookExecutable(ruleId, "hotword_client_binder", hotwordBinderMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockHotwordTracker(ConfigBridge.current)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            val pendingCtx = deferredHotwordContext.getAndSet(null)?.get()
            if (pendingCtx != null) {
                val initMethod = hotwordInitMethod.value
                if (initMethod != null) {
                    runCatching {
                        initMethod.isAccessible = true
                        initMethod.invoke(null, pendingCtx)
                    }
                }
            }
            chain.proceed()
        }

        val editTrackerClass = resolver.resolveClass(
            cacheKey = "xiaoai.hotword_edit_tracker",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_HOTWORD_EDIT_TRACKER),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, XiaoAiAnchors.HOTWORD_TRACKER_STRINGS)
            }
        ).value

        val trackerBindEditorMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.hotword_tracker_editor",
            targetClass = editTrackerClass,
            fastPathName = "a",
            paramTypes = arrayOf(EditorInfo::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(EditorInfo::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(EditorInfo::class.java))
                }
            }
        )

        val trackerSelectionMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.hotword_tracker_selection",
            targetClass = editTrackerClass,
            fastPathName = "f",
            paramTypes = arrayOf(Int::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(Int::class.javaPrimitiveType!!)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.name == "f" &&
                        method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType!!))
                }
            }
        )

        val trackerSnapshotMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.hotword_tracker_snapshot",
            targetClass = editTrackerClass,
            fastPathName = "h",
            paramTypes = emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramCount(0)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.name == "h" &&
                        method.parameterCount == 0 &&
                        method.returnType == Void.TYPE
                }
            }
        )

        for ((hookId, methodRes) in listOf(
            "hotword_tracker_bind_editor" to trackerBindEditorMethod,
            "hotword_tracker_selection" to trackerSelectionMethod,
            "hotword_tracker_snapshot" to trackerSnapshotMethod
        )) {
            registry.hookExecutable(ruleId, hookId, methodRes) { chain ->
                if (XiaoAiPrivacyPolicy.shouldBlockHotwordTracker(ConfigBridge.current)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared Symbol Resolution & Reflection Utilities
    // -------------------------------------------------------------------------

    private inline fun runSafely(registry: HookRegistry, label: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            registry.logWarn("XiaoAiHookPack phase failed [$label]: ${t.message}", t)
        }
    }

    private fun findClassDescriptorByStrings(bridge: DexKitBridge, strings: List<String>): String? =
        bridge.findClass(
            FindClass.create().matcher(ClassMatcher.create().usingStrings(strings))
        ).firstOrNull()?.descriptor

    private fun resolveMethodOnClass(
        resolver: SymbolResolver,
        classLoader: ClassLoader,
        cacheKey: String,
        targetClass: Class<*>?,
        fastPathName: String,
        paramTypes: Array<Class<*>>,
        dexKitMatcher: ((Class<*>) -> MethodMatcher)?,
        structuralFallback: ((Class<*>) -> Method?)?,
        customFastPath: ((Class<*>, String) -> Method?)? = null
    ): ResolveResult<Method> {
        return resolver.resolveMethod(
            cacheKey = cacheKey,
            fastPath = { cl ->
                val cls = targetClass ?: return@resolveMethod null
                val resolvedCls = if (cls.classLoader === cl) cls else SymbolResolver.loadClassOrNull(cls.name, cl)
                    ?: return@resolveMethod null
                customFastPath?.invoke(resolvedCls, fastPathName)
                    ?: SymbolResolver.findDeclaredMethodOrNull(resolvedCls, fastPathName, *paramTypes)
            },
            dexKitFinder = dexKitMatcher?.let { matcherFactory ->
                finder@{ bridge ->
                    val cls = targetClass ?: return@finder null
                    bridge.findMethod(FindMethod.create().matcher(matcherFactory(cls)))
                        .firstOrNull()?.descriptor
                }
            },
            structuralFallback = structuralFallback?.let { predicate ->
                fallback@{ _ ->
                    val cls = targetClass ?: return@fallback null
                    predicate(cls)
                }
            }
        )
    }

    private fun safeDeclaredMethods(clazz: Class<*>): List<Method> =
        XiaoAiOneTrackGuard.safeDeclaredMethods(clazz)

    private fun safeDeclaredFields(clazz: Class<*>): List<Field> =
        XiaoAiOneTrackGuard.safeDeclaredFields(clazz)

    private fun findSessionHolderField(collectorClass: Class<*>?): Field? {
        collectorClass ?: return null
        val named = runCatching { collectorClass.getDeclaredField(XiaoAiAnchors.FIELD_SESSION_HOLDER) }.getOrNull()
        if (named != null && Modifier.isStatic(named.modifiers) && !named.type.isPrimitive && named.type != String::class.java) {
            named.isAccessible = true
            return named
        }
        val targetPkg = collectorClass.name.substringBeforeLast('.', "")
        return safeDeclaredFields(collectorClass).firstOrNull { field ->
            Modifier.isStatic(field.modifiers) &&
                !field.type.isPrimitive &&
                field.type.name.substringBeforeLast('.', "") == targetPkg
        }?.apply { isAccessible = true }
    }

    private fun defaultReturnValue(returnType: Class<*>): Any? =
        XiaoAiOneTrackGuard.defaultReturnValue(returnType)

    private fun completeScreenshotCallbackWithNull(callbackWrapper: Any?): Boolean {
        callbackWrapper ?: return false
        return runCatching {
            val contField = runCatching { callbackWrapper.javaClass.getDeclaredField("b") }.getOrNull()
                ?: safeDeclaredFields(callbackWrapper.javaClass).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.type != Int::class.javaPrimitiveType
                }
                ?: return false
            contField.isAccessible = true
            val cont = contField.get(callbackWrapper) ?: return false
            val resumeMethod = runCatching {
                cont.javaClass.getDeclaredMethod("p", Throwable::class.java)
            }.getOrNull() ?: safeDeclaredMethods(cont.javaClass).firstOrNull { method ->
                method.name == "p" && method.parameterCount == 1
            } ?: return false
            resumeMethod.isAccessible = true
            resumeMethod.invoke(cont, null)
            true
        }.getOrDefault(false)
    }

    private fun notifyConnectCallbackFailed(callback: Any?, code: Int, reason: String) {
        callback ?: return
        runCatching {
            val method = callback.javaClass.getMethod(
                "onConnectFailed",
                Int::class.javaPrimitiveType!!,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(callback, code, reason)
        }
    }

    private class SessionAccessHelper(
        private val holderField: Field?,
        holderClass: Class<*>?
    ) {
        private val passwordField: Field? = holderClass?.let { cls ->
            runCatching {
                cls.getDeclaredField(XiaoAiAnchors.FIELD_SESSION_PASSWORD).takeIf {
                    it.type == Boolean::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers)
                }?.apply { isAccessible = true }
            }.getOrNull()
        }

        private val holderFields: List<Field> = holderClass?.let { safeDeclaredFields(it) } ?: emptyList()

        private val mutableIntFields: List<Field> = holderFields.filter {
            !Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) && it.type == Int::class.javaPrimitiveType
        }.onEach { it.isAccessible = true }

        private val mutableLongFields: List<Field> = holderFields.filter {
            !Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) && it.type == Long::class.javaPrimitiveType
        }.onEach { it.isAccessible = true }

        private val collectionFields: List<Field> = holderFields.filter {
            !Modifier.isStatic(it.modifiers) && MutableCollection::class.java.isAssignableFrom(it.type)
        }.onEach { it.isAccessible = true }

        fun isPasswordSession(): Boolean {
            val field = passwordField ?: return false
            val holder = currentHolder() ?: return false
            return runCatching { field.getBoolean(holder) }.getOrDefault(false)
        }

        fun zeroCounters(explicitHolder: Any? = null) {
            val holder = explicitHolder ?: currentHolder() ?: return
            for (field in collectionFields) {
                runCatching { (field.get(holder) as? MutableCollection<*>)?.clear() }
            }
            for (field in mutableIntFields) {
                runCatching { field.setInt(holder, 0) }
            }
            for (field in mutableLongFields) {
                runCatching { field.setLong(holder, 0L) }
            }
        }

        private fun currentHolder(): Any? = holderField?.let {
            runCatching { it.get(null) }.getOrNull()
        }
    }

    private class MiclawFailureHelper(
        managerClass: Class<*>?,
        sessionClass: Class<*>?,
        failedClass: Class<*>?
    ) {
        private val sessionConstructor: Constructor<*>? = if (managerClass != null && sessionClass != null) {
            runCatching {
                sessionClass.getDeclaredConstructor(
                    managerClass,
                    Long::class.javaPrimitiveType!!,
                    Long::class.javaPrimitiveType!!,
                    String::class.java,
                    List::class.java,
                    String::class.java,
                    String::class.java
                ).apply { isAccessible = true }
            }.getOrNull()
        } else null

        private val failedConstructor: Constructor<*>? = failedClass?.let { cls ->
            runCatching {
                cls.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
            }.getOrNull()
        }

        private val setStateMethod: Method? = sessionClass?.let { cls ->
            val methods = safeDeclaredMethods(cls)
            methods.firstOrNull { method ->
                method.name == "b" && method.parameterCount == 1 &&
                    (failedClass == null || method.parameterTypes[0].isAssignableFrom(failedClass))
            } ?: if (failedClass != null) {
                methods.firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.returnType == Void.TYPE &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] != Any::class.java &&
                        method.parameterTypes[0].isAssignableFrom(failedClass)
                }
            } else null
        }?.apply { isAccessible = true }

        fun buildFailedSession(
            manager: Any?,
            text: String,
            tts: String?,
            featureId: String?
        ): Any? {
            val ctor = sessionConstructor
            if (manager == null || ctor == null) return null
            return runCatching {
                val session = ctor.newInstance(manager, 0L, 0L, text, null, tts, featureId)
                val failedObj = failedConstructor?.newInstance("Cloud input disabled")
                if (failedObj != null) {
                    setStateMethod?.invoke(session, failedObj)
                }
                session
            }.getOrNull()
        }
    }

    private class TranslateBlockHelper(
        idleClass: Class<*>?,
        errorClass: Class<*>?
    ) {
        private val idleInstance: Any? = runCatching {
            val singletonField = idleClass?.let { cls ->
                runCatching { cls.getDeclaredField("a") }.getOrNull()?.takeIf {
                    Modifier.isStatic(it.modifiers) && it.type == cls
                } ?: safeDeclaredFields(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.type == cls
                }
            }
            singletonField?.apply { isAccessible = true }?.get(null)
        }.getOrNull()

        private val errorConstructor: Constructor<*>? = runCatching {
            errorClass?.getDeclaredConstructor(
                Int::class.javaPrimitiveType!!,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType!!
            )?.apply { isAccessible = true }
        }.getOrNull()

        fun failAndReturnIdle(startRequest: Any?): Any? {
            val idle = idleInstance ?: return null
            if (startRequest != null) {
                runCatching {
                    val errorArg = errorConstructor?.newInstance(403, "Cloud translation disabled", null, false)
                        ?: return@runCatching
                    val callbackField = runCatching { startRequest.javaClass.getDeclaredField("c") }.getOrNull()
                        ?: safeDeclaredFields(startRequest.javaClass).firstOrNull { field ->
                            !Modifier.isStatic(field.modifiers) &&
                                field.type != String::class.java &&
                                !field.type.isPrimitive &&
                                safeDeclaredMethods(field.type).any { method ->
                                    method.parameterCount == 1 &&
                                        method.parameterTypes[0].isAssignableFrom(errorArg.javaClass)
                                }
                        }
                        ?: return@runCatching
                    callbackField.isAccessible = true
                    val callback = callbackField.get(startRequest) ?: return@runCatching
                    val notifyMethod = runCatching {
                        callback.javaClass.getDeclaredMethod("b", errorArg.javaClass)
                    }.getOrNull() ?: safeDeclaredMethods(callback.javaClass).firstOrNull { method ->
                        method.name == "b" &&
                            method.parameterCount == 1 &&
                            method.parameterTypes[0].isAssignableFrom(errorArg.javaClass)
                    } ?: safeDeclaredMethods(callback.javaClass).firstOrNull { method ->
                        !Modifier.isStatic(method.modifiers) &&
                            method.returnType == Void.TYPE &&
                            method.parameterCount == 1 &&
                            method.parameterTypes[0].isAssignableFrom(errorArg.javaClass)
                    } ?: return@runCatching
                    notifyMethod.isAccessible = true
                    notifyMethod.invoke(callback, errorArg)
                }
            }
            return idle
        }
    }

    private class TaiyiErrorResponseHelper(classLoader: ClassLoader) {
        private val responseClass: Class<*>? =
            SymbolResolver.loadClassOrNull(XiaoAiAnchors.CLASS_TAIYI_RESPONSE, classLoader)
        private val requestClass: Class<*>? =
            SymbolResolver.loadClassOrNull(XiaoAiAnchors.CLASS_TAIYI_REQUEST, classLoader)

        private val createErrorMethod: Method? = run {
            val respCls = responseClass
            val reqCls = requestClass
            if (respCls != null && reqCls != null) {
                runCatching {
                    respCls.getDeclaredMethod("create", reqCls, Int::class.javaPrimitiveType!!)
                        .apply { isAccessible = true }
                }.getOrNull()
            } else null
        }

        fun createBlockedResponse(request: Any?, errorCode: Int): Any? {
            val method = createErrorMethod
            if (request == null || method == null) return null
            return runCatching { method.invoke(null, request, errorCode) }.getOrNull()
        }
    }
}
