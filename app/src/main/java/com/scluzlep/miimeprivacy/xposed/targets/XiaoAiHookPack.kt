package com.scluzlep.miimeprivacy.xposed.targets

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.view.inputmethod.EditorInfo
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.DexKitExecutor
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ImeHookPack
import com.scluzlep.miimeprivacy.xposed.ImePackInstallResult
import com.scluzlep.miimeprivacy.xposed.ResolveResult
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.io.IOException
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Verified class/method names and DexKit anchor strings extracted from
 * `com.xiaomi.type` (versionCode 21363) decompiled sources.
 */
internal object XiaoAiAnchors {
    const val CLASS_MI_IME_SERVICE = "com.mi.ime.MiInputMethodService"
    const val METHOD_IME_RESOLVE_TOP_ACTIVITY = "resolveTopActivityName"
    const val METHOD_IME_REPORT_TYPING_TIME = "onReportTypingTimeOfIme"

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

    const val CLASS_DEGRADE_LEVEL = "t7.e"
    const val FIELD_DEGRADE_STRICT = "c"
    val DEGRADE_HELPER_STRINGS = listOf("Degraded mode(", "runtime-only")

    const val CLASS_SENSOR_ANTIHOOK_HELPER = "xc.i"
    val SENSOR_ANTIHOOK_STRINGS = listOf("SensorHelper", "Xposed found on the system.", "virtualapp")
    val SENSOR_ANTIHOOK_ENV_STRINGS = listOf("xposed", "substrate", "virtualapp")
    const val CLASS_SECURITY_DEVICE_REPORTER = "com.xiaomi.passport.SecurityDeviceReporter"
    const val CLASS_PRIVACY_DATA_MASTER = "com.xiaomi.account.privacy_data.master.PrivacyDataMaster"
    const val CLASS_PRIVACY_DATA_TYPE = "com.xiaomi.account.privacy_data.master.PrivacyDataType"

    const val CLASS_ONETRACK_OAID_IMPL = "com.xiaomi.onetrack.util.p"
    val ONETRACK_OAID_IMPL_STRINGS = listOf("IdentifierManager", "getOAID")
    const val CLASS_ONETRACK_OAID_HELPER = "com.xiaomi.onetrack.util.oaid.a"
    val ONETRACK_OAID_HELPER_STRINGS = listOf(
        "getOaid() throw exception : Don't use it on the main thread",
        "isNotAllowedGetOaid"
    )
    const val CLASS_ONETRACK_INSID_HELPER = "com.xiaomi.onetrack.util.q"
    val ONETRACK_INSID_STRINGS = listOf(
        "content://com.miui.analytics.OneTrackProvider/insId",
        "getRemoteCacheInstanceId e"
    )
    const val CLASS_PHONENUM_OBTAIN = "cc.c"
    val PHONENUM_OBTAIN_STRINGS = listOf("ObtainHandler", "getPhoneNumberConfig parse response failed")

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

    const val CLASS_ONETRACK_BASE_IMP = "com.xiaomi.onetrack.api.c"
    val ONETRACK_BASE_IMP_STRINGS = listOf("BaseOneTrackImp", "triggerOTMonitorBusiness")
    const val CLASS_ONETRACK_AR_INTERFACE = "com.xiaomi.onetrack.api.ar"
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
    const val CLASS_ONETRACK_TRACEID_CALLABLE = "com.xiaomi.onetrack.api.b"
    val ONETRACK_TRACEID_STRINGS = listOf(
        "content://com.miui.analytics.OneTrackProvider/traceId",
        "exception while getTraceId"
    )
    const val CLASS_ONETRACK_LOG_CONFIG = "com.xiaomi.onetrack.h.a.a"
    val ONETRACK_LOG_CONFIG_STRINGS = listOf(
        "OTLogConfigManager",
        "content://com.miui.analytics.OneTrackProvider/crashLogConfig"
    )

    const val CLASS_CRASH_ANALYSIS = "com.xiaomi.onetrack.CrashAnalysis"
    val CRASH_ANALYSIS_HOOKER_STRINGS = listOf("xcrash.XCrash", "registerHook succeeded")
    val CRASH_ANALYSIS_REPORT_STRINGS = listOf("No crash info, skip it!", "onetrack_bug_report")
    const val CLASS_DUMP_LOG_PROVIDER = "com.miui.bugreport.logprovider.DumpLogProvider"

    const val CLASS_JANK_REPORTER = "kb.e"
    val JANK_REPORTER_STRINGS = listOf("JankReporter", "latencyType")
    const val CLASS_TAIYI_APP_TRACK_RUNNABLE = "i5.a0"
    val TAIYI_APP_TRACK_STRINGS = listOf("com.xiaomi.aiservice.authority.data", "app_track")

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
    const val CLASS_VOICEASSIST_QUERY_HELPER = "rb.c"
    val VOICEASSIST_QUERY_STRINGS = listOf(
        "com.miui.voiceassist.query",
        "com.xiaomi.voiceassistant.SpeechQueryService"
    )
    const val CLASS_VOICEASSIST_STATE = "z8.a"

    const val CLASS_MICLAW_MANAGER = "k8.n0"
    const val CLASS_MICLAW_SESSION = "k8.d0"
    const val CLASS_MICLAW_FAILED = "k8.g"
    val MICLAW_MANAGER_STRINGS = listOf("MiclawAgentManager", "mi_ime")
    val MICLAW_SUBMIT_STRINGS = listOf("Service not bound, attempting rebind for submitText")
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
    val SPEECH_ASR_START_STRINGS = listOf("Service not bound, attempting bind for startRecording")
    const val CLASS_SPEECH_MANAGER = "lc.f"
    val SPEECH_MANAGER_STRINGS = listOf("SpeechManager", "com.xiaomi.speech.action.SPEECH")
    const val CLASS_HOTWORD_CLOSURE = "mb.k3"
    val HOTWORD_CLOSURE_STRINGS = listOf("DELETE FROM dict_pack_catalog", "SELECT * FROM dict_pack_installed")

    const val CLASS_DICTPACK_SCHEDULER = "u9.d"
    val DICTPACK_SCHEDULER_STRINGS = listOf(
        "DictPackSyncScheduler",
        "dict_pack_catalog_periodic",
        "hot_dict_sync_immediate"
    )
    const val CLASS_DICTPACK_CATALOG_WORKER = "com.mi.ime.dictpack.work.DictPackCatalogSyncWorker"
    const val CLASS_DICTPACK_DOWNLOAD_WORKER = "com.mi.ime.dictpack.work.DictPackDownloadWorker"
    const val CLASS_HOTDICT_SYNC_WORKER = "com.mi.ime.dictpack.hotdict.HotDictSyncWorker"
    const val CLASS_WORK_RESULT_SUCCESS = "i5.r"
    val WORK_RESULT_SUCCESS_STRINGS = listOf("Success {mOutputData=")
    const val CLASS_CATALOG_HTTP_CLIENT = "i.o0"
    val CATALOG_HTTP_CLIENT_STRINGS = listOf(
        "Catalog requires HTTPS and the AES catalog endpoint",
        "fail to encrypt by aescoder"
    )

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

    const val CLASS_APP_LABEL_HELPER = "pd.a"
    val APP_LABEL_HELPER_STRINGS = listOf("getApplicationInfo(...)")

    const val CLASS_HOTWORD_CLIENT = "ca.w"
    val HOTWORD_CLIENT_STRINGS = listOf("HotwordRemoteClient", "com.xiaomi.aicr", "ime_hotword_service_version")
    val HOTWORD_STATIC_STRINGS = listOf("getStaticHotwords")
    val HOTWORD_STATIC_PAGED_STRINGS = listOf("getStaticHotwordsPaged")
    const val CLASS_HOTWORD_INIT_TASK = "ca.u"
    val HOTWORD_INIT_TASK_STRINGS = listOf("HotwordRemoteClient init done: supported=", "hotwords_preset.txt")
    const val CLASS_HOTWORD_EDIT_TRACKER = "ba.b"
    val HOTWORD_TRACKER_STRINGS = listOf("HotwordEditTracker", "textProvider 返回 null，无法初始化 ShadowBuffer")
    const val CLASS_VOICE_HOTWORD_CACHE = "fb.y5"
    val VOICE_HOTWORD_CACHE_STRINGS = listOf("VoiceHotwordCache", "from_app", "asr_text")

    const val CLASS_CLOUD_CONTROL_HELPER = "o8.c"
    const val CLASS_CLOUD_CONTROL_FLOW = "o8.b"
    val CLOUD_CONTROL_STRINGS = listOf("CloudControl", "observeCloudData snapshot failed module=")
    val CLOUD_CONTROL_FLOW_STRINGS = listOf("CloudControl", "not initialized, call CloudControlClient.init(context) first")
    const val CLASS_SCENARIO_RECOGNITION = "com.scenariorecognition.ScenarioRecognition"
    const val CLASS_SCENARIO_BINDER = "ub.a"
    val SCENARIO_BINDER_STRINGS = listOf("CoreScenarioRecognition onTransact error: ")
    const val CLASS_KOTLIN_UNIT = "yc.m"
    val KOTLIN_UNIT_STRINGS = listOf("kotlin.Unit")
}

/**
 * XiaoAi IME (`com.xiaomi.type`) Xposed hook pack (v2.0 granular rule engine).
 *
 * Covers all 20 XiaoAi rules defined in [HookRuleCatalog.rulesFor] for [ImeTarget.XIAOAI]
 * (19 hook-installed rules in this pack + `xiaoai.overlay_pill` delegated to the overlay hook).
 * Every hook callback gates dynamically via [ConfigBridge.isPointEnabled] so UI rule toggles
 * take effect immediately without restarting the IME process.
 */
object XiaoAiHookPack : ImeHookPack {

    override val target: ImeTarget = ImeTarget.XIAOAI

    private val targetPkg: String = target.packageName
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
            val imeServiceClass = resolveImeServiceClass(resolver)
            val baseImpClass = resolveOneTrackBaseImpClass(resolver)
            val unitSingleton = resolveKotlinUnitInstance(resolver, classLoader)

            runSafely(registry, "Gate rules (CTA fail-open & unlock degrade)") {
                installCtaFailOpenGate(classLoader, resolver, registry)
                installUnlockDegradeBypassHook(classLoader, resolver, registry, imeServiceClass)
            }
            runSafely(registry, "Identity & anti-hook rules") {
                installAntiHookAndSecReportHooks(classLoader, resolver, registry)
                installOaidInsidPhoneNumHooks(classLoader, resolver, registry, baseImpClass)
            }
            runSafely(registry, "Telemetry rules (OneTrack, Session, Taiyi/Jank, Bugreport/XCrash)") {
                installTelemetryHooks(classLoader, resolver, registry, imeServiceClass, baseImpClass)
            }
            runSafely(registry, "Cloud AI & DictPack & VoiceAssist rules") {
                installCloudAndIpcHooks(classLoader, resolver, registry, imeServiceClass)
            }
            runSafely(registry, "Context strip & Clipboard & Hotword rules") {
                installTopActivityAndPkgHooks(classLoader, resolver, registry, imeServiceClass)
                installClipboardPrivacyHooks(classLoader, resolver, registry)
                installHotwordAicrHooks(classLoader, resolver, registry, unitSingleton)
            }
            runSafely(registry, "System cloud control rule") {
                installSystemCloudControlHooks(classLoader, resolver, registry, unitSingleton)
            }
        }

        val reports = rules.mapNotNull { registry.getReport(it.ruleId) }
        val activeCount = reports.count { it.status == HookMatchStatus.ACTIVE }
        return ImePackInstallResult(
            target = target,
            reports = reports,
            note = "XiaoAiHookPack v2.0 installed: $activeCount/${rules.size} rules active (pkg=${appInfo.packageName})"
        )
    }

    // -------------------------------------------------------------------------
    // Gate Rules: CTA Fail-Open & Unlock Degrade Bypass
    // -------------------------------------------------------------------------

    private fun installCtaFailOpenGate(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_GATE_CTA_FAIL_OPEN
        val ctaClass = resolver.resolveClass(
            cacheKey = "xiaoai.cta_activity",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.CTA_ACTIVITY_STRINGS) }
        ).value

        val onActivityResultMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.cta_on_activity_result",
            targetClass = ctaClass,
            fastPathName = XiaoAiAnchors.METHOD_CTA_ON_ACTIVITY_RESULT,
            paramTypes = arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Intent::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.CTA_RESULT_STRINGS)
                    .paramTypes(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Intent::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterTypes.contentEquals(
                            arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Intent::class.java)
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
            val enabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_GATE_CTA_FAIL_OPEN)
            val allow = !enabled || explicitCtaConsentInProgress.get() == true
            if (!allow) {
                registry.incrementBlocked(ruleId)
                (chain.thisObject as? Activity)?.takeIf { !it.isFinishing }?.finish()
                return@hookExecutable null
            }
            chain.proceed()
        }
    }

    private fun installUnlockDegradeBypassHook(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        imeServiceClass: Class<*>?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_GATE_UNLOCK_DEGRADE_BYPASS
        val helperClass = resolver.resolveClass(
            cacheKey = "xiaoai.degrade_helper_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_VOICEASSIST_LAUNCH),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.DEGRADE_HELPER_STRINGS) }
        ).value
        val degradeEnumClass = resolver.resolveClass(
            cacheKey = "xiaoai.degrade_level_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_DEGRADE_LEVEL),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .superClass("java.lang.Enum")
                            .usingStrings(listOf("STRICT", "CTA", "NONE"))
                    )
                ).singleOrNull()?.descriptor
            }
        ).value

        val degradeEvalMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.unlock_degrade_eval",
            targetClass = helperClass,
            fastPathName = "t",
            paramTypes = if (imeServiceClass != null) arrayOf(imeServiceClass) else emptyArray(),
            dexKitMatcher = if (imeServiceClass != null && degradeEnumClass != null) {
                { cls ->
                    MethodMatcher.create()
                        .declaredClass(cls.name)
                        .paramTypes(imeServiceClass)
                        .returnType(degradeEnumClass)
                }
            } else null,
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 1 &&
                        (imeServiceClass == null || method.parameterTypes[0] == imeServiceClass) &&
                        method.returnType.isEnum &&
                        (method.returnType == degradeEnumClass || method.returnType.enumConstants?.size == 3)
                }
            }
        )

        val resolvedEnumClass = degradeEvalMethod.value?.returnType ?: degradeEnumClass
        val strictFieldRes = resolver.resolveField(
            cacheKey = "xiaoai.degrade_strict_field",
            fastPath = { _ ->
                resolvedEnumClass?.let { cls ->
                    runCatching {
                        cls.getDeclaredField(XiaoAiAnchors.FIELD_DEGRADE_STRICT).apply { isAccessible = true }
                    }.getOrNull()
                }
            },
            dexKitFinder = { bridge ->
                val cls = resolvedEnumClass ?: return@resolveField null
                val imeCls = imeServiceClass ?: return@resolveField null
                bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create()
                            .declaredClass(imeCls.name)
                            .name("isKeyboardEnglishOnly\$app_iflytekFullRelease")
                            .paramCount(0)
                    )
                ).singleOrNull()
                    ?.usingFields
                    ?.map { it.field }
                    ?.singleOrNull {
                        it.declaredClassName == cls.name &&
                            it.type.name == cls.name &&
                            Modifier.isStatic(it.modifiers)
                    }
                    ?.descriptor
            }
        )
        val strictValue: Any? = runCatching {
            strictFieldRes.value?.get(null) ?: resolvedEnumClass?.enumConstants?.lastOrNull()
        }.getOrNull()

        registry.hookExecutable(ruleId, "unlock_degrade_bypass", degradeEvalMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_GATE_UNLOCK_DEGRADE_BYPASS) &&
                strictValue != null
            ) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable strictValue
            }
            chain.proceed()
        }
    }

    // -------------------------------------------------------------------------
    // Identity & Anti-Hook Rules
    // -------------------------------------------------------------------------

    private fun installAntiHookAndSecReportHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT

        // 1. xc.i#{b, c}: Anti-hook environment spoofing & error scrubbing
        val sensorHelperClass = resolver.resolveClass(
            cacheKey = "xiaoai.sensor_antihook",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SENSOR_ANTIHOOK_HELPER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.SENSOR_ANTIHOOK_STRINGS) }
        ).value

        val envJsonMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.sensor_antihook_c",
            targetClass = sensorHelperClass,
            fastPathName = "c",
            paramTypes = arrayOf(Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.SENSOR_ANTIHOOK_ENV_STRINGS)
                    .paramTypes(Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!)
                    .returnType(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.contentEquals(
                            arrayOf(Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!)
                        )
                }
            }
        )
        registry.hookExecutable(ruleId, "antihook_env_c", envJsonMethod) { chain ->
            val raw = chain.proceed() as? String
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT) &&
                raw != null
            ) {
                val sanitized = XiaoAiPrivacyPolicy.sanitizeAntiHookEnvJson(raw)
                if (sanitized != null && sanitized != raw) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable sanitized
                }
            }
            raw
        }

        val errNotifyMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.sensor_antihook_b",
            targetClass = sensorHelperClass,
            fastPathName = "b",
            paramTypes = emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(listOf("SensorHelper"))
                    .paramCount(3)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 3 &&
                        method.parameterTypes[0] == cls &&
                        IOException::class.java.isAssignableFrom(method.parameterTypes[2])
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull { method ->
                method.name == name &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 3 &&
                    IOException::class.java.isAssignableFrom(method.parameterTypes[2])
            }
        }
        registry.hookExecutable(ruleId, "antihook_err_b", errNotifyMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT)) {
                val args = chain.args
                if (args.size >= 3 && args[2] is IOException) {
                    registry.incrementBlocked(ruleId)
                    val modified = args.toTypedArray()
                    modified[2] = IOException("redacted")
                    return@hookExecutable chain.proceed(modified)
                }
            }
            chain.proceed()
        }

        // 2. SecurityDeviceReporter#report
        val secReporterClass = resolver.resolveClass(
            cacheKey = "xiaoai.sec_device_reporter",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SECURITY_DEVICE_REPORTER),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, listOf("https://tz.sec.xiaomi.com/session", "SecurityDeviceReporter"))
            }
        ).value
        val secReportMethod = resolveMethodOnClass(
            resolver = resolver,
            classLoader = classLoader,
            cacheKey = "xiaoai.sec_device_report",
            targetClass = secReporterClass,
            fastPathName = "report",
            paramTypes = arrayOf(
                Context::class.java,
                Int::class.javaPrimitiveType!!,
                String::class.java,
                String::class.java
            ),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .name("report")
                    .modifiers(Modifier.PUBLIC)
                    .paramTypes(Context::class.java, Int::class.javaPrimitiveType!!, String::class.java, String::class.java)
                    .returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    method.name == "report" && method.parameterCount == 4
                }
            }
        )
        registry.hookExecutable(ruleId, "security_device_report", secReportMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        // 3. PrivacyDataMaster#{get, forceGet} shared hook for both identity rules
        installPrivacyDataMasterHooks(classLoader, resolver, registry)
    }

    private fun installPrivacyDataMasterHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val pdmClass = resolver.resolveClass(
            cacheKey = "xiaoai.privacy_data_master",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_PRIVACY_DATA_MASTER),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, listOf("PrivacyDataMaster", "com.xiaomi.account.privacy_data.master.data"))
            }
        ).value
        val pdTypeClass = resolver.resolveClass(
            cacheKey = "xiaoai.privacy_data_type",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_PRIVACY_DATA_TYPE),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .superClass("java.lang.Enum")
                            .usingStrings(listOf("miui_device_id", "line_1_number", "configured_ssids", "sim_in_service"))
                    )
                ).singleOrNull()?.descriptor
            }
        ).value

        val methodsToHook = mutableListOf<Pair<String, ResolveResult<Method>>>()
        methodsToHook += "pdm_get_str" to resolveMethodOnClass(
            resolver, classLoader, "xiaoai.pdm_get_str", pdmClass, "get",
            arrayOf(Context::class.java, String::class.java, Array<String>::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).name("get")
                    .paramTypes(Context::class.java, String::class.java, Array<String>::class.java)
            },
            structuralFallback = null
        )
        methodsToHook += "pdm_force_str" to resolveMethodOnClass(
            resolver, classLoader, "xiaoai.pdm_force_str", pdmClass, "forceGet",
            arrayOf(Context::class.java, String::class.java, Array<String>::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).name("forceGet")
                    .paramTypes(Context::class.java, String::class.java, Array<String>::class.java)
            },
            structuralFallback = null
        )
        if (pdTypeClass != null) {
            methodsToHook += "pdm_get_enum" to resolveMethodOnClass(
                resolver, classLoader, "xiaoai.pdm_get_enum", pdmClass, "get",
                arrayOf(Context::class.java, pdTypeClass, Array<String>::class.java),
                dexKitMatcher = { cls ->
                    MethodMatcher.create().declaredClass(cls.name).name("get")
                        .paramTypes(Context::class.java, pdTypeClass, Array<String>::class.java)
                },
                structuralFallback = null
            )
            methodsToHook += "pdm_force_enum" to resolveMethodOnClass(
                resolver, classLoader, "xiaoai.pdm_force_enum", pdmClass, "forceGet",
                arrayOf(Context::class.java, pdTypeClass, Array<String>::class.java),
                dexKitMatcher = { cls ->
                    MethodMatcher.create().declaredClass(cls.name).name("forceGet")
                        .paramTypes(Context::class.java, pdTypeClass, Array<String>::class.java)
                },
                structuralFallback = null
            )
        }

        val secRuleId = HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT
        val oaidRuleId = HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM
        for ((hookId, methodRes) in methodsToHook) {
            // Register under OAID ruleId (primary) and dynamically increment whichever rule matched
            registry.hookExecutable(oaidRuleId, hookId, methodRes) { chain ->
                val rawType = chain.getArg(1)
                val typeName = when (rawType) {
                    is String -> rawType
                    else -> rawType?.let { enumObj ->
                        runCatching {
                            enumObj.javaClass.getField("name").get(enumObj) as? String
                        }.getOrNull() ?: (enumObj as? Enum<*>)?.name
                    }
                }
                val triggeredRule = XiaoAiPrivacyPolicy.shouldBlockPrivacyDataType(ConfigBridge.current, typeName)
                if (triggeredRule != null && ConfigBridge.isPointEnabled(targetPkg, triggeredRule)) {
                    registry.incrementBlocked(triggeredRule)
                    if (triggeredRule == secRuleId) {
                        registry.incrementBlocked(secRuleId)
                    }
                    return@hookExecutable ""
                }
                chain.proceed()
            }
        }
    }

    private fun installOaidInsidPhoneNumHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        baseImpClass: Class<*>?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM

        // 1. com.xiaomi.onetrack.util.p#a(Context) -> IdProviderImpl.getOAID
        val oaidImplClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_oaid_impl",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_OAID_IMPL),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_OAID_IMPL_STRINGS) }
        ).value
        val oaidImplGetMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_oaid_impl_get", oaidImplClass, "a",
            arrayOf(Context::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).paramTypes(Context::class.java).returnType(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
            }
        )
        registry.hookExecutable(ruleId, "onetrack_oaid_impl", oaidImplGetMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable ""
            }
            chain.proceed()
        }

        // 2. com.xiaomi.onetrack.util.oaid.a#a(Context)
        val oaidHelperClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_oaid_helper",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_OAID_HELPER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_OAID_HELPER_STRINGS) }
        ).value
        val oaidHelperGetMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_oaid_helper_get", oaidHelperClass, "a",
            arrayOf(Context::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).paramTypes(Context::class.java).returnType(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.returnType == String::class.java &&
                        method.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
            }
        )
        registry.hookExecutable(ruleId, "onetrack_oaid_helper", oaidHelperGetMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable ""
            }
            chain.proceed()
        }

        // 3. com.xiaomi.onetrack.util.q#b() -> OneTrackProvider/insId
        val insIdHelperClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_insid_helper",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_INSID_HELPER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_INSID_STRINGS) }
        ).value
        val insIdGetMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_insid_get", insIdHelperClass, "b",
            emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .modifiers(Modifier.PUBLIC)
                    .paramCount(0)
                    .returnType(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        Modifier.isPublic(method.modifiers) &&
                        method.parameterCount == 0 &&
                        method.returnType == String::class.java
                }
            }
        )
        registry.hookExecutable(ruleId, "onetrack_insid_get", insIdGetMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable ""
            }
            chain.proceed()
        }

        // 4. cc.c#b(int, String, String) -> Passport/PhoneNum AccountCertification getter
        val phoneObtainClass = resolver.resolveClass(
            cacheKey = "xiaoai.phonenum_obtain",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_PHONENUM_OBTAIN),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.PHONENUM_OBTAIN_STRINGS) }
        ).value
        val phoneObtainMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.phonenum_obtain_b", phoneObtainClass, "b",
            arrayOf(Int::class.javaPrimitiveType!!, String::class.java, String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("PhoneNumberRequest#TraceId="))
                    .paramTypes(Int::class.javaPrimitiveType!!, String::class.java, String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(
                            arrayOf(Int::class.javaPrimitiveType!!, String::class.java, String::class.java)
                        )
                }
            }
        )
        registry.hookExecutable(ruleId, "phonenum_obtain", phoneObtainMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        // 5. BaseOneTrackImp c#{c(Context), d(Context), e()} (OAID, AndroidId, InstanceId)
        for ((suffix, name, params) in listOf(
            Triple("base_oaid", "c", arrayOf<Class<*>>(Context::class.java)),
            Triple("base_androidid", "d", arrayOf<Class<*>>(Context::class.java)),
            Triple("base_instanceid", "e", emptyArray<Class<*>>())
        )) {
            val mRes = resolveMethodOnClass(
                resolver, classLoader, "xiaoai.onetrack_$suffix", baseImpClass, name, params,
                dexKitMatcher = { cls ->
                    MethodMatcher.create().declaredClass(cls.name).name(name)
                        .paramTypes(*params).returnType(String::class.java)
                },
                structuralFallback = { cls ->
                    SymbolResolver.findDeclaredMethodOrNull(cls, name, *params)?.takeIf {
                        it.returnType == String::class.java
                    }
                }
            )
            registry.hookExecutable(ruleId, "onetrack_$suffix", mRes) { chain ->
                if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable ""
                }
                chain.proceed()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Telemetry Rules: OneTrack, Password/Session, Taiyi/Jank, Bugreport/XCrash
    // -------------------------------------------------------------------------

    private fun installTelemetryHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        imeServiceClass: Class<*>?,
        baseImpClass: Class<*>?
    ) {
        val collectorClass = resolver.resolveClass(
            cacheKey = "xiaoai.session_collector",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SESSION_COLLECTOR),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.SESSION_COLLECTOR_STRINGS) }
        ).value
        val holderClass = resolver.resolveClass(
            cacheKey = "xiaoai.session_holder",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SESSION_HOLDER),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings(XiaoAiAnchors.SESSION_HOLDER_STRINGS + "keys(...)", StringMatchType.Equals)
                            .interfaces { count(0) }
                            .methods { add { paramTypes(org.json.JSONObject::class.java) } }
                    )
                ).singleOrNull()?.descriptor
            }
        ).value
        val holderField = resolver.resolveField(
            cacheKey = "xiaoai.session_holder_field",
            fastPath = { _ -> findSessionHolderField(collectorClass) },
            dexKitFinder = { bridge ->
                val cls = collectorClass ?: return@resolveField null
                val hCls = holderClass ?: return@resolveField null
                bridge.findField(
                    FindField.create().matcher(
                        org.luckypray.dexkit.query.matchers.FieldMatcher.create()
                            .declaredClass(cls.name)
                            .type(hCls.name)
                            .modifiers(Modifier.STATIC or Modifier.FINAL)
                    )
                ).singleOrNull()?.descriptor
            }
        ).value
        val passwordField = resolver.resolveField(
            cacheKey = "xiaoai.session_password_field",
            fastPath = { _ ->
                holderClass?.let { cls ->
                    runCatching {
                        cls.getDeclaredField(XiaoAiAnchors.FIELD_SESSION_PASSWORD).takeIf {
                            it.type == Boolean::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers)
                        }?.apply { isAccessible = true }
                    }.getOrNull()
                }
            },
            dexKitFinder = { bridge ->
                val cCls = collectorClass ?: return@resolveField null
                val hCls = holderClass ?: return@resolveField null
                val nonPasswordDescs = bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create()
                            .declaredClass(cCls.name)
                            .usingStrings("duration_s")
                    )
                ).singleOrNull()
                    ?.usingFields
                    ?.map { it.field }
                    ?.filter { it.declaredClassName == hCls.name && it.type.name == "boolean" }
                    ?.mapTo(HashSet()) { it.descriptor }
                    ?: emptySet()
                bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create()
                            .declaredClass(cCls.name)
                            .usingStrings("source_app")
                    )
                ).singleOrNull()
                    ?.usingFields
                    ?.map { it.field }
                    ?.singleOrNull {
                        it.declaredClassName == hCls.name &&
                            it.type.name == "boolean" &&
                            it.descriptor !in nonPasswordDescs
                    }
                    ?.descriptor
            }
        ).value
        val sessionAccess = SessionAccessHelper(holderField, holderClass, passwordField)
        val sdkClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_sdk",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_SDK),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings(listOf("context is null!"), StringMatchType.Equals)
                            .methods {
                                add { name(XiaoAiAnchors.METHOD_ONETRACK_SET_NET) }
                                add { name("track"); paramCount(2) }
                                add { name("trackEventFromH5"); paramCount(1) }
                            }
                    )
                ).singleOrNull()?.descriptor
            }
        ).value

        installOneTrackSdkHooks(classLoader, resolver, registry, sessionAccess, sdkClass)
        installOneTrackHelperHooks(classLoader, resolver, registry, sessionAccess, sdkClass)
        installOneTrackBaseAndIpcHooks(classLoader, resolver, registry, sessionAccess, baseImpClass)
        installSessionActionAndTypingHooks(classLoader, resolver, registry, collectorClass, holderClass, sessionAccess, imeServiceClass)
        installTaiyiAndJankHooks(classLoader, resolver, registry)
        installBugreportAndXCrashHooks(classLoader, resolver, registry)
    }

    private fun shouldBlockOneTrackNow(sessionAccess: SessionAccessHelper): Boolean =
        ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK) ||
            (sessionAccess.isPasswordSession() &&
                ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION))

    private fun installOneTrackSdkHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        sessionAccess: SessionAccessHelper,
        sdkClass: Class<*>?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK

        val setNetMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_set_net", sdkClass,
            XiaoAiAnchors.METHOD_ONETRACK_SET_NET,
            arrayOf(Context::class.java, Boolean::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .name(XiaoAiAnchors.METHOD_ONETRACK_SET_NET)
                    .paramTypes(Context::class.java, Boolean::class.javaPrimitiveType!!)
            },
            structuralFallback = null
        )
        registry.hookExecutable(ruleId, "onetrack_set_net", setNetMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK)) {
                val originalArgs = chain.args
                if (originalArgs.size >= 2 && originalArgs[1] == true) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable chain.proceed(arrayOf(originalArgs[0], false))
                }
            }
            chain.proceed()
        }

        val trackSpecs = listOf(
            Triple("onetrack_sdk_track#0", "track", arrayOf("java.lang.String", "java.util.Map")),
            Triple("onetrack_sdk_track#1", "track", arrayOf("java.lang.String", "java.util.List", "java.util.Map")),
            Triple("onetrack_sdk_trackByFileOnlySystemApp#0", "trackByFileOnlySystemApp", arrayOf("java.lang.String", "java.util.Map")),
            Triple("onetrack_sdk_trackEventFromH5#0", "trackEventFromH5", arrayOf("java.lang.String")),
            Triple(
                "onetrack_sdk_trackException#0",
                "trackException",
                arrayOf("java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String", "long")
            ),
            Triple("onetrack_sdk_trackPluginEvent#0", "trackPluginEvent", arrayOf("java.lang.String", "java.lang.String", "java.util.Map")),
            Triple("onetrack_sdk_trackServiceQualityEvent#0", "trackServiceQualityEvent", arrayOf("com.xiaomi.onetrack.ServiceQualityEvent")),
            Triple("onetrack_sdk_adTrack#0", "adTrack", arrayOf("java.lang.String", "java.util.Map")),
            Triple("onetrack_sdk_adTrack#1", "adTrack", arrayOf("java.lang.String", "java.util.Map", "java.util.List"))
        )

        for ((hookId, methodName, paramTypeNames) in trackSpecs) {
            val mRes = resolveMethodByTypeNames(
                resolver = resolver,
                classLoader = classLoader,
                cacheKey = "xiaoai.$hookId",
                targetClass = sdkClass,
                methodName = methodName,
                paramTypeNames = paramTypeNames
            )
            registry.hookExecutable(ruleId, hookId, mRes) { chain ->
                if (shouldBlockOneTrackNow(sessionAccess)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }
    }

    private fun installOneTrackHelperHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        sessionAccess: SessionAccessHelper,
        sdkClass: Class<*>?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK
        val helperClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_helper",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_HELPER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_HELPER_STRINGS) }
        ).value

        val flushEarlyMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_helper_flush", helperClass, "b",
            if (sdkClass != null) arrayOf(sdkClass) else emptyArray(),
            dexKitMatcher = if (sdkClass != null) {
                { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(sdkClass).returnType(Void.TYPE) }
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
            resolver, classLoader, "xiaoai.onetrack_helper_error", helperClass, "f",
            arrayOf(String::class.java, Map::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.ONETRACK_HELPER_ERROR_STRINGS)
                    .paramTypes(String::class.java, Map::class.java)
            },
            structuralFallback = { cls ->
                val methods = safeDeclaredMethods(cls)
                methods.firstOrNull { method ->
                    method.name == "f" && !Modifier.isStatic(method.modifiers) &&
                        method.parameterTypes.contentEquals(arrayOf(String::class.java, Map::class.java))
                } ?: methods.firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
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
                val errDesc = trackErrorMethod.descriptor
                bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create().declaredClass(cls.name)
                            .paramTypes(String::class.java, Map::class.java).returnType(Void.TYPE)
                    )
                ).singleOrNull { it.descriptor != errDesc }?.descriptor
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

        for ((hookId, methodRes) in listOf(
            "helper_flush_early" to flushEarlyMethod,
            "helper_track_event" to trackEventMethod,
            "helper_track_error" to trackErrorMethod
        )) {
            registry.hookExecutable(ruleId, hookId, methodRes) { chain ->
                if (shouldBlockOneTrackNow(sessionAccess)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }
    }

    private fun installOneTrackBaseAndIpcHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        sessionAccess: SessionAccessHelper,
        baseImpClass: Class<*>?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK

        // Superclass com.xiaomi.onetrack.api.c (BaseOneTrackImp)
        val arInterfaceClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_ar_interface",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_AR_INTERFACE),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create()
                        .searchPackages("com.xiaomi.onetrack.api")
                        .matcher(
                            ClassMatcher.create()
                                .modifiers(Modifier.INTERFACE)
                                .methods {
                                    count(4)
                                    add { paramTypes(Int::class.javaPrimitiveType!!); returnType(Void.TYPE) }
                                    add { paramTypes(Boolean::class.javaPrimitiveType!!); returnType(Void.TYPE) }
                                }
                        )
                ).singleOrNull()?.descriptor
            }
        ).value

        // c#j(): ar -> return safe no-op ar proxy if au#a short-circuited leaving f4218a == null
        val baseGetArMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_base_j", baseImpClass, "j",
            emptyArray(),
            dexKitMatcher = if (arInterfaceClass != null) {
                { cls -> MethodMatcher.create().declaredClass(cls.name).paramCount(0).returnType(arInterfaceClass) }
            } else null,
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) && method.parameterCount == 0 && method.returnType.isInterface
                }
            }
        )
        registry.hookExecutable(ruleId, "onetrack_base_ar_getter", baseGetArMethod) { chain ->
            val res = chain.proceed()
            XiaoAiOneTrackGuard.ensureSafeArDelegate(
                delegateResult = res,
                arInterface = arInterfaceClass ?: baseGetArMethod.value?.returnType,
                config = ConfigBridge.current
            )
        }

        // Hook BaseOneTrackImp lifecycle / event / monitor methods
        for (methodName in listOf("p", "s", "h", "t", "o")) {
            val mRes = resolveMethodOnClass(
                resolver, classLoader, "xiaoai.onetrack_base_$methodName", baseImpClass, methodName,
                emptyArray(),
                dexKitMatcher = { cls ->
                    MethodMatcher.create().declaredClass(cls.name).name(methodName).paramCount(0).returnType(Void.TYPE)
                },
                structuralFallback = { cls ->
                    SymbolResolver.findDeclaredMethodOrNull(cls, methodName)?.takeIf { it.returnType == Void.TYPE }
                }
            )
            registry.hookExecutable(ruleId, "onetrack_base_$methodName", mRes) { chain ->
                if (shouldBlockOneTrackNow(sessionAccess)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }
        val baseTrackSpecs: List<Triple<String, String, Array<String>>> = listOf(
            Triple("0", "a", arrayOf("java.util.HashMap")),
            Triple("1", "a", arrayOf("java.lang.String", "java.lang.String", "java.util.Map")),
            Triple("2", "a", arrayOf("java.lang.String", "java.lang.String", "boolean")),
            Triple("3", "b", arrayOf("java.util.Map")),
            Triple("4", "b", arrayOf("java.lang.String")),
            Triple("5", "b", arrayOf("boolean")),
            Triple("6", "a", arrayOf("java.lang.String", "java.util.Map", "boolean")),
            Triple("7", "a", arrayOf("java.lang.String", "java.util.Map", "java.util.List")),
            Triple("8", "a", arrayOf("java.lang.String")),
            Triple(
                "9",
                "a",
                arrayOf(
                    "java.lang.String",
                    "java.lang.String",
                    "java.lang.String",
                    "java.lang.String",
                    "java.lang.String",
                    "java.lang.String",
                    "long",
                    "java.util.Map",
                    "boolean"
                )
            ),
            Triple("10", "a", arrayOf("java.util.Map")),
            Triple("11", "a", arrayOf("java.lang.String", "java.lang.Object")),
            Triple("12", "a", arrayOf("java.lang.String", "com.xiaomi.onetrack.OneTrack\$UserIdType", "java.util.Map", "boolean")),
            Triple("13", "a", arrayOf("java.lang.String", "java.lang.Number")),
            Triple("14", "a", arrayOf("java.util.Map", "boolean")),
            Triple("15", "a", arrayOf("java.lang.String", "boolean")),
            Triple("16", "a", arrayOf("java.lang.String", "long")),
            Triple("17", "a", arrayOf("boolean")),
            Triple("18", "a", arrayOf("com.xiaomi.onetrack.OneTrack\$ICommonPropertyProvider")),
            Triple("19", "a", arrayOf("com.xiaomi.onetrack.ServiceQualityEvent")),
            Triple("20", "a", arrayOf("com.xiaomi.onetrack.OneTrack\$IEventHook"))
        )
        for ((idx, methodName, paramTypeNames) in baseTrackSpecs) {
            val mRes = resolveMethodByTypeNames(
                resolver = resolver,
                classLoader = classLoader,
                cacheKey = "xiaoai.onetrack_base_track_$idx",
                targetClass = baseImpClass,
                methodName = methodName,
                paramTypeNames = paramTypeNames,
                returnTypeName = "void"
            )
            registry.hookExecutable(ruleId, "onetrack_base_track_$idx", mRes) { chain ->
                if (shouldBlockOneTrackNow(sessionAccess)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }

        // Subclass au#a(Context): short-circuit BEFORE proceed() to prevent ba bindService without mutating OneTrack.b
        val impClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_imp",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_IMP),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings(
                                listOf("OneTrackImp", "master sdk only smarthome call this method! "),
                                StringMatchType.Equals
                            )
                            .apply { baseImpClass?.let { superClass(it.name) } }
                    )
                ).singleOrNull()?.descriptor
            }
        ).value
        val impInitMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_imp_init", impClass, "a",
            arrayOf(Context::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).paramTypes(Context::class.java).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) && method.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
            }
        )
        registry.hookExecutable(ruleId, "onetrack_imp_init", impInitMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        val sysImpClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_sys_imp",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_SYSTEM_IMP),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_SYSTEM_IMP_STRINGS) }
        ).value
        installOneTrackArHooks(ruleId, "xiaoai.onetrack_sys_imp", "onetrack_sys_imp", sysImpClass, classLoader, resolver, registry, sessionAccess)

        val localImpClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_local_imp",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_LOCAL_IMP),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_LOCAL_IMP_STRINGS) }
        ).value
        installOneTrackArHooks(ruleId, "xiaoai.onetrack_local_imp", "onetrack_local_imp", localImpClass, classLoader, resolver, registry, sessionAccess)

        // ServiceConnectManager (ba)
        val serviceConnClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_service_conn",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_SERVICE_CONN),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_SERVICE_CONN_STRINGS) }
        ).value
        val threeStrings: Array<Class<*>> = Array(3) { String::class.java }
        val sevenStrings: Array<Class<*>> = Array(7) { String::class.java }
        val ipcThreeMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_ipc_3", serviceConnClass, "a", threeStrings,
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(*threeStrings) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { !Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(threeStrings) }
            }
        )
        val ipcSevenAMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_ipc_7a", serviceConnClass, "a", sevenStrings,
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(listOf("track ps throwable: "))
                    .paramTypes(*sevenStrings)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { !Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(sevenStrings) }
            }
        )
        val ipcSevenBMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_ipc_7b", serviceConnClass, "b", sevenStrings,
            dexKitMatcher = { cls ->
                MethodMatcher.create()
                    .declaredClass(cls.name)
                    .usingStrings(listOf("projectId", "topic"))
                    .paramTypes(*sevenStrings)
            },
            structuralFallback = { cls ->
                val sevenAName = ipcSevenAMethod.value?.name
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(sevenStrings) && it.name != sevenAName
                }
            }
        )
        val bindServiceInternalMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_ipc_bind", serviceConnClass, "f", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("bindService e: "))
                    .paramCount(0)
            },
            structuralFallback = { cls ->
                SymbolResolver.findDeclaredMethodOrNull(cls, "f")
            }
        )

        for ((hookId, methodRes) in listOf(
            "onetrack_ipc_3" to ipcThreeMethod,
            "onetrack_ipc_7a" to ipcSevenAMethod,
            "onetrack_ipc_7b" to ipcSevenBMethod,
            "onetrack_ipc_bind" to bindServiceInternalMethod
        )) {
            registry.hookExecutable(ruleId, hookId, methodRes) { chain ->
                val returnType = methodRes.value?.returnType ?: Boolean::class.javaPrimitiveType!!
                if (shouldBlockOneTrackNow(sessionAccess)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable defaultReturnValue(returnType)
                }
                chain.proceed()
            }
        }

        // OneTrackProvider/traceId & OneTrackProvider/crashLogConfig
        val traceIdClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_traceid_callable",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_TRACEID_CALLABLE),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_TRACEID_STRINGS) }
        ).value
        val traceIdCallMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_traceid_call", traceIdClass, "call", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .name("call")
                    .paramCount(0)
                    .returnType(Any::class.java)
            },
            structuralFallback = { cls -> SymbolResolver.findDeclaredMethodOrNull(cls, "call") }
        )
        registry.hookExecutable(ruleId, "onetrack_provider_traceid", traceIdCallMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable ""
            }
            chain.proceed()
        }

        val logConfigClass = resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_log_config",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_LOG_CONFIG),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_LOG_CONFIG_STRINGS) }
        ).value
        val fetchLogConfigMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.onetrack_log_config_d", logConfigClass, "d", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("content://com.miui.analytics.OneTrackProvider/crashLogConfig"))
                    .paramCount(0).returnType(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == String::class.java }
            }
        )
        registry.hookExecutable(ruleId, "onetrack_provider_logconfig", fetchLogConfigMethod) { chain ->
            val onetrackOn = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK)
            val bugreportOn = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH)
            if (onetrackOn || bugreportOn) {
                if (onetrackOn) registry.incrementBlocked(ruleId)
                if (bugreportOn) registry.incrementBlocked(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH)
                return@hookExecutable null
            }
            chain.proceed()
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
            resolver, classLoader, "$cachePrefix.lifecycle", impClass, "a", arrayOf(Int::class.javaPrimitiveType!!),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(Int::class.javaPrimitiveType!!).returnType(Void.TYPE) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) && it.returnType == Void.TYPE &&
                        it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType!!))
                }
            }
        )
        val trackAMethod = resolveMethodOnClass(
            resolver, classLoader, "$cachePrefix.track_a", impClass, "a", arrayOf(String::class.java, String::class.java),
            dexKitMatcher = { cls ->
                val matcher = MethodMatcher.create()
                    .declaredClass(cls.name)
                    .paramTypes(String::class.java, String::class.java)
                    .returnType(Void.TYPE)
                if (cachePrefix == "xiaoai.onetrack_sys_imp") {
                    matcher.usingNumbers(0)
                } else {
                    matcher.usingStrings(listOf("The privacy policy is not permitted"))
                }
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) && it.returnType == Void.TYPE &&
                        it.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
                }
            }
        )
        val trackBMethod = resolver.resolveMethod(
            cacheKey = "$cachePrefix.track_b",
            fastPath = { cl ->
                val cls = impClass ?: return@resolveMethod null
                val resolvedCls = if (cls.classLoader === cl) cls else SymbolResolver.loadClassOrNull(cls.name, cl) ?: return@resolveMethod null
                SymbolResolver.findDeclaredMethodOrNull(resolvedCls, "b", String::class.java, String::class.java)
            },
            dexKitFinder = { bridge ->
                val cls = impClass ?: return@resolveMethod null
                val trackADesc = trackAMethod.descriptor
                bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create().declaredClass(cls.name).paramTypes(String::class.java, String::class.java).returnType(Void.TYPE)
                    )
                ).singleOrNull { it.descriptor != trackADesc }?.descriptor
            },
            structuralFallback = { _ ->
                val cls = impClass ?: return@resolveMethod null
                val trackAName = trackAMethod.value?.name
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) && it.returnType == Void.TYPE &&
                        it.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java)) && it.name != trackAName
                }
            }
        )
        val flushFlagMethod = resolveMethodOnClass(
            resolver, classLoader, "$cachePrefix.flush", impClass, "a", arrayOf(Boolean::class.javaPrimitiveType!!),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(Boolean::class.javaPrimitiveType!!).returnType(Void.TYPE) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) && it.returnType == Void.TYPE &&
                        it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType!!))
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
                if (shouldBlockOneTrackNow(sessionAccess)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable defaultReturnValue(returnType)
                }
                chain.proceed()
            }
        }
    }

    private fun installSessionActionAndTypingHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        collectorClass: Class<*>?,
        holderClass: Class<*>?,
        sessionAccess: SessionAccessHelper,
        imeServiceClass: Class<*>?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION

        val holderSetActiveMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.session_holder_active", holderClass, "a",
            arrayOf(Boolean::class.javaPrimitiveType!!),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(Boolean::class.javaPrimitiveType!!).returnType(Void.TYPE) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType!!)) && it.returnType == Void.TYPE
                }
            }
        )
        val buildMapMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.session_build_map", collectorClass, "a",
            arrayOf(List::class.java, Int::class.javaPrimitiveType!!),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(List::class.java, Int::class.javaPrimitiveType!!) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(List::class.java, Int::class.javaPrimitiveType!!))
                }
            }
        )
        val backActionMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.session_action_back", collectorClass, "b", arrayOf(String::class.java),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.SESSION_ACTION_BACK_STRINGS).paramTypes(String::class.java) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { it.name == "b" && Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
            }
        )
        val delActionMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.session_action_del", collectorClass, "c", arrayOf(String::class.java),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.SESSION_ACTION_DEL_STRINGS).paramTypes(String::class.java) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { it.name == "c" && Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
            }
        )
        val otherActionMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.session_action_d", collectorClass, "d", arrayOf(String::class.java),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.SESSION_ACTION_OTHER_STRINGS).paramTypes(String::class.java) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                        it.name != backActionMethod.value?.name && it.name != delActionMethod.value?.name
                }
            }
        )
        val emitSessionMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.session_emit", collectorClass, "e",
            arrayOf(Long::class.javaPrimitiveType!!, String::class.java, Map::class.java),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(Long::class.javaPrimitiveType!!, String::class.java, Map::class.java) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType!!, String::class.java, Map::class.java))
                }
            }
        )
        val cacheClass = resolver.resolveClass(
            cacheKey = "xiaoai.session_cache",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SESSION_CACHE),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings(
                                XiaoAiAnchors.SESSION_CACHE_STRINGS + "reportEvent",
                                StringMatchType.Equals
                            )
                            .interfaces { count(0) }
                            .methods {
                                add { paramTypes(String::class.java, Map::class.java).returnType(Void.TYPE) }
                            }
                    )
                ).singleOrNull()?.descriptor
            }
        ).value
        val cacheReportMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.session_cache_report", cacheClass, "b",
            arrayOf(String::class.java, Map::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.SESSION_CACHE_STRINGS)
                    .paramTypes(String::class.java, Map::class.java).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(String::class.java, Map::class.java))
                }
            }
        )

        val shouldBlockSession: () -> Boolean = {
            ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION) ||
                ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK)
        }

        registry.hookExecutable(ruleId, "session_holder_active", holderSetActiveMethod) { chain ->
            if (shouldBlockSession()) {
                val res = chain.proceed(arrayOf(false))
                sessionAccess.zeroCounters(chain.thisObject)
                return@hookExecutable res
            }
            chain.proceed()
        }

        registry.hookExecutable(ruleId, "session_build_map", buildMapMethod) { chain ->
            if (shouldBlockSession()) {
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
                if (shouldBlockSession()) {
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
            registry.hookExecutable(ruleId, "session_cache_report", cacheReportMethod, onFailureStatus = HookMatchStatus.DEGRADED) { chain ->
                if (shouldBlockSession()) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }

        // Optional MIUI/HyperOS framework method on android.inputmethodservice.InputMethodService (absent in APK DEX & AOSP)
        val typingReportMethod = imeServiceClass?.let { cls ->
            runCatching { cls.getMethod(XiaoAiAnchors.METHOD_IME_REPORT_TYPING_TIME, Bundle::class.java) }.getOrNull()
        }
        if (typingReportMethod != null) {
            registry.hookExecutable(
                ruleId = ruleId,
                hookId = "ime_report_typing_time",
                resolveResult = ResolveResult(
                    value = typingReportMethod,
                    source = ResolveSource.FAST_PATH,
                    descriptor = SymbolResolver.executableToDescriptor(typingReportMethod),
                    detail = SymbolResolver.formatExecutable(typingReportMethod)
                ),
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }
    }

    private fun installTaiyiAndJankHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val jankRuleId = HookRuleCatalog.RULE_XIAOAI_TELEMETRY_TAIYI_AND_JANK

        // kb.e#P(int) -> JankReporter
        val jankClass = resolver.resolveClass(
            cacheKey = "xiaoai.jank_reporter",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_JANK_REPORTER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.JANK_REPORTER_STRINGS) }
        ).value
        val jankReportMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.jank_report_p", jankClass, "P",
            arrayOf(Int::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.JANK_REPORTER_STRINGS)
                    .paramTypes(Int::class.javaPrimitiveType!!).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    it.name == "P" && Modifier.isStatic(it.modifiers) &&
                        it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType!!)) && it.returnType == Void.TYPE
                }
            }
        )
        registry.hookExecutable(jankRuleId, "jank_reporter_p", jankReportMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_TAIYI_AND_JANK)) {
                registry.incrementBlocked(jankRuleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        // i5.a0#run() -> Taiyi TrackManager app_track (gated cleanly ONLY for the non-case-0 AIRequest case)
        val appTrackClass = resolver.resolveClass(
            cacheKey = "xiaoai.taiyi_app_track_runnable",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TAIYI_APP_TRACK_RUNNABLE),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.TAIYI_APP_TRACK_STRINGS) }
        ).value
        val appTrackRunMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.taiyi_app_track_run", appTrackClass, "run", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).name("run").paramCount(0).returnType(Void.TYPE)
            },
            structuralFallback = { cls -> SymbolResolver.findDeclaredMethodOrNull(cls, "run") }
        )
        registry.hookExecutable(jankRuleId, "taiyi_app_track_run", appTrackRunMethod) { chain ->
            if (isTaiyiAppTrackRunnableInstance(chain.thisObject)) {
                val taiyiBlocked = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_TAIYI_AND_JANK)
                val pkgBlocked = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)
                if (taiyiBlocked || pkgBlocked) {
                    if (taiyiBlocked) registry.incrementBlocked(jankRuleId)
                    if (pkgBlocked) registry.incrementBlocked(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)
                    return@hookExecutable null
                }
            }
            chain.proceed()
        }
    }

    private fun installBugreportAndXCrashHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH
        val crashClass = resolver.resolveClass(
            cacheKey = "xiaoai.crash_analysis",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_CRASH_ANALYSIS),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.CRASH_ANALYSIS_HOOKER_STRINGS) }
        ).value

        val xcrashHookerMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.crash_analysis_hooker", crashClass, "a", arrayOf(Context::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.CRASH_ANALYSIS_HOOKER_STRINGS)
                    .paramTypes(Context::class.java).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                SymbolResolver.findDeclaredMethodOrNull(cls, "a", Context::class.java)
            }
        )
        val processCrashMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.crash_analysis_process", crashClass, "a", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("no crash file found"))
                    .paramCount(0).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { !Modifier.isStatic(it.modifiers) && it.name == "a" && it.parameterCount == 0 && it.returnType == Void.TYPE }
            }
        )
        val reportCrashMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.crash_analysis_report", crashClass, "e", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.CRASH_ANALYSIS_REPORT_STRINGS)
                    .paramCount(0).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { !Modifier.isStatic(it.modifiers) && it.name == "e" && it.parameterCount == 0 && it.returnType == Void.TYPE }
            }
        )

        for ((hookId, methodRes) in listOf(
            "crash_xcrash_init_hooker" to xcrashHookerMethod,
            "crash_analysis_process" to processCrashMethod,
            "crash_analysis_report" to reportCrashMethod
        )) {
            registry.hookExecutable(ruleId, hookId, methodRes) { chain ->
                if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }

        // DumpLogProvider#{query, openFile}
        val dumpProviderClass = resolver.resolveClass(
            cacheKey = "xiaoai.dump_log_provider",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_DUMP_LOG_PROVIDER),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(bridge, listOf("com.miui.bugreport", "logDir"))
            }
        ).value
        val dumpQueryMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.dump_log_query", dumpProviderClass, "query",
            arrayOf(Uri::class.java, Array<String>::class.java, String::class.java, Array<String>::class.java, String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).name("query")
                    .paramTypes(Uri::class.java, Array<String>::class.java, String::class.java, Array<String>::class.java, String::class.java)
            },
            structuralFallback = null
        )
        val dumpOpenFileMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.dump_log_openfile", dumpProviderClass, "openFile",
            arrayOf(Uri::class.java, String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).name("openFile")
                    .paramTypes(Uri::class.java, String::class.java)
            },
            structuralFallback = null
        )
        for ((hookId, methodRes) in listOf(
            "dumplog_query" to dumpQueryMethod,
            "dumplog_openfile" to dumpOpenFileMethod
        )) {
            registry.hookExecutable(ruleId, hookId, methodRes) { chain ->
                if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cloud Rules: MiClaw LLM, ScreenCapture, Speech ASR, Translate/Taiyi, DictPack, VoiceAssist
    // -------------------------------------------------------------------------

    private fun installCloudAndIpcHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        imeServiceClass: Class<*>?
    ) {
        installVoiceAssistLaunchHooks(classLoader, resolver, registry, imeServiceClass)
        installMiclawLlmHooks(classLoader, resolver, registry)
        installScreenCaptureHooks(classLoader, resolver, registry)
        installTranslateAndTaiyiHooks(classLoader, resolver, registry)
        installSpeechAsrHooks(classLoader, resolver, registry)
        installDictPackSyncHooks(classLoader, resolver, registry)
    }

    private fun installVoiceAssistLaunchHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        imeServiceClass: Class<*>?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CLOUD_VOICEASSIST_LAUNCH

        // 1. AgentServiceConnection#a (k8.o)
        val agentConnClass = resolver.resolveClass(
            cacheKey = "xiaoai.agent_conn",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_AGENT_CONNECTION),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.AGENT_CONNECTION_STRINGS) }
        ).value
        val agentBindMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.agent_conn_bind", agentConnClass, "a",
            if (agentConnClass != null) arrayOf(agentConnClass) else emptyArray(),
            dexKitMatcher = if (agentConnClass != null) {
                { cls ->
                    MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.AGENT_BIND_STRINGS)
                        .paramTypes(cls).returnType(Void.TYPE)
                }
            } else null,
            structuralFallback = { cls ->
                val methods = safeDeclaredMethods(cls)
                methods.firstOrNull {
                    it.name == "a" && Modifier.isStatic(it.modifiers) && it.parameterCount == 1 &&
                        it.parameterTypes[0] == cls && it.returnType == Void.TYPE
                } ?: methods.firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterCount == 1 &&
                        it.parameterTypes[0] == cls && it.returnType == Void.TYPE
                }
            }
        )
        registry.hookExecutable(ruleId, "voiceassist_bind", agentBindMethod) { chain ->
            val cfg = ConfigBridge.current
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CLOUD_VOICEASSIST_LAUNCH) ||
                XiaoAiPrivacyPolicy.shouldBlockCloudService(cfg, HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM)
            ) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        // 2. VoiceAssist ACTION_ASSIST launcher (rh.b#L)
        val assistLaunchClass = resolver.resolveClass(
            cacheKey = "xiaoai.voiceassist_launcher_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_VOICEASSIST_LAUNCH),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.VOICEASSIST_ASSIST_STRINGS) }
        ).value
        val assistLaunchMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.voiceassist_launch", assistLaunchClass, "L",
            arrayOf(Context::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.VOICEASSIST_ASSIST_STRINGS)
                    .paramTypes(Context::class.java).returnType(Boolean::class.javaPrimitiveType!!)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    it.name == "L" && Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
            }
        )
        registry.hookExecutable(ruleId, "voiceassist_assist_intent", assistLaunchMethod) { chain ->
            val cfg = ConfigBridge.current
            if (XiaoAiPrivacyPolicy.shouldBlockVoiceAssistLaunch(cfg) ||
                XiaoAiPrivacyPolicy.shouldBlockCloudService(cfg, ruleId)
            ) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable XiaoAiOneTrackGuard.defaultReturnValue(
                    assistLaunchMethod.value?.returnType ?: Boolean::class.javaPrimitiveType!!
                )
            }
            chain.proceed()
        }

        // 3. rb.c#W(MiInputMethodService, z8.a) (SpeechQueryService handoff)
        val queryHelperClass = resolver.resolveClass(
            cacheKey = "xiaoai.voiceassist_query_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_VOICEASSIST_QUERY_HELPER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.VOICEASSIST_QUERY_STRINGS) }
        ).value
        val stateClass = resolver.resolveClass(
            cacheKey = "xiaoai.voiceassist_state_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_VOICEASSIST_STATE),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(
                    bridge,
                    listOf("sourceText", "IntentDisplay(label=", ", confidence=", ", sourceText=")
                )
            }
        ).value
        val queryLaunchMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.voiceassist_query_launch", queryHelperClass, "W",
            if (imeServiceClass != null && stateClass != null) arrayOf(imeServiceClass, stateClass) else emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.VOICEASSIST_QUERY_STRINGS)
                    .paramCount(2)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    it.name == "W" && Modifier.isStatic(it.modifiers) && it.parameterCount == 2
                }
            }
        )
        registry.hookExecutable(ruleId, "voiceassist_query_service", queryLaunchMethod) { chain ->
            val cfg = ConfigBridge.current
            if (XiaoAiPrivacyPolicy.shouldBlockVoiceAssistLaunch(cfg) ||
                XiaoAiPrivacyPolicy.shouldBlockCloudService(cfg, ruleId)
            ) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }
    }

    private fun installMiclawLlmHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM
        val miclawClass = resolver.resolveClass(
            cacheKey = "xiaoai.miclaw_manager",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_MICLAW_MANAGER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.MICLAW_MANAGER_STRINGS) }
        ).value

        val submitTextMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.miclaw_submit_text", miclawClass, "q",
            arrayOf(String::class.java, String::class.java, String::class.java, List::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.MICLAW_SUBMIT_STRINGS)
                    .paramTypes(String::class.java, String::class.java, String::class.java, List::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { method ->
                    !Modifier.isStatic(method.modifiers) && method.returnType != Void.TYPE &&
                        method.parameterTypes.contentEquals(
                            arrayOf(String::class.java, String::class.java, String::class.java, List::class.java)
                        )
                }
            }
        )

        val miclawFailedClass = resolver.resolveClass(
            cacheKey = "xiaoai.miclaw_failed",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_MICLAW_FAILED),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.MICLAW_FAILED_STRINGS) }
        ).value

        val miclawSessionClass = submitTextMethod.value?.returnType
            ?: resolver.resolveClass(
                cacheKey = "xiaoai.miclaw_session",
                fastPathCandidates = listOf(XiaoAiAnchors.CLASS_MICLAW_SESSION),
                dexKitFinder = { bridge ->
                    val mgrCls = miclawClass ?: return@resolveClass null
                    bridge.findClass(
                        FindClass.create().matcher(
                            ClassMatcher.create().methods {
                                add {
                                    name("<init>")
                                    paramTypes(
                                        mgrCls.name,
                                        "long",
                                        "long",
                                        "java.lang.String",
                                        "java.util.List",
                                        "java.lang.String",
                                        "java.lang.String"
                                    )
                                }
                            }
                        )
                    ).singleOrNull()?.descriptor
                }
            ).value
        val miclawHelper = MiclawFailureHelper(
            managerClass = miclawClass,
            sessionClass = miclawSessionClass,
            failedClass = miclawFailedClass
        )

        registry.hookExecutable(ruleId, "miclaw_submit_text", submitTextMethod) { chain ->
            val cfg = ConfigBridge.current
            val llmEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM)
            val screenEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE)
            val cursorEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP)
            val pkgEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)

            val text = chain.getArg(0) as? String ?: ""
            val tts = chain.getArg(1) as? String
            val featureId = chain.getArg(2) as? String

            if (XiaoAiPrivacyPolicy.shouldBlockCloudService(cfg, ruleId)) {
                val failedSession = miclawHelper.buildFailedSession(chain.thisObject, text, tts, featureId)
                if (failedSession != null) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable failedSession
                }
                return@hookExecutable chain.proceed()
            }

            if (llmEnabled || screenEnabled || cursorEnabled || pkgEnabled) {
                val modifiedArgs = chain.args.toTypedArray()
                var changed = false

                val sanitizedText = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(text, cfg)
                if (sanitizedText != text) {
                    modifiedArgs[0] = sanitizedText
                    changed = true
                    if (cursorEnabled) registry.incrementBlocked(HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP)
                    if (pkgEnabled) registry.incrementBlocked(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)
                }

                // Clear attachment ParcelFileDescriptor lists (`args[3]` in n0#q and defensively `args[1]` if List)
                if (screenEnabled || cursorEnabled || llmEnabled) {
                    if (modifiedArgs.size >= 4 && modifiedArgs[3] is List<*>) {
                        val list3 = modifiedArgs[3] as List<*>
                        if (list3.isNotEmpty()) {
                            closeAttachmentListFds(list3)
                            modifiedArgs[3] = emptyList<Any?>()
                            changed = true
                            registry.incrementBlocked(if (screenEnabled) HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE else ruleId)
                        }
                    }
                    if (modifiedArgs.size >= 2 && modifiedArgs[1] is List<*>) {
                        val list1 = modifiedArgs[1] as List<*>
                        if (list1.isNotEmpty()) {
                            closeAttachmentListFds(list1)
                            modifiedArgs[1] = emptyList<Any?>()
                            changed = true
                            registry.incrementBlocked(if (screenEnabled) HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE else ruleId)
                        }
                    }
                }

                if (changed) {
                    if (llmEnabled) registry.incrementBlocked(ruleId)
                    return@hookExecutable chain.proceed(modifiedArgs)
                }
            }

            chain.proceed()
        }

        registry.getReport(ruleId)?.let { miclawReport ->
            registry.reportRule(
                ruleId = HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP,
                status = miclawReport.status,
                matchedMethod = miclawReport.matchedMethod,
                source = miclawReport.source,
                detail = miclawReport.detail
            )
        }
    }

    private fun installScreenCaptureHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE
        val screenshotClass = resolver.resolveClass(
            cacheKey = "xiaoai.screenshot_capture",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SCREENSHOT_CAPTURE),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.SCREENSHOT_CAPTURE_STRINGS) }
        ).value

        val captureMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.screenshot_capture_call", screenshotClass, "c", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("android.window.ScreenCapture"))
                    .paramCount(2).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterCount == 2 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType && it.returnType == Void.TYPE
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull {
                it.name == name && Modifier.isStatic(it.modifiers) && it.parameterCount == 2 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType && it.returnType == Void.TYPE
            }
        }

        registry.hookExecutable(ruleId, "screenshot_capture", captureMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE)) {
                val callbackWrapper = chain.getArg(1)
                if (completeScreenshotCallbackWithNull(callbackWrapper)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
            }
            chain.proceed()
        }
    }

    private fun installTranslateAndTaiyiHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CLOUD_TRANSLATE_AND_TAIYI

        // 1. TranslateEngine#f (nc.r)
        val translateClass = resolver.resolveClass(
            cacheKey = "xiaoai.translate_engine",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TRANSLATE_ENGINE),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.TRANSLATE_ENGINE_STRINGS) }
        ).value
        val translateStartClass = resolver.resolveClass(
            cacheKey = "xiaoai.translate_start_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TRANSLATE_START),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.TRANSLATE_START_STRINGS) }
        ).value
        val translateMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.translate_start", translateClass, "f",
            if (translateStartClass != null) arrayOf(translateStartClass) else emptyArray(),
            dexKitMatcher = if (translateStartClass != null) {
                { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(translateStartClass) }
            } else null,
            structuralFallback = { cls ->
                val methods = safeDeclaredMethods(cls)
                methods.firstOrNull {
                    it.name == "f" && !Modifier.isStatic(it.modifiers) && it.parameterCount == 1 && it.returnType != Void.TYPE
                } ?: methods.firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.parameterCount == 1 &&
                        translateStartClass != null && it.parameterTypes[0] == translateStartClass && it.returnType != Void.TYPE
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
                bridge.findClass(FindClass.create().matcher(matcher)).singleOrNull()?.descriptor
            }
        ).value
        val translateErrorClass = resolver.resolveClass(
            cacheKey = "xiaoai.translate_error_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TRANSLATE_ERROR),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.TRANSLATE_ERROR_STRINGS) }
        ).value
        val translateHelper = TranslateBlockHelper(translateIdleClass, translateErrorClass)

        registry.hookExecutable(ruleId, "translate_engine", translateMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudService(ConfigBridge.current, ruleId)) {
                val idleState = translateHelper.failAndReturnIdle(chain.getArg(0))
                if (idleState != null) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable idleState
                }
            }
            chain.proceed()
        }

        // 2. Taiyi AIService connection (rc.j#d) & execution (rc.b#e preserving isLocalAffinity())
        val taiyiServiceClass = resolver.resolveClass(
            cacheKey = "xiaoai.taiyi_service",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TAIYI_SERVICE),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.TAIYI_SERVICE_STRINGS) }
        ).value
        val taiyiConnectMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.taiyi_connect", taiyiServiceClass, "d", emptyArray(),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.TAIYI_CONNECT_STRINGS).paramCount(2) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.parameterCount == 2 && it.parameterTypes[0] == Context::class.java
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull {
                it.name == name && !Modifier.isStatic(it.modifiers) && it.parameterCount == 2 && it.parameterTypes[0] == Context::class.java
            }
        }
        registry.hookExecutable(ruleId, "taiyi_aiservice_connect", taiyiConnectMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudService(ConfigBridge.current, ruleId)) {
                notifyConnectCallbackFailed(chain.getArg(1), XiaoAiAnchors.TAIYI_BLOCKED_CODE, "Cloud AI blocked")
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        val taiyiManagerClass = resolver.resolveClass(
            cacheKey = "xiaoai.taiyi_manager",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TAIYI_MANAGER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.TAIYI_MANAGER_STRINGS) }
        ).value
        val aiRequestClass = resolver.resolveClass(
            cacheKey = "xiaoai.taiyi_request_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TAIYI_REQUEST),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings(listOf("AIRequest{\nid = ", ", \nheaders = ", ", \nbody = "), StringMatchType.Equals)
                            .methods { add { name("getApi") } }
                    )
                ).singleOrNull()?.descriptor
            }
        ).value
        val aiResponseClass = resolver.resolveClass(
            cacheKey = "xiaoai.taiyi_response_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_TAIYI_RESPONSE),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings(listOf("AIResponse{\nid = ", ", \nheaders = "))
                            .methods { add { name("isSuccessful") } }
                    )
                ).singleOrNull()?.descriptor
            }
        ).value
        val taiyiExecMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.taiyi_exec", taiyiManagerClass, "e",
            if (aiRequestClass != null) arrayOf(aiRequestClass) else emptyArray(),
            dexKitMatcher = if (aiRequestClass != null && aiResponseClass != null) {
                { cls ->
                    MethodMatcher.create()
                        .declaredClass(cls.name)
                        .paramTypes(aiRequestClass)
                        .returnType(aiResponseClass)
                }
            } else null,
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.parameterCount == 1 &&
                        it.parameterTypes[0].name == XiaoAiAnchors.CLASS_TAIYI_REQUEST &&
                        it.returnType.name == XiaoAiAnchors.CLASS_TAIYI_RESPONSE
                }
            }
        )
        val aiResponseHelper = TaiyiErrorResponseHelper(aiRequestClass, aiResponseClass)
        registry.hookExecutable(ruleId, "taiyi_aiservice_exec", taiyiExecMethod) { chain ->
            val request = chain.getArg(0)
            val localAffinity = isTaiyiLocalAffinity(request)
            if (!localAffinity && XiaoAiPrivacyPolicy.shouldBlockCloudService(ConfigBridge.current, ruleId)) {
                val errResp = aiResponseHelper.createBlockedResponse(request, XiaoAiAnchors.TAIYI_BLOCKED_CODE)
                if (errResp != null) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable errResp
                }
            }
            chain.proceed()
        }
    }

    private fun installSpeechAsrHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CLOUD_SPEECH_ASR
        val speechClass = resolver.resolveClass(
            cacheKey = "xiaoai.speech_asr",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SPEECH_ASR_MANAGER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.SPEECH_ASR_STRINGS) }
        ).value

        // 1. m8.h#m(String fromApp, boolean)
        val speechStartMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.speech_asr_start", speechClass, "m",
            arrayOf(String::class.java, Boolean::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.SPEECH_ASR_START_STRINGS)
                    .paramTypes(String::class.java, Boolean::class.javaPrimitiveType!!).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                        it.parameterTypes.contentEquals(arrayOf(String::class.java, Boolean::class.javaPrimitiveType!!))
                }
            }
        )
        registry.hookExecutable(ruleId, "speech_asr_start", speechStartMethod) { chain ->
            val asrEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CLOUD_SPEECH_ASR)
            val pkgEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)
            if (XiaoAiPrivacyPolicy.shouldBlockCloudService(ConfigBridge.current, ruleId)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            if (asrEnabled || pkgEnabled) {
                val args = chain.args
                if (args.isNotEmpty() && args[0] is String && (args[0] as String).isNotEmpty()) {
                    if (asrEnabled) registry.incrementBlocked(ruleId)
                    if (pkgEnabled) registry.incrementBlocked(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)
                    val modified = args.toTypedArray()
                    modified[0] = ""
                    return@hookExecutable chain.proceed(modified)
                }
            }
            chain.proceed()
        }

        // 2. m8.h#b(m8.h, int, boolean, String fromApp)
        val speechPrepareMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.speech_asr_prepare", speechClass, "b",
            if (speechClass != null) arrayOf(speechClass, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, String::class.java) else emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).usingStrings(listOf("hotwordsJson"))
                    .paramTypes(cls, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(
                        arrayOf(cls, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, String::class.java)
                    )
                }
            }
        )
        registry.hookExecutable(ruleId, "speech_asr_prepare", speechPrepareMethod) { chain ->
            val asrEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CLOUD_SPEECH_ASR)
            val pkgEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)
            if (XiaoAiPrivacyPolicy.shouldBlockCloudService(ConfigBridge.current, ruleId)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            if (asrEnabled || pkgEnabled) {
                val args = chain.args
                if (args.size >= 4 && args[3] is String && (args[3] as String).isNotEmpty()) {
                    if (asrEnabled) registry.incrementBlocked(ruleId)
                    if (pkgEnabled) registry.incrementBlocked(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)
                    val modified = args.toTypedArray()
                    modified[3] = ""
                    return@hookExecutable chain.proceed(modified)
                }
            }
            chain.proceed()
        }

        // 3. lc.f#b(Application, ComponentName) (SpeechService binder connection)
        val speechMgrClass = resolver.resolveClass(
            cacheKey = "xiaoai.speech_manager_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SPEECH_MANAGER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.SPEECH_MANAGER_STRINGS) }
        ).value
        val speechBindMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.speech_manager_bind", speechMgrClass, "b", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("com.xiaomi.speech.action.SPEECH")).paramCount(2).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterCount == 2 && it.returnType == Void.TYPE
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull {
                it.name == name && Modifier.isStatic(it.modifiers) && it.parameterCount == 2
            }
        }
        registry.hookExecutable(ruleId, "speech_manager_bind", speechBindMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudService(ConfigBridge.current, ruleId)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        // 4. mb.k3#h(Object) (hotwordsJson lambda case 11 inside SpeechAsrManager)
        val hotwordClosureClass = resolver.resolveClass(
            cacheKey = "xiaoai.hotword_closure_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_HOTWORD_CLOSURE),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings(XiaoAiAnchors.HOTWORD_CLOSURE_STRINGS, StringMatchType.Equals)
                            .methods { add { name("h"); paramTypes(Any::class.java) } }
                    )
                ).singleOrNull()?.descriptor
            }
        ).value
        val hotwordClosureMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_closure_h", hotwordClosureClass, "h", arrayOf(Any::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).name("h").paramTypes(Any::class.java)
            },
            structuralFallback = { cls -> SymbolResolver.findDeclaredMethodOrNull(cls, "h", Any::class.java) }
        )
        registry.hookExecutable(ruleId, "speech_asr_hotword_closure", hotwordClosureMethod) { chain ->
            if (isHotwordLambdaCase(chain.thisObject, 11)) {
                val asrEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CLOUD_SPEECH_ASR)
                val hotwordEnabled = ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)
                if (asrEnabled || hotwordEnabled) {
                    if (asrEnabled) registry.incrementBlocked(ruleId)
                    if (hotwordEnabled) registry.incrementBlocked(HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)
                    return@hookExecutable emptyList<String>()
                }
            }
            chain.proceed()
        }
    }

    private fun installDictPackSyncHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CLOUD_DICTPACK_SYNC

        // 1. u9.d#i(Context, boolean, boolean) (WorkManager DictPackSyncScheduler)
        val schedulerClass = resolver.resolveClass(
            cacheKey = "xiaoai.dictpack_scheduler",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_DICTPACK_SCHEDULER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.DICTPACK_SCHEDULER_STRINGS) }
        ).value
        val schedulerSyncMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.dictpack_scheduler_i", schedulerClass, "i",
            arrayOf(Context::class.java, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("hot_dict_sync_immediate"))
                    .paramTypes(Context::class.java, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(
                        arrayOf(Context::class.java, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
                    )
                }
            }
        )
        registry.hookExecutable(ruleId, "dictpack_scheduler_sync", schedulerSyncMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudService(ConfigBridge.current, ruleId)) {
                val args = chain.args
                if (args.size >= 3 && (args[1] == true || args[2] == true)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable chain.proceed(arrayOf(args[0], false, false))
                }
            }
            chain.proceed()
        }

        // 2. WorkManager DictPack & HotDict Workers (#b)
        val workSuccessClass = resolver.resolveClass(
            cacheKey = "xiaoai.work_result_success",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_WORK_RESULT_SUCCESS),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.WORK_RESULT_SUCCESS_STRINGS) }
        ).value
        val workSuccessCtor = runCatching { workSuccessClass?.getDeclaredConstructor()?.apply { isAccessible = true } }.getOrNull()

        for ((hookId, workerClassName, workerStrings) in listOf(
            Triple(
                "dictpack_catalog_worker",
                XiaoAiAnchors.CLASS_DICTPACK_CATALOG_WORKER,
                listOf("DictPackCatalogWorker", "catalog.worker", "dict_pack_catalog_periodic")
            ),
            Triple(
                "dictpack_download_worker",
                XiaoAiAnchors.CLASS_DICTPACK_DOWNLOAD_WORKER,
                listOf("DictPackDownloadWorker", "download database unavailable; retry later")
            ),
            Triple(
                "hotdict_sync_worker",
                XiaoAiAnchors.CLASS_HOTDICT_SYNC_WORKER,
                listOf("HotDictSyncWorker")
            )
        )) {
            val workerCls = resolver.resolveClass(
                cacheKey = "xiaoai.$hookId",
                fastPathCandidates = listOf(workerClassName),
                dexKitFinder = { bridge ->
                    bridge.findClass(
                        FindClass.create().matcher(
                            ClassMatcher.create()
                                .superClass("androidx.work.CoroutineWorker")
                                .usingStrings(workerStrings)
                        )
                    ).singleOrNull()?.descriptor
                }
            ).value
            val doWorkMethod = resolveMethodOnClass(
                resolver, classLoader, "xiaoai.${hookId}_b", workerCls, "b", emptyArray(),
                dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).name("b").paramCount(1) },
                structuralFallback = { cls ->
                    safeDeclaredMethods(cls).firstOrNull { !Modifier.isStatic(it.modifiers) && it.name == "b" && it.parameterCount == 1 }
                }
            ) { cls, name ->
                safeDeclaredMethods(cls).firstOrNull { !Modifier.isStatic(it.modifiers) && it.name == name && it.parameterCount == 1 }
            }
            registry.hookExecutable(ruleId, hookId, doWorkMethod) { chain ->
                if (XiaoAiPrivacyPolicy.shouldBlockCloudService(ConfigBridge.current, ruleId)) {
                    val successResult = runCatching { workSuccessCtor?.newInstance() }.getOrNull()
                    if (successResult != null) {
                        registry.incrementBlocked(ruleId)
                        return@hookExecutable successResult
                    }
                }
                chain.proceed()
            }
        }

        // 3. i.o0#b(String, Map, n9.z) (AES catalog endpoint HTTP client)
        val catalogHttpClass = resolver.resolveClass(
            cacheKey = "xiaoai.catalog_http_client",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_CATALOG_HTTP_CLIENT),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.CATALOG_HTTP_CLIENT_STRINGS) }
        ).value
        val catalogHttpMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.catalog_http_b", catalogHttpClass, "b", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("Catalog requires HTTPS and the AES catalog endpoint"))
                    .paramCount(3).returnType(String::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.parameterCount == 3 &&
                        it.parameterTypes[0] == String::class.java &&
                        Map::class.java.isAssignableFrom(it.parameterTypes[1]) &&
                        it.returnType == String::class.java
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull {
                it.name == name && !Modifier.isStatic(it.modifiers) && it.parameterCount == 3 &&
                    it.parameterTypes[0] == String::class.java && Map::class.java.isAssignableFrom(it.parameterTypes[1])
            }
        }
        registry.hookExecutable(ruleId, "dictpack_http_b", catalogHttpMethod) { chain ->
            if (XiaoAiPrivacyPolicy.shouldBlockCloudService(ConfigBridge.current, ruleId)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable ""
            }
            if (ConfigBridge.isPointEnabled(targetPkg, ruleId)) {
                val args = chain.args
                if (args.size >= 2 && args[1] is Map<*, *>) {
                    val sanitized = XiaoAiPrivacyPolicy.sanitizeDictSyncParams(args[1] as Map<*, *>)
                    if (sanitized != null) {
                        registry.incrementBlocked(ruleId)
                        val modified = args.toTypedArray()
                        modified[1] = sanitized
                        return@hookExecutable chain.proceed(modified)
                    }
                }
            }
            chain.proceed()
        }
    }

    // -------------------------------------------------------------------------
    // Context Strip, Clipboard & Hotword Rules
    // -------------------------------------------------------------------------

    private fun installTopActivityAndPkgHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        imeServiceClass: Class<*>?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG

        // 1. MiInputMethodService#resolveTopActivityName(String)
        val resolveTopActivityMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.ime_resolve_top_activity", imeServiceClass,
            XiaoAiAnchors.METHOD_IME_RESOLVE_TOP_ACTIVITY, arrayOf(String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).name(XiaoAiAnchors.METHOD_IME_RESOLVE_TOP_ACTIVITY)
                    .paramTypes(String::class.java).returnType(String::class.java)
            },
            structuralFallback = { cls ->
                SymbolResolver.findDeclaredMethodOrNull(cls, XiaoAiAnchors.METHOD_IME_RESOLVE_TOP_ACTIVITY, String::class.java)
            }
        )
        registry.hookExecutable(ruleId, "ime_resolve_top_activity", resolveTopActivityMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        // 2. pd.a#x(MiInputMethodService) -> host app label resolution
        val appLabelClass = resolver.resolveClass(
            cacheKey = "xiaoai.app_label_helper",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_APP_LABEL_HELPER),
            dexKitFinder = { bridge ->
                imeServiceClass?.let { imeCls ->
                    bridge.findClass(
                        FindClass.create().matcher(
                            ClassMatcher.create().methods {
                                add {
                                    paramTypes(imeCls)
                                    returnType(String::class.java)
                                    usingStrings(XiaoAiAnchors.APP_LABEL_HELPER_STRINGS)
                                }
                            }
                        )
                    ).singleOrNull()?.descriptor
                }
            }
        ).value
        val appLabelMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.app_label_x", appLabelClass, "x",
            if (imeServiceClass != null) arrayOf(imeServiceClass) else emptyArray(),
            dexKitMatcher = if (imeServiceClass != null) {
                { cls ->
                    MethodMatcher.create().declaredClass(cls.name)
                        .paramTypes(imeServiceClass).returnType(String::class.java)
                        .usingStrings(XiaoAiAnchors.APP_LABEL_HELPER_STRINGS)
                }
            } else null,
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterCount == 1 &&
                        (imeServiceClass == null || it.parameterTypes[0] == imeServiceClass) &&
                        it.returnType == String::class.java && it.name == "x"
                }
            }
        )
        registry.hookExecutable(ruleId, "app_label_x", appLabelMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }
    }

    private fun installClipboardPrivacyHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CLIP_BACKGROUND_GUARD
        val clipboardMonitorClass = resolver.resolveClass(
            cacheKey = "xiaoai.clipboard_monitor",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_CLIPBOARD_MONITOR),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.CLIPBOARD_MONITOR_STRINGS) }
        ).value

        val onClipChangedMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.clipboard_monitor_changed", clipboardMonitorClass, "c", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).usingStrings(listOf("sensitive clip ignored"))
                    .paramCount(0).returnType(Void.TYPE)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == Void.TYPE && it.name == "c"
                }
            }
        )

        registry.hookExecutable(ruleId, "clipboard_monitor_changed", onClipChangedMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CLIP_BACKGROUND_GUARD)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }
    }

    private fun installHotwordAicrHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        unitSingleton: Any?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR

        // 1. ca.w#{a, b, e, f} (HotwordRemoteClient)
        val hotwordClientClass = resolver.resolveClass(
            cacheKey = "xiaoai.hotword_client",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_HOTWORD_CLIENT),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.HOTWORD_CLIENT_STRINGS) }
        ).value

        val hotwordInitMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_client_init", hotwordClientClass, "a", arrayOf(Context::class.java),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(Context::class.java).returnType(Void.TYPE) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(Context::class.java)) && it.returnType == Void.TYPE
                }
            }
        )
        val hotwordBinderMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_client_binder", hotwordClientClass, "b", arrayOf(String::class.java),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(String::class.java) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(String::class.java)) && it.returnType != Void.TYPE
                }
            }
        )
        val hotwordStaticMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_client_e", hotwordClientClass, "e", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(XiaoAiAnchors.HOTWORD_STATIC_STRINGS, StringMatchType.Equals)
                    .paramCount(4)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.name == "e" && it.parameterCount == 4 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType && it.parameterTypes[2] == String::class.java
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull {
                it.name == name && !Modifier.isStatic(it.modifiers) && it.parameterCount == 4 && it.parameterTypes[2] == String::class.java
            }
        }
        val hotwordPagedMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_client_f", hotwordClientClass, "f", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.HOTWORD_STATIC_PAGED_STRINGS).paramCount(4)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.name == "f" && it.parameterCount == 4 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType && it.parameterTypes[2] == String::class.java
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull {
                it.name == name && !Modifier.isStatic(it.modifiers) && it.parameterCount == 4 && it.parameterTypes[2] == String::class.java
            }
        }

        val deferredHotwordContext = AtomicReference<WeakReference<Context>?>(null)

        registry.hookExecutable(ruleId, "hotword_client_init", hotwordInitMethod) { chain ->
            val ctx = chain.getArg(0) as? Context
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)) {
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
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            val pendingCtx = deferredHotwordContext.getAndSet(null)?.get()
            if (pendingCtx != null) {
                hotwordInitMethod.value?.let { initM ->
                    runCatching {
                        initM.isAccessible = true
                        initM.invoke(null, pendingCtx)
                    }
                }
            }
            chain.proceed()
        }

        for ((hookId, methodRes) in listOf(
            "hotword_client_static_e" to hotwordStaticMethod,
            "hotword_client_paged_f" to hotwordPagedMethod
        )) {
            registry.hookExecutable(ruleId, hookId, methodRes) { chain ->
                if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable ArrayList<Any?>()
                }
                chain.proceed()
            }
        }

        // 2. ca.u#s(Object) case 0 (HotwordRemoteClient init coroutine)
        val hotwordInitTaskClass = resolver.resolveClass(
            cacheKey = "xiaoai.hotword_init_task",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_HOTWORD_INIT_TASK),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.HOTWORD_INIT_TASK_STRINGS) }
        ).value
        val hotwordInitTaskMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_init_task_s", hotwordInitTaskClass, "s", arrayOf(Any::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("HotwordRemoteClient init done: supported="))
                    .paramTypes(Any::class.java)
            },
            structuralFallback = { cls -> SymbolResolver.findDeclaredMethodOrNull(cls, "s", Any::class.java) }
        )
        registry.hookExecutable(ruleId, "hotword_init_task", hotwordInitTaskMethod) { chain ->
            if (isHotwordLambdaCase(chain.thisObject, 0) &&
                ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)
            ) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable unitSingleton ?: chain.proceed()
            }
            chain.proceed()
        }

        // 3. ba.b#{a, c, f, h} (HotwordEditTracker including ShadowBuffer init c())
        val editTrackerClass = resolver.resolveClass(
            cacheKey = "xiaoai.hotword_edit_tracker",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_HOTWORD_EDIT_TRACKER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.HOTWORD_TRACKER_STRINGS) }
        ).value
        val trackerBindEditorMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_tracker_editor", editTrackerClass, "a", arrayOf(EditorInfo::class.java),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(EditorInfo::class.java) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull { !Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(EditorInfo::class.java)) }
            }
        )
        val trackerShadowInitMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_tracker_shadow_c", editTrackerClass, "c", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .usingStrings(listOf("textProvider 返回 null，无法初始化 ShadowBuffer")).paramCount(0)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.name == "c" && it.parameterCount == 0 && it.returnType != Void.TYPE
                }
            }
        )
        val trackerSelectionMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_tracker_selection", editTrackerClass, "f", arrayOf(Int::class.javaPrimitiveType!!),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).paramTypes(Int::class.javaPrimitiveType!!).returnType(Void.TYPE) },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.name == "f" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType!!))
                }
            }
        )
        val trackerSnapshotMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.hotword_tracker_snapshot", editTrackerClass, "h", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .paramCount(0)
                    .returnType(Void.TYPE)
                    .invokeMethods {
                        add { declaredClass(cls.name); paramCount(0); returnType(Void.TYPE) }
                        add { declaredClass(cls.name); paramTypes(Boolean::class.javaPrimitiveType!!); returnType(Void.TYPE) }
                    }
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.name == "h" && it.parameterCount == 0 && it.returnType == Void.TYPE
                }
            }
        )

        for ((hookId, methodRes) in listOf(
            "hotword_tracker_bind_editor" to trackerBindEditorMethod,
            "hotword_tracker_shadow_init" to trackerShadowInitMethod,
            "hotword_tracker_selection" to trackerSelectionMethod,
            "hotword_tracker_snapshot" to trackerSnapshotMethod
        )) {
            registry.hookExecutable(ruleId, hookId, methodRes) { chain ->
                if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)) {
                    registry.incrementBlocked(ruleId)
                    return@hookExecutable null
                }
                chain.proceed()
            }
        }

        // 4. fb.y5#{a, b, c} (VoiceHotwordCache)
        val voiceHotwordCacheClass = resolver.resolveClass(
            cacheKey = "xiaoai.voice_hotword_cache",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_VOICE_HOTWORD_CACHE),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.VOICE_HOTWORD_CACHE_STRINGS) }
        ).value
        val y5MethodA = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.voice_hotword_a", voiceHotwordCacheClass, "a", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name)
                    .modifiers(Modifier.STATIC)
                    .paramCount(3)
                    .returnType(Any::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.name == "a" && it.parameterCount == 3 &&
                        it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == String::class.java
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull {
                Modifier.isStatic(it.modifiers) && it.name == name && it.parameterCount == 3 &&
                    it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == String::class.java
            }
        }
        val y5MethodB = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.voice_hotword_b", voiceHotwordCacheClass, "b", emptyArray(),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).paramCount(2).returnType(Any::class.java)
            },
            structuralFallback = { cls ->
                safeDeclaredMethods(cls).firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.name == "b" && it.parameterCount == 2 && it.parameterTypes[0] == String::class.java
                }
            }
        ) { cls, name ->
            safeDeclaredMethods(cls).firstOrNull {
                Modifier.isStatic(it.modifiers) && it.name == name && it.parameterCount == 2 && it.parameterTypes[0] == String::class.java
            }
        }
        val y5MethodC = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.voice_hotword_c", voiceHotwordCacheClass, "c",
            arrayOf(String::class.java, String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).usingStrings(listOf("from_app", "asr_text"))
                    .paramTypes(String::class.java, String::class.java).returnType(String::class.java)
            },
            structuralFallback = { cls ->
                SymbolResolver.findDeclaredMethodOrNull(cls, "c", String::class.java, String::class.java)
            }
        )

        registry.hookExecutable(ruleId, "voice_hotword_cache_a", y5MethodA) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)) {
                registry.incrementBlocked(ruleId)
                val args = chain.args
                if (args.size >= 3) {
                    val modified = args.toTypedArray()
                    modified[0] = ""
                    modified[1] = ""
                    return@hookExecutable chain.proceed(modified)
                }
            }
            chain.proceed()
        }
        registry.hookExecutable(ruleId, "voice_hotword_cache_b", y5MethodB) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)) {
                registry.incrementBlocked(ruleId)
                val args = chain.args
                if (args.size >= 2) {
                    val modified = args.toTypedArray()
                    modified[0] = ""
                    return@hookExecutable chain.proceed(modified)
                }
            }
            chain.proceed()
        }
        registry.hookExecutable(ruleId, "voice_hotword_cache_c", y5MethodC) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable XiaoAiPrivacyPolicy.sanitizeHotwordJson("{\"from_app\":\"\"}")
            }
            chain.proceed()
        }
    }

    // -------------------------------------------------------------------------
    // System Cloud Control Rule
    // -------------------------------------------------------------------------

    private fun installSystemCloudControlHooks(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry,
        unitSingleton: Any?
    ) {
        val ruleId = HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL

        // 1. o8.c#a(ContentResolver, String)
        val cloudHelperClass = resolver.resolveClass(
            cacheKey = "xiaoai.cloud_control_helper",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_CLOUD_CONTROL_HELPER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.CLOUD_CONTROL_STRINGS) }
        ).value
        val cloudSnapshotMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.cloud_control_a", cloudHelperClass, "a",
            arrayOf(ContentResolver::class.java, String::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.CLOUD_CONTROL_STRINGS)
                    .paramTypes(ContentResolver::class.java, String::class.java)
            },
            structuralFallback = { cls ->
                SymbolResolver.findDeclaredMethodOrNull(cls, "a", ContentResolver::class.java, String::class.java)
            }
        )
        registry.hookExecutable(ruleId, "cloud_control_snapshot", cloudSnapshotMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable emptyList<Any?>()
            }
            chain.proceed()
        }

        // 2. o8.b#s(Object)
        val cloudFlowClass = resolver.resolveClass(
            cacheKey = "xiaoai.cloud_control_flow",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_CLOUD_CONTROL_FLOW),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.CLOUD_CONTROL_FLOW_STRINGS) }
        ).value
        val cloudFlowMethod = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.cloud_control_flow_s", cloudFlowClass, "s", arrayOf(Any::class.java),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).usingStrings(XiaoAiAnchors.CLOUD_CONTROL_FLOW_STRINGS)
                    .paramTypes(Any::class.java)
            },
            structuralFallback = { cls -> SymbolResolver.findDeclaredMethodOrNull(cls, "s", Any::class.java) }
        )
        registry.hookExecutable(ruleId, "cloud_control_flow", cloudFlowMethod) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable unitSingleton ?: chain.proceed()
            }
            chain.proceed()
        }

        // 3. android.provider.MiuiSettings$SettingsCloudData (MIUI/HyperOS system API, optional on AOSP/Robolectric)
        val miuiCloudDataClass = SymbolResolver.loadClassOrNull("android.provider.MiuiSettings\$SettingsCloudData", classLoader)
        if (miuiCloudDataClass != null) {
            registry.hookAllMethods(ruleId, "miui_settings_cloud_data", miuiCloudDataClass, "getCloudDataList") { chain ->
                if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL)) {
                    registry.incrementBlocked(ruleId)
                    return@hookAllMethods emptyList<Any?>()
                }
                chain.proceed()
            }
        }

        // 4. ScenarioRecognition & ub.a#onTransact
        val scenarioClass = resolver.resolveClass(
            cacheKey = "xiaoai.scenario_recognition",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SCENARIO_RECOGNITION),
            dexKitFinder = { bridge ->
                findClassDescriptorByStrings(
                    bridge,
                    listOf("miui.scenariorecognition.ScenarioRecognitionManager", "CoreScenarioRecognition init time: ")
                )
            }
        ).value
        val scenarioGetInstance = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.scenario_get_instance", scenarioClass, "getInstance", emptyArray(),
            dexKitMatcher = { cls -> MethodMatcher.create().declaredClass(cls.name).name("getInstance").paramCount(0) },
            structuralFallback = { cls -> SymbolResolver.findDeclaredMethodOrNull(cls, "getInstance") }
        )
        registry.hookExecutable(ruleId, "scenario_recognition_instance", scenarioGetInstance) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable null
            }
            chain.proceed()
        }

        val scenarioBinderClass = resolver.resolveClass(
            cacheKey = "xiaoai.scenario_binder",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_SCENARIO_BINDER),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.SCENARIO_BINDER_STRINGS) }
        ).value
        val scenarioOnTransact = resolveMethodOnClass(
            resolver, classLoader, "xiaoai.scenario_on_transact", scenarioBinderClass, "onTransact",
            arrayOf(Int::class.javaPrimitiveType!!, Parcel::class.java, Parcel::class.java, Int::class.javaPrimitiveType!!),
            dexKitMatcher = { cls ->
                MethodMatcher.create().declaredClass(cls.name).name("onTransact")
                    .usingStrings(XiaoAiAnchors.SCENARIO_BINDER_STRINGS)
            },
            structuralFallback = { cls ->
                SymbolResolver.findDeclaredMethodOrNull(
                    cls, "onTransact",
                    Int::class.javaPrimitiveType!!, Parcel::class.java, Parcel::class.java, Int::class.javaPrimitiveType!!
                )
            }
        )
        registry.hookExecutable(ruleId, "scenario_binder_transact", scenarioOnTransact) { chain ->
            if (ConfigBridge.isPointEnabled(targetPkg, HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL)) {
                registry.incrementBlocked(ruleId)
                return@hookExecutable true
            }
            chain.proceed()
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

    private fun resolveImeServiceClass(resolver: SymbolResolver): Class<*>? =
        resolver.resolveClass(
            cacheKey = "xiaoai.ime_service_cls",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_MI_IME_SERVICE),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .superClass("android.inputmethodservice.InputMethodService")
                            .usingStrings(listOf("MiInputMethodService", "onReportTypingTimeOfIme", "isInternalTestMode"))
                    )
                ).singleOrNull()?.descriptor
            }
        ).value

    private fun resolveOneTrackBaseImpClass(resolver: SymbolResolver): Class<*>? =
        resolver.resolveClass(
            cacheKey = "xiaoai.onetrack_base_imp",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_ONETRACK_BASE_IMP),
            dexKitFinder = { bridge -> findClassDescriptorByStrings(bridge, XiaoAiAnchors.ONETRACK_BASE_IMP_STRINGS) }
        ).value

    private fun findClassDescriptorByStrings(bridge: DexKitExecutor, strings: List<String>): String? =
        bridge.findClass(
            FindClass.create().matcher(ClassMatcher.create().usingStrings(strings))
        ).singleOrNull()?.descriptor

    private fun resolveMethodByTypeNames(
        resolver: SymbolResolver,
        classLoader: ClassLoader,
        cacheKey: String,
        targetClass: Class<*>?,
        methodName: String,
        paramTypeNames: Array<String>,
        returnTypeName: String? = null
    ): ResolveResult<Method> {
        return resolver.resolveMethod(
            cacheKey = cacheKey,
            fastPath = { cl ->
                val cls = targetClass ?: return@resolveMethod null
                val resolvedCls = if (cls.classLoader === cl) cls else SymbolResolver.loadClassOrNull(cls.name, cl)
                    ?: return@resolveMethod null
                val paramClasses = paramTypeNames.map { typeName ->
                    when (typeName) {
                        "boolean" -> Boolean::class.javaPrimitiveType!!
                        "byte" -> Byte::class.javaPrimitiveType!!
                        "short" -> Short::class.javaPrimitiveType!!
                        "char" -> Char::class.javaPrimitiveType!!
                        "int" -> Int::class.javaPrimitiveType!!
                        "long" -> Long::class.javaPrimitiveType!!
                        "float" -> Float::class.javaPrimitiveType!!
                        "double" -> Double::class.javaPrimitiveType!!
                        else -> SymbolResolver.loadClassOrNull(typeName, resolvedCls.classLoader ?: cl)
                            ?: return@resolveMethod null
                    }
                }.toTypedArray()
                SymbolResolver.findDeclaredMethodOrNull(resolvedCls, methodName, *paramClasses)
            },
            dexKitFinder = { bridge ->
                val cls = targetClass ?: return@resolveMethod null
                val matcher = MethodMatcher.create()
                    .declaredClass(cls.name)
                    .name(methodName)
                    .paramTypes(*paramTypeNames)
                if (returnTypeName != null) {
                    matcher.returnType(returnTypeName)
                }
                bridge.findMethod(FindMethod.create().matcher(matcher)).singleOrNull()?.descriptor
            }
        )
    }

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
                        .singleOrNull()?.descriptor
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

    private fun resolveKotlinUnitInstance(resolver: SymbolResolver, classLoader: ClassLoader): Any? {
        val unitClass = resolver.resolveClass(
            cacheKey = "xiaoai.kotlin_unit",
            fastPathCandidates = listOf(XiaoAiAnchors.CLASS_KOTLIN_UNIT),
            dexKitFinder = { bridge ->
                bridge.findClass(
                    FindClass.create().matcher(
                        ClassMatcher.create()
                            .usingStrings(XiaoAiAnchors.KOTLIN_UNIT_STRINGS, StringMatchType.Equals)
                            .fields { count(1) }
                            .methods { add { name("toString"); paramCount(0); returnType(String::class.java) } }
                    )
                ).singleOrNull()?.descriptor
            }
        ).value ?: return null
        return runCatching {
            safeDeclaredFields(unitClass).firstOrNull {
                Modifier.isStatic(it.modifiers) && it.type == unitClass
            }?.apply { isAccessible = true }?.get(null)
        }.getOrNull()
    }

    private fun isHotwordLambdaCase(instance: Any?, expectedCase: Int): Boolean {
        instance ?: return false
        return runCatching {
            safeDeclaredFields(instance.javaClass).any { field ->
                !Modifier.isStatic(field.modifiers) &&
                    field.type == Int::class.javaPrimitiveType &&
                    field.apply { isAccessible = true }.getInt(instance) == expectedCase
            }
        }.getOrDefault(false)
    }

    private fun isTaiyiAppTrackRunnableInstance(instance: Any?): Boolean {
        instance ?: return false
        return runCatching {
            safeDeclaredFields(instance.javaClass).any { field ->
                !Modifier.isStatic(field.modifiers) &&
                    !field.type.isPrimitive &&
                    field.apply { isAccessible = true }.get(instance)?.javaClass?.name == XiaoAiAnchors.CLASS_TAIYI_REQUEST
            }
        }.getOrDefault(false)
    }

    private fun isTaiyiLocalAffinity(request: Any?): Boolean {
        request ?: return false
        return runCatching {
            val headers = request.javaClass.getMethod("getHeaders").invoke(request) ?: return false
            val res = headers.javaClass.getMethod("isLocalAffinity").invoke(headers)
            res as? Boolean ?: false
        }.getOrDefault(false)
    }

    private fun closeAttachmentListFds(attachments: List<*>) {
        for (item in attachments) {
            if (item == null) continue
            runCatching {
                if (item is ParcelFileDescriptor) {
                    item.close()
                } else {
                    for (field in safeDeclaredFields(item.javaClass)) {
                        if (!Modifier.isStatic(field.modifiers) &&
                            ParcelFileDescriptor::class.java.isAssignableFrom(field.type)
                        ) {
                            field.isAccessible = true
                            (field.get(item) as? ParcelFileDescriptor)?.close()
                        }
                    }
                }
            }
        }
    }

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
        holderClass: Class<*>?,
        private val passwordField: Field?
    ) {

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

    private class TaiyiErrorResponseHelper(
        private val requestClass: Class<*>?,
        private val responseClass: Class<*>?
    ) {

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
