package com.scluzlep.miimeprivacy.xposed.targets

import android.app.Activity
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.wifi.WifiInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.webkit.WebView
import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.emoji.SogouDualSkinPopupHook
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.DexKitExecutor
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ImeHookPack
import com.scluzlep.miimeprivacy.xposed.ImePackInstallResult
import com.scluzlep.miimeprivacy.xposed.ResolveResult
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import com.scluzlep.miimeprivacy.xposed.findClass
import com.scluzlep.miimeprivacy.xposed.findMethod
import io.github.libxposed.api.XposedInterface
import org.json.JSONArray
import org.json.JSONObject
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.MatchType
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

/**
 * Xposed hook pack for Sogou Xiaomi IME (`com.sohu.inputmethod.sogou.xiaomi`, versionCode 20160).
 *
 * Implements granular v2.0 per-rule gating across all 30 Sogou rules in [HookRuleCatalog.rulesFor]:
 * - `sogou.gate.*`: [HookRuleCatalog.RULE_SOGOU_GATE_ALIVE_HEARTBEAT],
 *   [HookRuleCatalog.RULE_SOGOU_GATE_PRECONSENT_GUARD]
 * - `sogou.id.*`: [HookRuleCatalog.RULE_SOGOU_ID_OAID_TGPA],
 *   [HookRuleCatalog.RULE_SOGOU_ID_HARDWARE_AND_SENSOR],
 *   [HookRuleCatalog.RULE_SOGOU_ID_ANDROID_ID_AND_UUID]
 * - `sogou.telemetry.*`: [HookRuleCatalog.RULE_SOGOU_TELEMETRY_PINGBACK],
 *   [HookRuleCatalog.RULE_SOGOU_TELEMETRY_WHITEDOG_LOGDIAG],
 *   [HookRuleCatalog.RULE_SOGOU_TELEMETRY_BEACON_QIMEI],
 *   [HookRuleCatalog.RULE_SOGOU_TELEMETRY_THIRDPARTY_ADS_QM],
 *   [HookRuleCatalog.RULE_SOGOU_TELEMETRY_CRASH_APM]
 * - `sogou.cloud.*`: [HookRuleCatalog.RULE_SOGOU_CLOUD_GATEWAY_AND_PIGGYBACK],
 *   [HookRuleCatalog.RULE_SOGOU_CLOUD_PAYLOAD_DEVICE_DIALOGUE],
 *   [HookRuleCatalog.RULE_SOGOU_CLOUD_WANGZAI_VPA],
 *   [HookRuleCatalog.RULE_SOGOU_CLOUD_VOICE_PRIVACY]
 * - `sogou.context.*`: [HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT],
 *   [HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE],
 *   [HookRuleCatalog.RULE_SOGOU_CONTEXT_TOUCH_POSITIONS],
 *   [HookRuleCatalog.RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA]
 * - `sogou.clip.*`: [HookRuleCatalog.RULE_SOGOU_CLIP_SNIFFER_AND_KOULING],
 *   [HookRuleCatalog.RULE_SOGOU_CLIP_AUTO_TRANSLATE_AND_JS],
 *   [HookRuleCatalog.RULE_SOGOU_CLIP_DB_HISTORY_SAVE],
 *   [HookRuleCatalog.RULE_SOGOU_CLIP_STRICT_BLOCK_LISTENER] (default OFF; preserves candidate chip `q.e().m`)
 * - `sogou.scan.*`: [HookRuleCatalog.RULE_SOGOU_SCAN_DEVICEINFO_HARVESTER],
 *   [HookRuleCatalog.RULE_SOGOU_SCAN_APP_LIST],
 *   [HookRuleCatalog.RULE_SOGOU_SCAN_CONTACTS_UPLOAD_AND_DICT],
 *   [HookRuleCatalog.RULE_SOGOU_SCAN_LOCATION_WIFI]
 * - `sogou.net.*`: [HookRuleCatalog.RULE_SOGOU_NET_DYNAMIC_DEX_AND_JS],
 *   [HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS] (default OFF),
 *   [HookRuleCatalog.RULE_SOGOU_NET_NATIVE_ENGINE_STRICT] (default OFF, install-gated)
 * - UI & Overlay: [HookRuleCatalog.RULE_SOGOU_UI_DUAL_SKIN_EMOJI],
 *   [HookRuleCatalog.RULE_SOGOU_OVERLAY_PILL] (wired via `KeyboardQuickPillHook`)
 */
object SogouHookPack : ImeHookPack {

    override val target: ImeTarget = ImeTarget.SOGOU

    private val SEARCH_PACKAGES = arrayOf("com.sohu", "com.sogou", "sogou", "base.sogou")
    private const val DUMMY_UUID_ZERO = "0000000000000000000000000000000000000000000000000"
    private const val DUMMY_MAC = "02:00:00:00:00:00"

    private val registeredCloudListener = AtomicReference<((PrivacyConfig, PrivacyConfig) -> Unit)?>(null)
    private val forcedCloudMgrPriorM = AtomicReference<Int?>(null)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var cachedCloudMgrRefs: CloudMgrRefs? = null

    @Volatile
    private var cachedImeUiRefs: ImeUiRefs? = null

    @Volatile
    private var activeRegistryRef: HookRegistry? = null

    internal class CloudMgrRefs(clazz: Class<*>, j2Class: Class<*>?) {
        val fieldZ: Field? = findField(clazz, "z")
        val fieldM: Field? = findField(clazz, "m")
        val fieldX: Field? = findField(clazz, "x")
        val fieldJ2C0: Field? = j2Class?.let { findField(it, "C0") }
        val methodO: Method? = SymbolResolver.findDeclaredMethodOrNull(clazz, "o")
        val methodH1: Method? = SymbolResolver.findDeclaredMethodOrNull(clazz, "h1")
        val methodV0: Method? = SymbolResolver.findDeclaredMethodOrNull(clazz, "V0")
        val methodV: Method? = SymbolResolver.findDeclaredMethodOrNull(clazz, "v")
    }

    private class ImeUiRefs(classLoader: ClassLoader) {
        private val imeClass: Class<*>? =
            SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.MainImeServiceDel", classLoader)
        private val getInstanceMethod: Method? =
            imeClass?.let { findMethodInHierarchy(it, "getInstance") }
        private val isInputViewShownMethod: Method? =
            imeClass?.let { findMethodInHierarchy(it, "isInputViewShown") }

        fun isInputViewShown(): Boolean {
            val getter = getInstanceMethod ?: return false
            val checker = isInputViewShownMethod ?: return false
            return runCatching {
                val instance = getter.invoke(null) ?: return@runCatching false
                checker.invoke(instance) as? Boolean ?: false
            }.getOrDefault(false)
        }
    }

    internal class RuleStats {
        var installed = 0
        var failed = 0
        var primaryInstalled = 0
        var usedFallback = false
        private var primaryMatchedMethod: String? = null
        private var primarySource: String? = null
        private var lastMatchedMethod: String? = null
        private var lastSource: String? = null
        private val recordedSources = linkedSetOf<String>()
        val details = mutableListOf<String>()

        fun record(
            registry: HookRegistry,
            ruleId: String,
            hookName: String,
            result: ResolveResult<*>,
            hooked: Boolean,
            primary: Boolean = false
        ) {
            val report = if (hooked) registry.getReport(ruleId) else null
            recordOutcome(
                hookName = hookName,
                source = result.source,
                count = if (hooked) 1 else 0,
                matchedMethod = report?.matchedMethod,
                reportedSource = report?.source,
                primary = primary
            )
        }

        fun recordCount(
            registry: HookRegistry,
            ruleId: String,
            hookName: String,
            source: ResolveSource,
            count: Int,
            primary: Boolean = false
        ) {
            val report = if (count > 0) registry.getReport(ruleId) else null
            recordOutcome(
                hookName = hookName,
                source = source,
                count = count,
                matchedMethod = report?.matchedMethod,
                reportedSource = report?.source,
                primary = primary
            )
        }

        internal fun recordOutcome(
            hookName: String,
            source: ResolveSource,
            count: Int,
            matchedMethod: String?,
            reportedSource: String? = null,
            primary: Boolean = false
        ) {
            if (count > 0) {
                installed += count
                if (primary) primaryInstalled += count
                if (source == ResolveSource.STRUCTURAL_FALLBACK) {
                    usedFallback = true
                }
                val srcTokens = (reportedSource?.takeIf { it.isNotBlank() } ?: source.name)
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                recordedSources.addAll(srcTokens)
                val sig = matchedMethod?.takeIf { it.isNotBlank() }
                val srcName = srcTokens.joinToString(",").ifBlank { source.name }
                if (sig != null) {
                    lastMatchedMethod = sig
                    lastSource = srcName
                    if (primary && primaryMatchedMethod == null) {
                        primaryMatchedMethod = sig
                        primarySource = srcName
                    }
                }
            } else {
                failed++
                details.add("miss:$hookName")
            }
        }

        internal fun buildFinalReport(
            existing: HookRuleReport?,
            ruleId: String,
            summaryPrefix: String
        ): HookRuleReport {
            val status = when {
                installed == 0 || primaryInstalled == 0 -> HookMatchStatus.FAILED
                failed > 0 -> HookMatchStatus.DEGRADED
                usedFallback -> HookMatchStatus.FALLBACK
                else -> HookMatchStatus.ACTIVE
            }
            val matchedMethod = primaryMatchedMethod?.takeIf { it.isNotBlank() }
                ?: lastMatchedMethod?.takeIf { it.isNotBlank() }
                ?: existing?.matchedMethod.orEmpty()
            val source = if (recordedSources.isNotEmpty()) {
                recordedSources.joinToString(",")
            } else {
                primarySource?.takeIf { it.isNotBlank() }
                    ?: lastSource?.takeIf { it.isNotBlank() }
                    ?: existing?.source.orEmpty()
            }
            val missing = if (details.isEmpty()) "" else "; ${details.joinToString(",")}"
            return HookRuleReport(
                ruleId = ruleId,
                status = status,
                matchedMethod = matchedMethod,
                source = source,
                blockedCount = existing?.blockedCount ?: 0L,
                detail = "$summaryPrefix installed=$installed failed=$failed$missing"
            )
        }

        fun finalizeRule(registry: HookRegistry, ruleId: String, summaryPrefix: String) {
            val report = buildFinalReport(registry.getReport(ruleId), ruleId, summaryPrefix)
            registry.reportRule(
                ruleId = report.ruleId,
                status = report.status,
                matchedMethod = report.matchedMethod,
                source = report.source,
                detail = report.detail
            )
        }
    }

    fun resetCaches() {
        registeredCloudListener.getAndSet(null)?.let { old ->
            ConfigBridge.removeListener(old)
        }
        cachedCloudMgrRefs = null
        cachedImeUiRefs = null
    }

    private fun isPointEnabled(ruleId: String): Boolean =
        ConfigBridge.isPointEnabled(target.packageName, ruleId)

    override fun install(
        context: Context?,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ): ImePackInstallResult {
        activeRegistryRef = registry
        cachedImeUiRefs = ImeUiRefs(classLoader)

        registry.createSymbolResolver(classLoader).use { resolver ->
            val sharedDeviceMRes = lazy(LazyThreadSafetyMode.NONE) {
                resolver.resolveClass(
                    cacheKey = "sogou.lib.device.m",
                    fastPathCandidates = listOf("com.sogou.lib.device.m"),
                    dexKitFinder = { dk -> findClassByStrings(dk, "socketwrapper", "doPostBatchReport: isPrivate: %b") }
                )
            }
            val sharedFlxWebViewRes = lazy(LazyThreadSafetyMode.NONE) {
                resolver.resolveClass(
                    cacheKey = "sogou.flx.FlxImeWebView",
                    fastPathCandidates = listOf("com.sohu.inputmethod.flx.miniprogram.view.FlxImeWebView"),
                    dexKitFinder = { dk -> findClassByStrings(dk, "responseClipBoardText", "responseSogouClipBoardText") }
                )
            }
            val sharedDeviceIRes = lazy(LazyThreadSafetyMode.NONE) {
                resolver.resolveClass(
                    cacheKey = "sogou.lib.device.i",
                    fastPathCandidates = listOf("com.sogou.lib.device.i"),
                    dexKitFinder = { dk -> findClassByStrings(dk, "pref_android_id", "/sogou/files/uuid") }
                )
            }
            val sharedInfoMgrRes = lazy(LazyThreadSafetyMode.NONE) {
                resolver.resolveClass(
                    cacheKey = "sogou.bu.InfoManager",
                    fastPathCandidates = listOf("com.sogou.bu.basic.util.InfoManager"),
                    dexKitFinder = { dk -> findClassByStrings(dk, "gsm_", "cdma_") }
                )
            }
            val sharedJsInterfaceRes = lazy(LazyThreadSafetyMode.NONE) {
                resolver.resolveClass(
                    cacheKey = "sogou.hotwords.SogouJSInterface",
                    fastPathCandidates = listOf("base.sogou.mobile.hotwordsbase.utils.SogouJSInterface"),
                    dexKitFinder = { dk ->
                        dk.findClass {
                            searchPackages("base.sogou.mobile.hotwordsbase.utils")
                            matcher {
                                addMethod { name("requestClipBoardText", StringMatchType.Equals) }
                            }
                        }.singleOrNull()?.descriptor
                    }
                )
            }
            val sharedDevInfoServiceRes = lazy(LazyThreadSafetyMode.NONE) {
                resolver.resolveClass(
                    cacheKey = "sogou.deviceinfo.DeviceInfoServiceImpl",
                    fastPathCandidates = listOf("com.sohu.inputmethod.settings.deviceinfo.DeviceInfoServiceImpl"),
                    dexKitFinder = { dk ->
                        dk.findClass(
                            FindClass.create()
                                .searchPackages("com.sohu.inputmethod.settings.deviceinfo")
                                .matcher(ClassMatcher.create().addMethod(MethodMatcher.create().name("addContactsInfo")))
                        ).singleOrNull()?.descriptor
                    }
                )
            }
            val sharedDevInfoBRes = lazy(LazyThreadSafetyMode.NONE) {
                resolver.resolveClass(
                    cacheKey = "sogou.deviceinfo.b",
                    fastPathCandidates = listOf("com.sohu.inputmethod.settings.deviceinfo.b"),
                    dexKitFinder = { dk ->
                        findClassByStrings(dk, "data_set desc  limit  0,5000", "date desc  limit  0,5000")
                    }
                )
            }
            val sharedHjRequestWorkRes = lazy(LazyThreadSafetyMode.NONE) {
                resolver.resolveClass(
                    cacheKey = "sogou.hj.RequestWork",
                    fastPathCandidates = listOf("com.sogou.hj.RequestWork"),
                    dexKitFinder = { dk ->
                        findClassByStrings(dk, "http://srv.android.shouji.sogou.com/v1/config/get_mpkg")
                    }
                )
            }

            // 1. sogou.gate.*
            installAliveHeartbeatRule(resolver, registry)
            installPreconsentGuardRule(resolver, registry)

            // 2. sogou.id.*
            installOaidTgpaRule(resolver, registry)
            installHardwareAndSensorIdRule(resolver, registry, sharedDeviceMRes, sharedDeviceIRes)
            installAndroidIdAndUuidRule(resolver, registry, sharedDeviceMRes, sharedDeviceIRes, sharedInfoMgrRes)

            // 3. sogou.telemetry.*
            installPingbackRule(resolver, registry, sharedDeviceMRes, sharedHjRequestWorkRes)
            installWhiteDogLogDiagRule(resolver, registry)
            installBeaconQimeiRule(resolver, registry, sharedJsInterfaceRes)
            installThirdPartyAdsQmRule(resolver, registry)
            installCrashApmRule(resolver, registry)

            // 4. sogou.cloud.* & sogou.context.*
            installCloudGatewayAndPiggybackRule(classLoader, resolver, registry)
            installSharedCloudAndContextPayloadRules(resolver, registry)
            installWangzaiVpaRule(resolver, registry)
            installVoicePrivacyRule(resolver, registry)
            installMutualBigDataRule(resolver, registry)

            // 5. sogou.clip.*
            installClipSnifferAndKoulingRule(resolver, registry)
            installClipAutoTranslateAndJsRule(resolver, registry, sharedFlxWebViewRes, sharedJsInterfaceRes)
            installClipDbHistorySaveRule(resolver, registry)
            installClipStrictBlockListenerRule(resolver, registry)

            // 6. sogou.scan.*
            installDeviceInfoHarvesterRule(resolver, registry, sharedDevInfoServiceRes, sharedDevInfoBRes)
            installAppListScanRule(resolver, registry)
            installContactsUploadAndDictRule(resolver, registry)
            installLocationWifiRule(resolver, registry, sharedInfoMgrRes, sharedDevInfoServiceRes, sharedDevInfoBRes)

            // 7. sogou.net.*
            installDynamicDexAndJsRule(resolver, registry, sharedFlxWebViewRes, sharedDeviceMRes, sharedHjRequestWorkRes)
            installHttpDnsRule(resolver, registry)
            installNativeEngineStrictRule(resolver, registry)

            // 8. sogou.ui.dual_skin_emoji
            installDualSkinEmojiRule(classLoader, resolver, registry)
        }

        val rules = HookRuleCatalog.rulesFor(target)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_SOGOU_OVERLAY_PILL }
        return ImePackInstallResult(
            target = target,
            reports = rules.mapNotNull { registry.getReport(it.ruleId) },
            note = "Sogou Xiaomi v20160 v2.0 privacy hooks active (${registry.processName})"
        )
    }

    // -------------------------------------------------------------------------
    // 1. sogou.gate.alive_heartbeat & sogou.gate.preconsent_guard
    // -------------------------------------------------------------------------

    private fun installAliveHeartbeatRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_GATE_ALIVE_HEARTBEAT
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val aliveARes = resolver.resolveClass(
                cacheKey = "sogou.alive.a",
                fastPathCandidates = listOf("com.sogou.alive.a"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sogou.alive")
                            .matcher(
                                ClassMatcher.create().addMethod(
                                    MethodMatcher.create()
                                        .paramTypes("okhttp3.z")
                                        .returnType("boolean")
                                )
                            )
                    ).singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "alive_a_c",
                cacheKey = "sogou.alive.a#c",
                classResult = aliveARes,
                methodName = "c",
                returnType = "void",
                primary = true,
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "alive_a_d",
                cacheKey = "sogou.alive.a#d",
                classResult = aliveARes,
                methodName = "d",
                String::class.java,
                returnType = "boolean",
                hooker = booleanFalseHooker(ruleId)
            )
            hookResolvedMethod(
                registry, resolver, stats, ruleId,
                hookId = "alive_a_e",
                cacheKey = "sogou.alive.a#e",
                fastPath = { cl ->
                    val cls = aliveARes.value ?: return@hookResolvedMethod null
                    val reqCls = SymbolResolver.loadClassOrNull("okhttp3.z", cl)
                    if (reqCls != null) SymbolResolver.findDeclaredMethodOrNull(cls, "e", reqCls)
                    else safeDeclaredMethods(cls).firstOrNull {
                        it.name == "e" && it.parameterCount == 1 && it.returnType == Boolean::class.javaPrimitiveType
                    }
                },
                dexKitFinder = { dk ->
                    findMethodBy(
                        dk = dk,
                        searchPackages = arrayOf("com.sogou.alive"),
                        returnType = "boolean",
                        paramTypes = arrayOf("okhttp3.z"),
                        strings = emptyArray()
                    )
                },
                hooker = booleanFalseHooker(ruleId)
            )

            hookResolvedMethod(
                registry, resolver, stats, ruleId,
                hookId = "alive_strategy_a",
                cacheKey = "sogou.alive.strategy.a#a",
                fastPath = { cl ->
                    SymbolResolver.findDeclaredMethodOrNull(
                        SymbolResolver.loadClassOrNull("com.sogou.alive.strategy.a", cl),
                        "a"
                    )
                },
                dexKitFinder = { dk ->
                    dk.findMethod {
                        searchPackages("com.sogou.alive.strategy")
                        matcher {
                            declaredClass {
                                usingStrings(
                                    listOf("https://api-android.shouji.sogou.com/v1/alive/special_alive"),
                                    StringMatchType.Contains
                                )
                            }
                            name("a", StringMatchType.Equals)
                            returnType("boolean")
                            paramCount(0)
                        }
                    }.singleOrNull()?.descriptor
                },
                hooker = booleanFalseHooker(ruleId)
            )

            val aliveStrategyBRes = resolver.resolveClass(
                cacheKey = "sogou.alive.strategy.b",
                fastPathCandidates = listOf("com.sogou.alive.strategy.b"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.alive.strategy")
                        matcher {
                            modifiers(Modifier.ABSTRACT, MatchType.Contains)
                            addMethod {
                                name("h", StringMatchType.Equals)
                                paramCount(0)
                                returnType("void")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "alive_strategy_b_h",
                cacheKey = "sogou.alive.strategy.b#h",
                classResult = aliveStrategyBRes,
                methodName = "h",
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val aliveInstallRes = resolver.resolveClass(
                cacheKey = "sogou.alive.install.e",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.alive.e"),
                dexKitFinder = { dk -> findClassByStrings(dk, "/v1/alive/install") }
            )
            listOf("a", "b", "c", "d").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "alive_install_$mName",
                    classResult = aliveInstallRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            stats.finalizeRule(registry, ruleId, "AliveHeartbeat")
        }
    }

    private fun installPreconsentGuardRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_GATE_PRECONSENT_GUARD
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val privacyARes = resolver.resolveClass(
                cacheKey = "sogou.privacy.a",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.privacy.a"),
                dexKitFinder = { dk ->
                    findClassByStrings(dk, "enforce_send=", "do not send network request without allowing privacy")
                }
            )
            hookResolvedMethod(
                registry, resolver, stats, ruleId,
                hookId = "privacy_enforce_send",
                cacheKey = "sogou.privacy.a#a",
                fastPath = { cl ->
                    val cls = privacyARes.value ?: return@hookResolvedMethod null
                    val reqCls = SymbolResolver.loadClassOrNull("okhttp3.z", cl)
                    if (reqCls != null) SymbolResolver.findDeclaredMethodOrNull(cls, "a", reqCls)
                    else safeDeclaredMethods(cls).firstOrNull {
                        it.name == "a" && it.parameterCount == 1 && it.returnType == Boolean::class.javaPrimitiveType
                    }
                },
                dexKitFinder = { dk ->
                    findMethodBy(
                        dk = dk,
                        returnType = "boolean",
                        paramCount = 1,
                        strings = arrayOf("enforce_send=")
                    )
                },
                primary = true,
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    val url = extractOkHttpUrlOrToString(chain.getArg(0))
                    if (SogouPayloadSanitizer.shouldBlockAliveEnforceSendUrl(url, ruleEnabled = true)) {
                        registry.incrementBlocked(ruleId)
                        return@Hooker false
                    }
                    chain.proceed()
                }
            )

            val launchHeadersRes = resolver.resolveClass(
                cacheKey = "sogou.launch.d_d",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.launch.d\$d"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sohu.inputmethod.sogou.launch")
                            .matcher(ClassMatcher.create().addInterface("com.sogou.networking.reporter.okhttp.a"))
                    ).singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "launch_d_d_b",
                cacheKey = "sogou.launch.d_d#b",
                classResult = launchHeadersRes,
                methodName = "b",
                returnType = "java.util.Map",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    HashMap<String, String>()
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "launch_d_d_c",
                cacheKey = "sogou.launch.d_d#c",
                classResult = launchHeadersRes,
                methodName = "c",
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )

            val reporterRes = resolver.resolveClass(
                cacheKey = "sogou.networking.reporter.a",
                fastPathCandidates = listOf("com.sogou.networking.reporter.a"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.networking.reporter")
                        matcher {
                            addMethod {
                                name("j", StringMatchType.Equals)
                                paramTypes(
                                    "android.content.Context",
                                    "com.sogou.networking.f",
                                    "com.sogou.networking.d"
                                )
                                returnType("void")
                            }
                            addMethod {
                                name("m", StringMatchType.Equals)
                                paramTypes("com.sogou.networking.reporter.okhttp.a")
                                returnType("void")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookResolvedMethod(
                registry, resolver, stats, ruleId,
                hookId = "networking_reporter_j",
                cacheKey = "sogou.networking.reporter.a#j",
                fastPath = { cl ->
                    val cls = reporterRes.value ?: return@hookResolvedMethod null
                    val fCls = SymbolResolver.loadClassOrNull("com.sogou.networking.f", cl)
                    val dCls = SymbolResolver.loadClassOrNull("com.sogou.networking.d", cl)
                    if (fCls != null && dCls != null) {
                        SymbolResolver.findDeclaredMethodOrNull(cls, "j", Context::class.java, fCls, dCls)
                    } else {
                        safeDeclaredMethods(cls).firstOrNull {
                            it.name == "j" && it.parameterCount == 3 && it.parameterTypes[0] == Context::class.java
                        }
                    }
                },
                dexKitFinder = { dk ->
                    val cls = reporterRes.value ?: return@hookResolvedMethod null
                    dk.findMethod {
                        searchPackages("com.sogou.networking.reporter")
                        matcher {
                            declaredClass(cls.name, StringMatchType.Equals)
                            name("j", StringMatchType.Equals)
                            returnType("void")
                            paramCount(3)
                        }
                    }.singleOrNull()?.descriptor
                },
                hooker = voidNoOpHooker(ruleId)
            )

            stats.finalizeRule(registry, ruleId, "PreconsentGuard")
        }
    }

    // -------------------------------------------------------------------------
    // 2. sogou.id.* (OAID / Hardware & Sensors / Android ID & UUID)
    // -------------------------------------------------------------------------

    private fun installOaidTgpaRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_ID_OAID_TGPA
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()
            val oaidRes = resolver.resolveClass(
                cacheKey = "sogou.lib.device.oaid.c",
                fastPathCandidates = listOf("com.sogou.lib.device.oaid.c"),
                dexKitFinder = { dk -> findClassByStrings(dk, "tencent_oaid", "200100") }
            )
            hookResolvedMethod(
                registry, resolver, stats, ruleId,
                hookId = "oaid_c_h",
                cacheKey = "sogou.lib.device.oaid.c#h",
                fastPath = { SymbolResolver.findDeclaredMethodOrNull(oaidRes.value, "h") },
                dexKitFinder = { dk ->
                    findMethodBy(
                        dk = dk,
                        searchPackages = arrayOf("com.sogou.lib.device.oaid"),
                        returnType = "java.lang.String",
                        paramCount = 0,
                        strings = arrayOf("tencent_oaid")
                    )
                },
                primary = true,
                hooker = emptyStringHooker(ruleId)
            )
            listOf("j", "o").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "oaid_c_$mName",
                    classResult = oaidRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }
            stats.finalizeRule(registry, ruleId, "OaidTgpa")
        }
    }

    private fun installHardwareAndSensorIdRule(
        resolver: SymbolResolver,
        registry: HookRegistry,
        sharedDeviceMRes: Lazy<ResolveResult<Class<*>>>,
        sharedDeviceIRes: Lazy<ResolveResult<Class<*>>>
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_ID_HARDWARE_AND_SENSOR
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val deviceFRes = resolver.resolveClass(
                cacheKey = "sogou.lib.device.f",
                fastPathCandidates = listOf("com.sogou.lib.device.f"),
                dexKitFinder = { dk -> findClassByStrings(dk, "pref_mac", DUMMY_MAC, "ro.serialno") }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "device_f_mac",
                classResult = deviceFRes,
                methodName = "A",
                primary = true,
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    DUMMY_MAC
                }
            )
            listOf("D", "N", "m", "q", "s", "u").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "device_f_$mName",
                    classResult = deviceFRes,
                    methodName = mName,
                    hooker = emptyStringHooker(ruleId)
                )
            }
            listOf("H", "I").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "device_f_sensor_$mName",
                    classResult = deviceFRes,
                    methodName = mName,
                    hooker = emptyJsonArrayHooker(ruleId)
                )
            }

            val deviceMRes = sharedDeviceMRes.value
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "device_m_bssid",
                cacheKey = "sogou.lib.device.m#d",
                classResult = deviceMRes,
                methodName = "d",
                WifiInfo::class.java,
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "device_m_imei",
                cacheKey = "sogou.lib.device.m#f",
                classResult = deviceMRes,
                methodName = "f",
                TelephonyManager::class.java,
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )

            val deviceNRes = resolver.resolveClass(
                cacheKey = "sogou.lib.device.n",
                fastPathCandidates = listOf("com.sogou.lib.device.n"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.lib.device")
                        matcher {
                            usingStrings(listOf("310260000000000"), StringMatchType.Contains)
                            addMethod { name("b", StringMatchType.Equals) }
                            addMethod { name("d", StringMatchType.Equals) }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("b", "d").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "device_n_$mName",
                    classResult = deviceNRes,
                    methodName = mName,
                    hooker = emptyStringHooker(ruleId)
                )
            }

            val oemJRes = resolver.resolveClass(
                cacheKey = "sogou.oem.util.j",
                fastPathCandidates = listOf("com.sogou.inputmethod.oem.util.j"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.inputmethod.oem.util")
                        matcher {
                            usingStrings(listOf("00000000000000000000"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("c", "d", "f", "h", "j").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "oem_j_$mName",
                    classResult = oemJRes,
                    methodName = mName,
                    hooker = emptyStringHooker(ruleId)
                )
            }

            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "device_i_h_imei",
                classResult = sharedDeviceIRes.value,
                methodName = "h",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    arrayOf("-1", "-1")
                }
            )

            val deviceSupportBRes = resolver.resolveClass(
                cacheKey = "sogou.lib.device.support.b",
                fastPathCandidates = listOf("com.sogou.lib.device.support.b"),
                dexKitFinder = { dk ->
                    findClassByStrings(dk, "cat /sys/block/mmcblk0/device/cid", "pref_sdcard_serial_id")
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "device_support_b_b",
                classResult = deviceSupportBRes,
                methodName = "b",
                hooker = emptyStringHooker(ruleId)
            )

            val weiboSsoRes = resolver.resolveClass(
                cacheKey = "sogou.weibo.WeiboSsoSdk",
                fastPathCandidates = listOf("com.weibo.ssosdk.WeiboSsoSdk"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.weibo.ssosdk")
                        matcher {
                            usingStrings(
                                listOf("weibo_sso_sdk_aid", "https://login.sina.com.cn/visitor/signin"),
                                StringMatchType.Contains
                            )
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "weibo_sso_riseWind",
                classResult = weiboSsoRes,
                methodName = "riseWind",
                hooker = emptyStringHooker(ruleId)
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "weibo_sso_j",
                classResult = weiboSsoRes,
                methodName = "j",
                hooker = booleanFalseHooker(ruleId)
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "weibo_sso_l",
                classResult = weiboSsoRes,
                methodName = "l",
                hooker = voidNoOpHooker(ruleId)
            )

            stats.finalizeRule(registry, ruleId, "HardwareSensorId")
        }
    }

    private fun installAndroidIdAndUuidRule(
        resolver: SymbolResolver,
        registry: HookRegistry,
        sharedDeviceMRes: Lazy<ResolveResult<Class<*>>>,
        sharedDeviceIRes: Lazy<ResolveResult<Class<*>>>,
        sharedInfoMgrRes: Lazy<ResolveResult<Class<*>>>
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_ID_ANDROID_ID_AND_UUID
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val deviceMRes = sharedDeviceMRes.value
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "device_m_android_id",
                cacheKey = "sogou.lib.device.m#c",
                classResult = deviceMRes,
                methodName = "c",
                ContentResolver::class.java,
                returnType = "java.lang.String",
                primary = true,
                hooker = emptyStringHooker(ruleId)
            )
            listOf("h", "i").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "device_m_$mName",
                    classResult = deviceMRes,
                    methodName = mName,
                    hooker = emptyStringHooker(ruleId)
                )
            }
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "device_m_settings_secure",
                cacheKey = "sogou.lib.device.m#j",
                classResult = deviceMRes,
                methodName = "j",
                ContentResolver::class.java,
                String::class.java,
                returnType = "java.lang.String",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    val key = chain.getArg(1) as? String
                    if (key == "android_id") {
                        registry.incrementBlocked(ruleId)
                        return@Hooker ""
                    }
                    chain.proceed()
                }
            )

            val deviceIRes = sharedDeviceIRes.value
            listOf("f", "m", "p").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "device_i_$mName",
                    classResult = deviceIRes,
                    methodName = mName,
                    hooker = emptyStringHooker(ruleId)
                )
            }
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "device_i_uuid",
                classResult = deviceIRes,
                methodName = "j",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    DUMMY_UUID_ZERO
                }
            )

            val infoMgrRes = sharedInfoMgrRes.value
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "infomgr_getAndroidID",
                cacheKey = "sogou.bu.InfoManager#getAndroidID",
                classResult = infoMgrRes,
                methodName = "getAndroidID",
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )

            stats.finalizeRule(registry, ruleId, "AndroidIdUuid")
        }
    }

    // -------------------------------------------------------------------------
    // 3. sogou.telemetry.* (Pingback, WhiteDog/LogDiag, Beacon/Qimei, Ads/QM, Crash/APM)
    // -------------------------------------------------------------------------

    private fun installPingbackRule(
        resolver: SymbolResolver,
        registry: HookRegistry,
        sharedDeviceMRes: Lazy<ResolveResult<Class<*>>>,
        sharedHjRequestWorkRes: Lazy<ResolveResult<Class<*>>>
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_TELEMETRY_PINGBACK
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val pingbackFRes = resolver.resolveClass(
                cacheKey = "sogou.pingback.f",
                fastPathCandidates = listOf("sogou.pingback.f"),
                dexKitFinder = { dk -> findClassByStrings(dk, "http://kpi2.android.shouji.sogou.com/kpi2.gif") }
            )
            listOf("d", "e", "f", "i", "j").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "pingback_f_$mName",
                    classResult = pingbackFRes,
                    methodName = mName,
                    primary = true,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val x2Res = resolver.resolveClass(
                cacheKey = "sogou.x2",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.x2"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.sogou")
                        matcher {
                            usingStrings(
                                listOf("http://instantpb.android.shouji.sogou.com/instantpb.gif"),
                                StringMatchType.Contains
                            )
                            addMethod { name("d", StringMatchType.Equals) }
                            addMethod { name("m", StringMatchType.Equals) }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("d", "f", "g", "i", "j", "k", "l", "m").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "x2_$mName",
                    classResult = x2Res,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val statusRes = resolver.resolveClass(
                cacheKey = "sogou.settings.status.a",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.status.a"),
                dexKitFinder = { dk -> findClassByStrings(dk, "input_methods_subtype_history", "default_input_method") }
            )
            listOf("h", "i").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "status_a_$mName",
                    classResult = statusRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val pb6Res = resolver.resolveClass(
                cacheKey = "sogou.settings.internet.l",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.l"),
                dexKitFinder = { dk -> findClassByStrings(dk, "http://pb6.sogou.com/androidinput.gif") }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "pb6_l_onWork",
                classResult = pb6Res,
                methodName = "onWork",
                hooker = voidNoOpHooker(ruleId)
            )

            val hotwordsPbRes = resolver.resolveClass(
                cacheKey = "sogou.hotwords.SendPingBackTask",
                fastPathCandidates = listOf("base.sogou.mobile.hotwordsbase.pingback.SendPingBackTask"),
                dexKitFinder = { dk ->
                    findClassByStrings(dk, "http://androidsdk.ping.mse.sogou.com/androidSDKBrowser.png")
                }
            )
            listOf("d", "e", "f", "g", "h").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "hotwords_pb_$mName",
                    classResult = hotwordsPbRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val r3Res = resolver.resolveClass(
                cacheKey = "sogou.r3",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.r3"),
                dexKitFinder = { dk -> findClassByStrings(dk, "app_data_sp", "b1227") }
            )
            listOf("w", "x", "y", "z", "A", "B").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "r3_$mName",
                    classResult = r3Res,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            // Block 02/03/05/07/08/09/10 pingback gaps
            val deeplinkPbRes = resolver.resolveClass(
                cacheKey = "sogou.bu.util.b",
                fastPathCandidates = listOf("com.sogou.bu.basic.util.b"),
                dexKitFinder = { dk -> findClassByStrings(dk, "http://instantpb.android.shouji.sogou.com/deeplinkpb.gif") }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "bu_util_b_e",
                cacheKey = "sogou.bu.util.b#e",
                classResult = deeplinkPbRes,
                methodName = "e",
                Map::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val bizAdBRes = resolver.resolveClass(
                cacheKey = "sogou.businessadvisement.b",
                fastPathCandidates = listOf("com.sohu.inputmethod.businessadvisement.b"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.businessadvisement")
                        matcher {
                            addMethod {
                                name("d", StringMatchType.Equals)
                                paramTypes("java.lang.String", "java.lang.String")
                                addInvoke {
                                    declaredClass("com.sohu.inputmethod.sogou.x2", StringMatchType.Equals)
                                    name("m", StringMatchType.Equals)
                                }
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "biz_ad_b_d",
                cacheKey = "sogou.businessadvisement.b#d",
                classResult = bizAdBRes,
                methodName = "d",
                String::class.java,
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val bizAdDRes = resolver.resolveClass(
                cacheKey = "sogou.businessadvisement.d",
                fastPathCandidates = listOf("com.sohu.inputmethod.businessadvisement.d"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.businessadvisement")
                        matcher {
                            addMethod {
                                name("c", StringMatchType.Equals)
                                paramTypes("java.lang.String", "java.lang.String")
                                addInvoke {
                                    declaredClass("com.sogou.threadpool.BackgroundService", StringMatchType.Equals)
                                    name("L", StringMatchType.Equals)
                                }
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "biz_ad_d_c",
                cacheKey = "sogou.businessadvisement.d#c",
                classResult = bizAdDRes,
                methodName = "c",
                String::class.java,
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val j0Res = resolver.resolveClass(
                cacheKey = "sogou.j0",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.j0"),
                dexKitFinder = { dk -> findClassByStrings(dk, "http://instantpb.android.shouji.sogou.com/ucenter.gif?type=tip_show") }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "j0_b",
                cacheKey = "sogou.j0#b",
                classResult = j0Res,
                methodName = "b",
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val netProcHandlerRes = resolver.resolveClass(
                cacheKey = "sogou.settings.NetworkProcessHandler",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.NetworkProcessHandler"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.settings")
                        matcher {
                            usingStrings(
                                listOf("input_efficiency_pingback", "cloud_efficiency_pingback"),
                                StringMatchType.Contains
                            )
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "net_proc_handler_k0",
                cacheKey = "sogou.settings.NetworkProcessHandler#k0",
                classResult = netProcHandlerRes,
                methodName = "k0",
                Boolean::class.javaPrimitiveType!!,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val internetTRes = resolver.resolveClass(
                cacheKey = "sogou.settings.internet.t",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.t"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.settings.internet")
                        matcher {
                            superClass("com.sogou.threadpool.net.a")
                            addMethod {
                                name("onWork", StringMatchType.Equals)
                                addInvoke {
                                    declaredClass("com.sohu.inputmethod.internet.InternetConnection", StringMatchType.Equals)
                                    name("y0", StringMatchType.Equals)
                                }
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val internetDRes = resolver.resolveClass(
                cacheKey = "sogou.settings.internet.d",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.d"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.settings.internet")
                        matcher {
                            superClass("com.sogou.threadpool.net.a")
                            addMethod {
                                name("onWork", StringMatchType.Equals)
                                addInvoke {
                                    declaredClass("com.sohu.inputmethod.internet.InternetConnection", StringMatchType.Equals)
                                    name("q0", StringMatchType.Equals)
                                }
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val internetGRes = resolver.resolveClass(
                cacheKey = "sogou.settings.internet.g",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.g"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.settings.internet")
                        matcher {
                            superClass("com.sogou.threadpool.net.a")
                            addMethod {
                                name("onWork", StringMatchType.Equals)
                                addInvoke {
                                    declaredClass("com.sohu.inputmethod.internet.InternetConnection", StringMatchType.Equals)
                                    name("x0", StringMatchType.Equals)
                                }
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val internetNRes = resolver.resolveClass(
                cacheKey = "sogou.settings.internet.n",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.n"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.settings.internet")
                        matcher {
                            superClass("com.sogou.threadpool.net.a")
                            addMethod {
                                name("a", StringMatchType.Equals)
                                addInvoke {
                                    declaredClass("com.sohu.inputmethod.internet.InternetConnection", StringMatchType.Equals)
                                    name("V", StringMatchType.Equals)
                                }
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val upgradeHRes = resolver.resolveClass(
                cacheKey = "sogou.settings.upgrade.h",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.upgrade.h"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.settings.internet.upgrade")
                        matcher {
                            superClass("com.sogou.threadpool.net.a")
                            usingStrings(listOf("utf-8"), StringMatchType.Contains)
                            addMethod {
                                name("c", StringMatchType.Equals)
                                addInvoke {
                                    declaredClass("com.sohu.inputmethod.internet.InternetConnection", StringMatchType.Equals)
                                    name("z0", StringMatchType.Equals)
                                }
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val voicePbARes = resolver.resolveClass(
                cacheKey = "sogou.voiceinput.pingback.a",
                fastPathCandidates = listOf("com.sohu.inputmethod.voiceinput.pingback.a"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.voiceinput.pingback")
                        matcher {
                            superClass("com.sogou.threadpool.net.a")
                            addMethod {
                                name("onWork", StringMatchType.Equals)
                                addInvoke {
                                    declaredClass("com.sohu.inputmethod.internet.InternetConnection", StringMatchType.Equals)
                                    name("Y0", StringMatchType.Equals)
                                }
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf(
                "internet_t_onWork" to internetTRes,
                "internet_d_onWork" to internetDRes,
                "internet_g_onWork" to internetGRes,
                "internet_n_onWork" to internetNRes,
                "upgrade_h_onWork" to upgradeHRes,
                "voiceinput_pb_a_onWork" to voicePbARes
            ).forEach { (hookIdPrefix, clsRes) ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = hookIdPrefix,
                    classResult = clsRes,
                    methodName = "onWork",
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val hotwordsHttpARes = resolver.resolveClass(
                cacheKey = "sogou.hotwords.http.a",
                fastPathCandidates = listOf("base.sogou.mobile.hotwordsbase.http.a"),
                dexKitFinder = { dk -> findClassByStrings(dk, "https://flxpb.shouji.sogou.com/", "https://pb.sogou.com/pv.gif") }
            )
            listOf("d", "e", "f", "g", "h", "i", "j").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "hotwords_http_a_$mName",
                    classResult = hotwordsHttpARes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val hjDRes = resolver.resolveClass(
                cacheKey = "sogou.hj.d",
                fastPathCandidates = listOf("com.sogou.hj.d"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.hj")
                        matcher {
                            usingStrings(listOf("http://data.ping.shouji.sogou.com/bigdata.gif"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "hj_d_b",
                cacheKey = "sogou.hj.d#b",
                classResult = hjDRes,
                methodName = "b",
                Map::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "hj_requestwork_s",
                classResult = sharedHjRequestWorkRes.value,
                methodName = "s",
                hooker = voidNoOpHooker(ruleId)
            )

            val internetConnRes = resolver.resolveClass(
                cacheKey = "sogou.internet.InternetConnection",
                fastPathCandidates = listOf("com.sohu.inputmethod.internet.InternetConnection"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.internet")
                        matcher {
                            usingStrings(listOf("offlinespeechpb.gz"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "internet_conn_Y0",
                cacheKey = "sogou.internet.InternetConnection#Y0",
                classResult = internetConnRes,
                methodName = "Y0",
                returnType = "int",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    0
                }
            )

            val okhttpGRes = resolver.resolveClass(
                cacheKey = "sogou.internet.okhttp.g",
                fastPathCandidates = listOf("com.sohu.inputmethod.internet.okhttp.g"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.internet.okhttp")
                        matcher {
                            usingStrings(listOf("http://instantpb.android.shouji.sogou.com/skinsharebtn.gif"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "okhttp_g_a",
                classResult = okhttpGRes,
                methodName = "a",
                hooker = voidNoOpHooker(ruleId)
            )

            val deviceMRes = sharedDeviceMRes.value
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "device_m_atta_b",
                classResult = deviceMRes,
                methodName = "b",
                hooker = voidNoOpHooker(ruleId)
            )
            listOf("k", "m").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "device_m_atta_$mName",
                    classResult = deviceMRes,
                    methodName = mName,
                    hooker = booleanFalseHooker(ruleId)
                )
            }

            val attaReporterRes = resolver.resolveClass(
                cacheKey = "sogou.raft.ATTAReporter",
                fastPathCandidates = listOf("com.tencent.raft.measure.report.ATTAReporter"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.tencent.raft.measure.report")
                        matcher {
                            usingStrings(listOf("https://h.trace.qq.com/kv", "doPostBatchReport err"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "raft_atta_doPostBatchReport",
                cacheKey = "sogou.raft.ATTAReporter#doPostBatchReport",
                classResult = attaReporterRes,
                methodName = "doPostBatchReport",
                JSONArray::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val sdkPbARes = resolver.resolveClass(
                cacheKey = "sogou.sdk.pingback.a",
                fastPathCandidates = listOf("com.sdk.sogou.pingback.a"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sdk.sogou.pingback")
                        matcher {
                            usingStrings(listOf("PingbackUtils_2_0"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "sdk_pb_a_i",
                classResult = sdkPbARes,
                methodName = "i",
                hooker = voidNoOpHooker(ruleId)
            )

            val doutuHttpClientRes = resolver.resolveClass(
                cacheKey = "sogou.sdk.doutu.HttpClient",
                fastPathCandidates = listOf("com.sdk.doutu.request.HttpClient"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sdk.doutu.request")
                        matcher {
                            addMethod {
                                name("sendPingback", StringMatchType.Equals)
                                paramTypes("java.lang.String", "java.lang.String")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "doutu_http_sendPingback",
                cacheKey = "sogou.sdk.doutu.HttpClient#sendPingback",
                classResult = doutuHttpClientRes,
                methodName = "sendPingback",
                String::class.java,
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val sdkPbBaseBRes = resolver.resolveClass(
                cacheKey = "sogou.sdk.pingback.base.b",
                fastPathCandidates = listOf("com.sdk.sogou.pingback.base.b"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sdk.sogou.pingback.base")
                        matcher {
                            modifiers(Modifier.ABSTRACT, MatchType.Contains)
                            usingStrings(listOf("uigs_productid=tugele&version="), StringMatchType.Contains)
                            addMethod {
                                name("d", StringMatchType.Equals)
                                paramTypes("android.content.Context", "java.lang.String")
                                returnType("void")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sdk_pb_base_b_d",
                cacheKey = "sogou.sdk.pingback.base.b#d",
                classResult = sdkPbBaseBRes,
                methodName = "d",
                Context::class.java,
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val themeNetERes = resolver.resolveClass(
                cacheKey = "sogou.theme.network.e",
                fastPathCandidates = listOf("com.sogou.theme.network.e"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.theme.network")
                        matcher {
                            usingStrings(listOf("http://instantpb.android.shouji.sogou.com/skinsharebtn.gif"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("t", "v", "w").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "theme_net_e_$mName",
                    classResult = themeNetERes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            stats.finalizeRule(registry, ruleId, "Pingback")
        }
    }

    private fun installWhiteDogLogDiagRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_TELEMETRY_WHITEDOG_LOGDIAG
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val whiteDogRes = resolver.resolveClass(
                cacheKey = "sogou.whitedog.step.f",
                fastPathCandidates = listOf("com.sogou.lib.whitedog.step.f"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sogou.lib.whitedog.step")
                            .matcher(
                                ClassMatcher.create().addMethod(
                                    MethodMatcher.create().paramTypes(
                                        "java.lang.String",
                                        "com.sogou.lib.whitedog.step.InputCollectorCommons\$InputCollectorPrepareStep",
                                        "java.lang.Object[]"
                                    )
                                )
                            )
                    ).singleOrNull()?.descriptor
                }
            )
            listOf("r", "g", "x").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "whitedog_f_$mName",
                    classResult = whiteDogRes,
                    methodName = mName,
                    primary = true,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val whiteDogUploaderRes = resolver.resolveClass(
                cacheKey = "sogou.whitedog.uploader.k",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.k"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.settings.internet")
                        matcher {
                            superClass("com.sogou.threadpool.net.a")
                            addMethod {
                                name("onWork", StringMatchType.Equals)
                                addInvoke {
                                    declaredClass("com.sohu.inputmethod.internet.InternetConnection", StringMatchType.Equals)
                                    name("A0", StringMatchType.Equals)
                                }
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "whitedog_uploader",
                classResult = whiteDogUploaderRes,
                methodName = "onWork",
                hooker = voidNoOpHooker(ruleId)
            )

            val logDiagRes = resolver.resolveClass(
                cacheKey = "sogou.imskit.LogDiagSyncLifecycle",
                fastPathCandidates = listOf("com.sogou.imskit.feature.lib.log.sync.LogDiagSyncLifecycle"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sogou.imskit.feature.lib.log")
                            .matcher(ClassMatcher.create().addMethod(MethodMatcher.create().name("onStartInputView")))
                    ).singleOrNull()?.descriptor
                }
            )
            listOf("onStartInput", "onStartInputView").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "logdiag_$mName",
                    classResult = logDiagRes,
                    methodName = mName,
                    primary = true,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val logFRes = resolver.resolveClass(
                cacheKey = "sogou.imskit.log.f",
                fastPathCandidates = listOf("com.sogou.imskit.feature.lib.log.f"),
                dexKitFinder = { dk -> findClassByStrings(dk, "用户反馈上报") }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "imskit_log_f_c",
                cacheKey = "sogou.imskit.log.f#c",
                classResult = logFRes,
                methodName = "c",
                Boolean::class.javaPrimitiveType!!,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "imskit_log_f_g",
                cacheKey = "sogou.imskit.log.f#g",
                classResult = logFRes,
                methodName = "g",
                Long::class.javaPrimitiveType!!,
                Long::class.javaPrimitiveType!!,
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val logInitRes = resolver.resolveClass(
                cacheKey = "sogou.imskit.LogInitServiceImpl_a",
                fastPathCandidates = listOf("com.sogou.imskit.feature.lib.log.initservice.LogInitServiceImpl\$a"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.imskit.feature.lib.log.initservice")
                        matcher {
                            addMethod {
                                name("a", StringMatchType.Equals)
                                paramCount(0)
                                returnType("void")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "imskit_log_init_a",
                classResult = logInitRes,
                methodName = "a",
                hooker = voidNoOpHooker(ruleId)
            )

            val bigDataUploaderRes = resolver.resolveClass(
                cacheKey = "sogou.bigdata.internet.a",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.bigdata.internet.a"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.sogou.bigdata.internet")
                        matcher {
                            addMethod { name("g", StringMatchType.Equals) }
                            addMethod { name("i", StringMatchType.Equals) }
                            addMethod { name("j", StringMatchType.Equals) }
                            addMethod { name("k", StringMatchType.Equals) }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("g", "i", "j", "k").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "bigdata_net_$mName",
                    classResult = bigDataUploaderRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            stats.finalizeRule(registry, ruleId, "WhiteDogLogDiag")
        }
    }

    private fun installBeaconQimeiRule(
        resolver: SymbolResolver,
        registry: HookRegistry,
        sharedJsInterfaceRes: Lazy<ResolveResult<Class<*>>>
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_TELEMETRY_BEACON_QIMEI
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val beaconBridgeRes = resolver.resolveClass(
                cacheKey = "sogou.beaconbridge.c",
                fastPathCandidates = listOf("com.sohu.inputmethod.beaconbridge.c"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.beaconbridge")
                        matcher {
                            addMethod {
                                name("e", StringMatchType.Equals)
                                paramCount(0)
                                returnType("void")
                            }
                            addMethod {
                                name("f", StringMatchType.Equals)
                                paramCount(0)
                                returnType("void")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("e", "f").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "beaconbridge_$mName",
                    classResult = beaconBridgeRes,
                    methodName = mName,
                    primary = mName == "e",
                    hooker = voidNoOpHooker(ruleId)
                )
            }
            val beaconBridgeARes = resolver.resolveClass(
                cacheKey = "sogou.beaconbridge.c_a",
                fastPathCandidates = listOf("com.sohu.inputmethod.beaconbridge.c\$a"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.beaconbridge")
                        matcher {
                            addMethod {
                                name("updateScookie", StringMatchType.Equals)
                                paramCount(0)
                                returnType("void")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "beaconbridge_updateScookie",
                classResult = beaconBridgeARes,
                methodName = "updateScookie",
                hooker = voidNoOpHooker(ruleId)
            )

            val beaconDRes = resolver.resolveClass(
                cacheKey = "sogou.beacon.d",
                fastPathCandidates = listOf("com.sogou.inputmethod.beacon.d"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.inputmethod.beacon")
                        matcher {
                            usingStrings(listOf("eventName"), StringMatchType.Contains)
                            addMethod {
                                name("r", StringMatchType.Equals)
                                paramCount(2)
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("l", "o", "w").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "beacon_d_$mName",
                    classResult = beaconDRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }
            listOf("r", "v").forEach { mName ->
                val retType = safeDeclaredMethods(beaconDRes.value).firstOrNull {
                    it.name == mName &&
                        it.parameterTypes.contentEquals(arrayOf(String::class.java, Map::class.java))
                }?.returnType
                hookMethodOnClass(
                    registry, resolver, stats, ruleId,
                    hookId = "beacon_d_$mName",
                    cacheKey = "sogou.beacon.d#$mName",
                    classResult = beaconDRes,
                    methodName = mName,
                    String::class.java,
                    Map::class.java,
                    hooker = XposedInterface.Hooker { chain ->
                        if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                        registry.incrementBlocked(ruleId)
                        createBlockedBeaconEventResult(retType)
                    }
                )
            }

            val beaconERes = resolver.resolveClass(
                cacheKey = "sogou.beacon.e",
                fastPathCandidates = listOf("com.sogou.inputmethod.beacon.e"),
                dexKitFinder = { dk -> findClassByStrings(dk, "ostar_mmkv", "ostar36", "ostar16") }
            )
            listOf("d", "e", "h", "l").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "beacon_e_$mName",
                    classResult = beaconERes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "beacon_e_f",
                classResult = beaconERes,
                methodName = "f",
                hooker = emptyStringHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_js_getQ36",
                cacheKey = "sogou.hotwords.SogouJSInterface#getQ36",
                classResult = sharedJsInterfaceRes.value,
                methodName = "getQ36",
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )

            val qimeiRes = resolver.resolveClass(
                cacheKey = "sogou.qimei.QimeiHelper",
                fastPathCandidates = listOf("com.sogou.plus.qimei.QimeiHelper"),
                dexKitFinder = { dk -> findClassByStrings(dk, "1AE26A39Q74EU7JH", "sogou-plus") }
            )
            listOf("init", "getQimeiAsyc").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "qimei_$mName",
                    classResult = qimeiRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "qimei_getQimeiSync",
                cacheKey = "sogou.qimei.QimeiHelper#getQimeiSync",
                classResult = qimeiRes,
                methodName = "getQimeiSync",
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )

            stats.finalizeRule(registry, ruleId, "BeaconQimei")
        }
    }

    private fun installThirdPartyAdsQmRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_TELEMETRY_THIRDPARTY_ADS_QM
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val qmRes = resolver.resolveClass(
                cacheKey = "sogou.questmobile.w",
                fastPathCandidates = listOf("com.sohu.util.w"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sohu.util")
                            .matcher(
                                ClassMatcher.create().addMethod(
                                    MethodMatcher.create().addInvoke(
                                        MethodMatcher.create().declaredClass("com.q.Qt")
                                    )
                                )
                            )
                    ).singleOrNull()?.descriptor
                }
            )
            listOf("a", "b", "c").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "qm_w_$mName",
                    classResult = qmRes,
                    methodName = mName,
                    primary = mName == "a",
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val miAdvDRes = resolver.resolveClass(
                cacheKey = "sogou.miadvertise.d",
                fastPathCandidates = listOf("com.sogou.inputmethod.oem.xiaomi.miadvertise.d"),
                dexKitFinder = { dk ->
                    findClassByStrings(dk, "https://adv.sec.miui.com/info/icon", "XiaomiVoiceAssistUtils")
                }
            )
            listOf("onWork", "c", "d", "e").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "miadv_d_$mName",
                    classResult = miAdvDRes,
                    methodName = mName,
                    primary = mName == "onWork",
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val miAdvGRes = resolver.resolveClass(
                cacheKey = "sogou.miadvertise.g",
                fastPathCandidates = listOf("com.sogou.inputmethod.oem.xiaomi.miadvertise.g"),
                dexKitFinder = { dk -> findClassByStrings(dk, "https://adv.sec.miui.com/info/icon_st") }
            )
            listOf("onWork", "a").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "miadv_g_$mName",
                    classResult = miAdvGRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val miAdvHelperRes = resolver.resolveClass(
                cacheKey = "sogou.miadvertise.MiAdvertiseDataHelper",
                fastPathCandidates = listOf("com.sogou.inputmethod.oem.xiaomi.miadvertise.MiAdvertiseDataHelper"),
                dexKitFinder = { dk -> findClassByStrings(dk, "cme_5", "cme_15", "android.intent.action.VIEW") }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "miadv_helper_k",
                cacheKey = "sogou.miadvertise.MiAdvertiseDataHelper#k",
                classResult = miAdvHelperRes,
                methodName = "k",
                returnType = "boolean",
                hooker = booleanFalseHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "miadv_helper_p",
                cacheKey = "sogou.miadvertise.MiAdvertiseDataHelper#p",
                classResult = miAdvHelperRes,
                methodName = "p",
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val miAdvFRes = resolver.resolveClass(
                cacheKey = "sogou.miadvertise.f",
                fastPathCandidates = listOf("com.sogou.inputmethod.oem.xiaomi.miadvertise.f"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sogou.inputmethod.oem.xiaomi.miadvertise")
                            .matcher(
                                ClassMatcher.create().addMethod(
                                    MethodMatcher.create()
                                        .name("a")
                                        .paramTypes("java.lang.String", "java.lang.String")
                                )
                            )
                    ).singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "miadv_f_a",
                cacheKey = "sogou.miadvertise.f#a",
                classResult = miAdvFRes,
                methodName = "a",
                String::class.java,
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            stats.finalizeRule(registry, ruleId, "ThirdPartyAdsQm")
        }
    }

    private fun installCrashApmRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_TELEMETRY_CRASH_APM
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val scrashlyRes = resolver.resolveClass(
                cacheKey = "sogou.scrashly.a",
                fastPathCandidates = listOf("com.sogou.scrashly.a"),
                dexKitFinder = { dk -> findClassByStrings(dk, "Bugly oa 2.0 is open!", "work_thread_lag") }
            )
            listOf("d", "f", "g").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "scrashly_$mName",
                    classResult = scrashlyRes,
                    methodName = mName,
                    primary = mName == "d",
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val u3Res = resolver.resolveClass(
                cacheKey = "sogou.u3",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.u3"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.sogou")
                        matcher {
                            addMethod {
                                name("i", StringMatchType.Equals)
                                paramTypes("android.app.Application")
                                returnType("void")
                            }
                            addMethod {
                                name("g", StringMatchType.Equals)
                                paramTypes("android.app.Application")
                                returnType("void")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "u3_i",
                cacheKey = "sogou.u3#i",
                classResult = u3Res,
                methodName = "i",
                Application::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val errorTraceRes = resolver.resolveClass(
                cacheKey = "sogou.engine.ErrorTrace",
                fastPathCandidates = listOf("com.sohu.inputmethod.engine.ErrorTrace"),
                dexKitFinder = { dk -> findClassByStrings(dk, "native_crash.txt", "sogou_anr.txt") }
            )
            listOf("installNativeCollect", "checkCrashFileOnCreate", "setNativeCollectSwitch").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "errortrace_$mName",
                    classResult = errorTraceRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val nativeCrashDRes = resolver.resolveClass(
                cacheKey = "sogou.nativecrashcollector.d",
                fastPathCandidates = listOf("com.sogou.nativecrashcollector.d"),
                dexKitFinder = { dk -> findClassByStrings(dk, "crash_collect") }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "nativecrash_d_h",
                classResult = nativeCrashDRes,
                methodName = "h",
                hooker = voidNoOpHooker(ruleId)
            )
            listOf("e", "f").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "nativecrash_d_$mName",
                    classResult = nativeCrashDRes,
                    methodName = mName,
                    hooker = XposedInterface.Hooker { chain ->
                        if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                        registry.incrementBlocked(ruleId)
                        -1
                    }
                )
            }

            val messageAnrARes = resolver.resolveClass(
                cacheKey = "sogou.messageAnr.net.a",
                fastPathCandidates = listOf("com.sohu.inputmethod.messageAnr.net.a"),
                dexKitFinder = { dk -> findClassByStrings(dk, "http://ping-msganr.android.shouji.sogou.com/msganr") }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "message_anr_a",
                classResult = messageAnrARes,
                methodName = "a",
                hooker = voidNoOpHooker(ruleId)
            )

            val fluencyNativeRes = resolver.resolveClass(
                cacheKey = "sogou.performance.FluencyNativeInterface",
                fastPathCandidates = listOf("com.sogou.lib.performance.fluency.FluencyNativeInterface"),
                dexKitFinder = { dk -> findClassByStrings(dk, "fluency_monitor") }
            )
            listOf("refreshHookFromSettings", "setInputThreadNative", "setRecordThresholdNative").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "fluency_native_$mName",
                    classResult = fluencyNativeRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }
            listOf("hookBinderNative", "hookIoNative", "hookAssetManagerNative").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "fluency_native_$mName",
                    classResult = fluencyNativeRes,
                    methodName = mName,
                    hooker = booleanFalseHooker(ruleId)
                )
            }

            stats.finalizeRule(registry, ruleId, "CrashApm")
        }
    }

    // -------------------------------------------------------------------------
    // 4. sogou.cloud.* & sogou.context.*
    // -------------------------------------------------------------------------

    private fun installCloudGatewayAndPiggybackRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CLOUD_GATEWAY_AND_PIGGYBACK
        val targetManaged = ConfigBridge.isTargetManaged(target)
        val gatewayEnabledAtInstall = isPointEnabled(ruleId)
        val mutualEnabledAtInstall = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA)

        if (!targetManaged) {
            registry.reportSkipped(ruleId, "已暂停接管")
            return
        }

        val stats = RuleStats()

        // Global Cloud Input gates: ALWAYS installed while Sogou is managed regardless of per-rule toggles.
        val cloudSettingsCls = resolver.resolveClass(
            cacheKey = "sogou.cloud.CloudSettings",
            fastPathCandidates = listOf("com.sohu.inputmethod.cloud.CloudSettings"),
            dexKitFinder = { dk ->
                dk.findClass {
                    searchPackages("com.sohu.inputmethod.cloud")
                    matcher {
                        addMethod { name("isCloudSinkOn", StringMatchType.Equals) }
                        addMethod { name("isCloudSinkAndSendWhenDown", StringMatchType.Equals) }
                    }
                }.singleOrNull()?.descriptor
            }
        )
        hookMethodOnClass(
            registry, resolver, stats, ruleId,
            hookId = "cloud_settings_on",
            cacheKey = "sogou.CloudSettings#isCloudSinkOn",
            classResult = cloudSettingsCls,
            methodName = "isCloudSinkOn",
            Context::class.java,
            returnType = "boolean",
            primary = true,
            hooker = XposedInterface.Hooker { chain ->
                if (!ConfigBridge.isTargetManaged(target)) return@Hooker chain.proceed()
                ConfigBridge.isCloudEnabled(target)
            }
        )
        hookMethodOnClass(
            registry, resolver, stats, ruleId,
            hookId = "cloud_settings_send_down",
            cacheKey = "sogou.CloudSettings#isCloudSinkAndSendWhenDown",
            classResult = cloudSettingsCls,
            methodName = "isCloudSinkAndSendWhenDown",
            Context::class.java,
            returnType = "boolean",
            hooker = XposedInterface.Hooker { chain ->
                if (ConfigBridge.isTargetManaged(target) && !ConfigBridge.isCloudEnabled(target)) {
                    return@Hooker false
                }
                chain.proceed()
            }
        )

        val cloudMgrRes = resolver.resolveClass(
            cacheKey = "sogou.cloud.CloudMgr",
            fastPathCandidates = listOf("com.sogou.inputmethod.cloud.api.ctrl.CloudMgr"),
            dexKitFinder = { dk -> findClassByStrings(dk, "postCloudRequest", "cloud_assoc_type") }
        )
        val cloudMgrCls = cloudMgrRes.value
        if (cloudMgrCls != null) {
            val j2Class = resolver.resolveClass(
                cacheKey = "sogou.j2",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.j2"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.sogou")
                        matcher {
                            usingStrings(listOf("  enableCht=", "/symuser.xml"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            cachedCloudMgrRefs = CloudMgrRefs(cloudMgrCls, j2Class)
            val refs = cachedCloudMgrRefs!!

            listOf(
                Triple("cloudmgr_O0", "O0", arrayOf<Class<*>>(Boolean::class.javaPrimitiveType!!)),
                Triple("cloudmgr_m", "m", arrayOf<Class<*>>(Boolean::class.javaPrimitiveType!!)),
                Triple("cloudmgr_P0", "P0", emptyArray())
            ).forEach { (hookId, mName, params) ->
                hookMethodOnClass(
                    registry, resolver, stats, ruleId,
                    hookId = hookId,
                    cacheKey = "sogou.CloudMgr#$mName",
                    classResult = cloudMgrRes,
                    methodName = mName,
                    *params,
                    returnType = "void",
                    hooker = XposedInterface.Hooker { chain ->
                        if (ConfigBridge.isTargetManaged(target) && !ConfigBridge.isCloudEnabled(target)) {
                            registry.incrementBlocked(ruleId)
                            return@Hooker null
                        }
                        chain.proceed()
                    }
                )
            }

            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "cloudmgr_k_assoc",
                cacheKey = "sogou.CloudMgr#k",
                classResult = cloudMgrRes,
                methodName = "k",
                CharSequence::class.java,
                Int::class.javaPrimitiveType!!,
                Char::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                String::class.java,
                Int::class.javaPrimitiveType!!,
                hooker = XposedInterface.Hooker { chain ->
                    if (ConfigBridge.isTargetManaged(target) && !ConfigBridge.isCloudEnabled(target)) {
                        registry.incrementBlocked(ruleId)
                        return@Hooker null
                    }
                    chain.proceed()
                }
            )

            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "cloudmgr_p_state",
                cacheKey = "sogou.CloudMgr#p",
                classResult = cloudMgrRes,
                methodName = "p",
                returnType = "int",
                hooker = XposedInterface.Hooker { chain ->
                    if (ConfigBridge.isTargetManaged(target) && !ConfigBridge.isCloudEnabled(target)) {
                        return@Hooker 0
                    }
                    chain.proceed()
                }
            )

            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "cloudmgr_o_refresh",
                cacheKey = "sogou.CloudMgr#o",
                classResult = cloudMgrRes,
                methodName = "o",
                returnType = "void",
                hooker = XposedInterface.Hooker { chain ->
                    val thisObj = chain.getThisObject()
                    val cfg = ConfigBridge.current
                    val managed = cfg.isImeManaged(target)
                    val forceOn = managed && cfg.cloudOverrideFor(target) == CloudOverride.FORCE_ON
                    val currentM = runCatching { refs.fieldM?.getInt(thisObj) ?: 0 }.getOrDefault(0)
                    val transition = SogouPayloadSanitizer.resolveCloudMgrStateTransition(
                        currentState = currentM,
                        savedPriorState = forcedCloudMgrPriorM.get(),
                        forceOn = forceOn
                    )
                    if (transition.newState != null) {
                        runCatching { refs.fieldM?.setInt(thisObj, transition.newState) }
                    }
                    forcedCloudMgrPriorM.set(transition.nextSavedPriorState)

                    if (managed && !cfg.effectiveCloudEnabled(target)) {
                        stopCloudMgrLocked(thisObj, refs)
                        registry.incrementBlocked(ruleId)
                        return@Hooker null
                    }
                    chain.proceed()
                }
            )
        } else {
            stats.record(registry, ruleId, "cloud_mgr_class", cloudMgrRes, hooked = false)
        }

        registerLiveCloudConfigListener(registry)

        // Anti-Piggybacking: com.sohu.inputmethod.sogou.v2#{l, e} gated on RULE_SOGOU_CLOUD_GATEWAY_AND_PIGGYBACK
        if (gatewayEnabledAtInstall) {
            val v2Res = resolver.resolveClass(
                cacheKey = "sogou.v2",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.v2"),
                dexKitFinder = { dk -> findClassByStrings(dk, "pwpur", "offpwpur", "indiv_applist") }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "v2_piggyback_l",
                cacheKey = "sogou.v2#l",
                classResult = v2Res,
                methodName = "l",
                returnType = "com.sogou.inputmethod.sogou.cloud.data.BaseInputRequestInfo",
                primary = true,
                hooker = nullHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "v2_piggyback_e",
                cacheKey = "sogou.v2#e",
                classResult = v2Res,
                methodName = "e",
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
        }

        // Shared WebSocket & InputRequests senders: ALWAYS installed while Sogou is managed so global
        // pure-local cloud blocking cannot be bypassed; mutual_bigdata remains independently gated.
        run {
            val reqTypeMethodRef = AtomicReference<Method?>(null)
            val failMethodRef = AtomicReference<Method?>(null)

            fun resolveReqMethods(reqObj: Any) {
                if (reqTypeMethodRef.get() == null) {
                    findMethodInHierarchy(reqObj.javaClass, "getRequestType")?.let {
                        reqTypeMethodRef.compareAndSet(null, it)
                    }
                }
                if (failMethodRef.get() == null) {
                    findMethodInHierarchy(reqObj.javaClass, "onDownloadFail", Context::class.java)?.let {
                        failMethodRef.compareAndSet(null, it)
                    }
                }
            }

            val wsRuleId = ruleId
            val wsStats = if (gatewayEnabledAtInstall) stats else null

            val wsSenderCls = resolver.resolveClass(
                cacheKey = "sogou.CloudWebSocketSender",
                fastPathCandidates = listOf("com.sohu.inputmethod.input.CloudWebSocketSender"),
                dexKitFinder = { dk -> findClassByStrings(dk, "Net_Switch", "ErrCode") }
            )
            val reqSenderCls = resolver.resolveClass(
                cacheKey = "sogou.InputRequestsSender",
                fastPathCandidates = listOf("com.sohu.inputmethod.input.InputRequestsSender"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.input")
                        matcher {
                            usingStrings(listOf("input_requests_key"), StringMatchType.Contains)
                            addMethod {
                                name("b", StringMatchType.Equals)
                                paramCount(1)
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val baseReqCls = resolver.resolveClass(
                cacheKey = "sogou.BaseInputRequestInfo",
                fastPathCandidates = listOf("com.sogou.inputmethod.sogou.cloud.data.BaseInputRequestInfo"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sogou.inputmethod.sogou.cloud.data")
                            .matcher(ClassMatcher.create().addMethod(MethodMatcher.create().name("getRequestTypeName")))
                    ).singleOrNull()?.descriptor
                }
            )

            val wsRes = resolver.resolveMethod(
                cacheKey = "sogou.CloudWebSocketSender#g",
                fastPath = {
                    val cls = wsSenderCls.value
                    val req = baseReqCls.value
                    if (cls != null && req != null) {
                        SymbolResolver.findDeclaredMethodOrNull(cls, "g", Context::class.java, req)
                    } else null
                },
                dexKitFinder = { dk ->
                    val cls = wsSenderCls.value ?: return@resolveMethod null
                    val req = baseReqCls.value ?: return@resolveMethod null
                    dk.findMethod {
                        searchPackages("com.sohu.inputmethod.input")
                        matcher {
                            declaredClass(cls.name, StringMatchType.Equals)
                            name("g", StringMatchType.Equals)
                            paramTypes("android.content.Context", req.name)
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                },
                structuralFallback = {
                    safeDeclaredMethods(wsSenderCls.value).firstOrNull { method ->
                        method.name == "g" &&
                            method.parameterCount == 2 &&
                            method.parameterTypes[0] == Context::class.java
                    }
                }
            )
            val wsHooked = registry.hookExecutable(
                ruleId = wsRuleId,
                hookId = "cloud_ws_sender_g",
                resolveResult = wsRes,
                priority = XposedInterface.PRIORITY_DEFAULT,
                onFailureStatus = HookMatchStatus.FAILED,
                hooker = XposedInterface.Hooker { chain ->
                    val ctx = chain.getArg(0) as? Context
                    val reqObj = chain.getArg(1) ?: return@Hooker chain.proceed()
                    resolveReqMethods(reqObj)
                    val reqType = runCatching { reqTypeMethodRef.get()?.invoke(reqObj) as? Int }.getOrNull()
                        ?: return@Hooker chain.proceed()
                    val mutualOn = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA)
                    when (
                        SogouPayloadSanitizer.evaluateWebSocketRequest(
                            requestType = reqType,
                            targetManaged = ConfigBridge.isTargetManaged(target),
                            cloudEnabled = ConfigBridge.isCloudEnabled(target),
                            blockMutualData = mutualOn
                        )
                    ) {
                        SogouPayloadSanitizer.OutboundDecision.ALLOW -> chain.proceed()
                        SogouPayloadSanitizer.OutboundDecision.BLOCK_CLOUD -> {
                            if (ctx != null) runCatching { failMethodRef.get()?.invoke(reqObj, ctx) }
                            registry.incrementBlocked(ruleId)
                            null
                        }
                        SogouPayloadSanitizer.OutboundDecision.BLOCK_MUTUAL -> {
                            if (ctx != null) runCatching { failMethodRef.get()?.invoke(reqObj, ctx) }
                            registry.incrementBlocked(HookRuleCatalog.RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA)
                            null
                        }
                    }
                }
            )
            wsStats?.record(registry, wsRuleId, "cloud_ws_sender_g", wsRes, wsHooked, primary = true)

            val reqRes = resolver.resolveMethod(
                cacheKey = "sogou.InputRequestsSender#b",
                fastPath = {
                    val cls = reqSenderCls.value
                    val req = baseReqCls.value
                    if (cls != null && req != null) {
                        SymbolResolver.findDeclaredMethodOrNull(cls, "b", req)
                    } else null
                },
                dexKitFinder = { dk ->
                    val cls = reqSenderCls.value ?: return@resolveMethod null
                    val req = baseReqCls.value ?: return@resolveMethod null
                    dk.findMethod {
                        searchPackages("com.sohu.inputmethod.input")
                        matcher {
                            declaredClass(cls.name, StringMatchType.Equals)
                            name("b", StringMatchType.Equals)
                            paramTypes(req.name)
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                },
                structuralFallback = {
                    safeDeclaredMethods(reqSenderCls.value).firstOrNull { method ->
                        method.name == "b" &&
                            method.parameterCount == 1 &&
                            method.parameterTypes[0] != String::class.java
                    }
                }
            )
            val reqHooked = registry.hookExecutable(
                ruleId = wsRuleId,
                hookId = "input_requests_sender_b",
                resolveResult = reqRes,
                priority = XposedInterface.PRIORITY_DEFAULT,
                onFailureStatus = HookMatchStatus.DEGRADED,
                hooker = XposedInterface.Hooker { chain ->
                    val reqObj = chain.getArg(0) ?: return@Hooker chain.proceed()
                    resolveReqMethods(reqObj)
                    val reqType = runCatching { reqTypeMethodRef.get()?.invoke(reqObj) as? Int }.getOrNull()
                        ?: return@Hooker chain.proceed()
                    val mutualOn = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA)
                    when (
                        SogouPayloadSanitizer.evaluateWebSocketRequest(
                            requestType = reqType,
                            targetManaged = ConfigBridge.isTargetManaged(target),
                            cloudEnabled = ConfigBridge.isCloudEnabled(target),
                            blockMutualData = mutualOn
                        )
                    ) {
                        SogouPayloadSanitizer.OutboundDecision.ALLOW -> chain.proceed()
                        SogouPayloadSanitizer.OutboundDecision.BLOCK_CLOUD -> {
                            registry.incrementBlocked(ruleId)
                            null
                        }
                        SogouPayloadSanitizer.OutboundDecision.BLOCK_MUTUAL -> {
                            registry.incrementBlocked(HookRuleCatalog.RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA)
                            null
                        }
                    }
                }
            )
            wsStats?.record(registry, wsRuleId, "input_requests_sender_b", reqRes, reqHooked)
        }

        if (gatewayEnabledAtInstall) {
            stats.finalizeRule(registry, ruleId, "CloudGatewayPiggyback")
        } else {
            registry.reportSkipped(ruleId, "规则已关闭")
        }
    }

    private fun installSharedCloudAndContextPayloadRules(
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val payloadRuleIds = listOf(
            HookRuleCatalog.RULE_SOGOU_CLOUD_PAYLOAD_DEVICE_DIALOGUE,
            HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT,
            HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE,
            HookRuleCatalog.RULE_SOGOU_CONTEXT_TOUCH_POSITIONS
        )
        val enabledRules = payloadRuleIds.filter { isPointEnabled(it) }
        payloadRuleIds.filterNot { it in enabledRules }.forEach { disabledRule ->
            registry.reportSkipped(disabledRule, "规则已关闭")
        }
        if (enabledRules.isEmpty()) return

        val anchorRuleId = enabledRules.first()
        val stats = RuleStats()

        val reqInfoRes = resolver.resolveClass(
            cacheKey = "sogou.cloud.CloudRequestInfo",
            fastPathCandidates = listOf("com.sohu.inputmethod.cloud.CloudRequestInfo"),
            dexKitFinder = { dk ->
                dk.findClass {
                    searchPackages("com.sohu.inputmethod.cloud")
                    matcher {
                        addMethod { name("getUncommittedText", StringMatchType.Equals) }
                        addMethod { name("getSyllableInfos", StringMatchType.Equals) }
                    }
                }.singleOrNull()?.descriptor
            }
        )
        val cloudBRes = resolver.resolveClass(
            cacheKey = "sogou.cloud.b",
            fastPathCandidates = listOf("com.sohu.inputmethod.sogou.cloud.b"),
            dexKitFinder = { dk ->
                dk.findClass {
                    searchPackages("com.sohu.inputmethod.sogou.cloud")
                    matcher {
                        usingStrings(listOf("#%#", "&nbsp", "UTF-16LE"), StringMatchType.Contains)
                        addMethod {
                            name("h", StringMatchType.Equals)
                            paramTypes("com.sohu.inputmethod.cloud.CloudRequestInfo", "android.content.Context")
                        }
                    }
                }.singleOrNull()?.descriptor
            }
        )
        val cloudARes = resolver.resolveClass(
            cacheKey = "sogou.cloud.a",
            fastPathCandidates = listOf("com.sohu.inputmethod.sogou.cloud.a"),
            dexKitFinder = { dk -> findClassByStrings(dk, "cloud_assoc_before_context", "#%#", "UTF-16LE") }
        )

        val sharedHooker = payloadSanitizerHooker()

        hookResolvedMethod(
            registry, resolver, stats, anchorRuleId,
            hookId = "cloud_body_builder_b_h",
            cacheKey = "sogou.cloud.b#h",
            fastPath = {
                val cls = cloudBRes.value
                val reqInfoCls = reqInfoRes.value
                if (cls != null && reqInfoCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(cls, "h", reqInfoCls, Context::class.java)
                } else null
            },
            dexKitFinder = { dk ->
                findMethodBy(
                    dk = dk,
                    paramTypes = arrayOf("com.sohu.inputmethod.cloud.CloudRequestInfo", "android.content.Context"),
                    strings = arrayOf("#%#", "&nbsp", "UTF-16LE")
                )
            },
            structuralFallback = {
                val reqInfoCls = reqInfoRes.value
                safeDeclaredMethods(cloudBRes.value).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 2 &&
                        (reqInfoCls == null || method.parameterTypes[0] == reqInfoCls) &&
                        method.parameterTypes[1] == Context::class.java &&
                        method.returnType != Void.TYPE
                }
            },
            primary = true,
            hooker = sharedHooker
        )

        hookResolvedMethod(
            registry, resolver, stats, anchorRuleId,
            hookId = "cloud_assoc_builder_a_i",
            cacheKey = "sogou.cloud.a#i",
            fastPath = {
                val cls = cloudARes.value
                val reqInfoCls = reqInfoRes.value
                if (cls != null && reqInfoCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(cls, "i", reqInfoCls, Context::class.java, Bundle::class.java)
                } else null
            },
            dexKitFinder = { dk ->
                findMethodBy(
                    dk = dk,
                    paramTypes = arrayOf(
                        "com.sohu.inputmethod.cloud.CloudRequestInfo",
                        "android.content.Context",
                        "android.os.Bundle"
                    ),
                    strings = arrayOf("cloud_assoc_before_context", "#%#", "UTF-16LE")
                )
            },
            structuralFallback = {
                val reqInfoCls = reqInfoRes.value
                safeDeclaredMethods(cloudARes.value).firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 3 &&
                        (reqInfoCls == null || method.parameterTypes[0] == reqInfoCls) &&
                        method.parameterTypes[1] == Context::class.java &&
                        method.parameterTypes[2] == Bundle::class.java &&
                        method.returnType != Void.TYPE
                }
            },
            primary = true,
            hooker = sharedHooker
        )

        val anchorReport = stats.buildFinalReport(registry.getReport(anchorRuleId), anchorRuleId, "CloudPayload")
        enabledRules.forEach { rId ->
            registry.reportRule(
                ruleId = rId,
                status = anchorReport.status,
                matchedMethod = anchorReport.matchedMethod,
                source = anchorReport.source,
                detail = anchorReport.detail
            )
        }
    }

    private fun payloadSanitizerHooker(): XposedInterface.Hooker {
        return XposedInterface.Hooker { chain ->
            val body = chain.proceed()
            if (body != null && ConfigBridge.isTargetManaged(target)) {
                val stripDeviceDialogue = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CLOUD_PAYLOAD_DEVICE_DIALOGUE)
                val stripCursor = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT)
                val maskHost = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)
                val stripTouch = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_TOUCH_POSITIONS)

                val report = SogouPayloadSanitizer.sanitizeClientRequestBodyDetailed(
                    body = body,
                    sanitizePayloadDeviceDialogue = stripDeviceDialogue,
                    stripCursorContext = stripCursor,
                    maskHostPackage = maskHost,
                    stripTouchPositions = stripTouch
                )
                if (report.modified) {
                    if (report.strippedFields.any { it == "dialogue" || it.startsWith("device.") }) {
                        registryOrNullIncrement(HookRuleCatalog.RULE_SOGOU_CLOUD_PAYLOAD_DEVICE_DIALOGUE)
                    }
                    if (report.strippedFields.any { it == "stdata" || it == "stdataBack" }) {
                        registryOrNullIncrement(HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT)
                    }
                    if ("env" in report.strippedFields) {
                        registryOrNullIncrement(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)
                    }
                    if (report.strippedFields.any { it == "keypositions" || it == "usePosAdjust" }) {
                        registryOrNullIncrement(HookRuleCatalog.RULE_SOGOU_CONTEXT_TOUCH_POSITIONS)
                    }
                }
            }
            body
        }
    }

    private fun installWangzaiVpaRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CLOUD_WANGZAI_VPA
        val vpaEnabled = isPointEnabled(ruleId)
        val cursorEnabled = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT)
        val hostPkgEnabled = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)
        val locEnabled = isPointEnabled(HookRuleCatalog.RULE_SOGOU_SCAN_LOCATION_WIFI)

        if (!vpaEnabled) {
            registry.reportSkipped(ruleId, "规则已关闭")
        }
        if (!vpaEnabled && !cursorEnabled && !hostPkgEnabled && !locEnabled) return

        val stats = RuleStats()

        val fanlingxiRes = resolver.resolveClass(
            cacheKey = "sogou.flx.FanlingxiParam",
            fastPathCandidates = listOf("com.sohu.inputmethod.flx.trigger.FanlingxiParam"),
            dexKitFinder = { dk ->
                findClassByStrings(dk, "android_oem_xiaomi_sogou", "vc8v7vghw7v278vn2v8239vh29vh890m")
            }
        )
        val bodyFieldRef = AtomicReference<Field?>(null)
        hookClassMethodsByName(
            registry, stats, ruleId,
            hookIdPrefix = "flx_param_assemble",
            classResult = fanlingxiRes,
            methodName = "assemble",
            primary = true,
            hooker = XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val thisObj = chain.getThisObject() ?: return@Hooker result
                val stripVpa = isPointEnabled(ruleId)
                val stripCursor = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT)
                val stripHost = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)
                val stripLoc = isPointEnabled(HookRuleCatalog.RULE_SOGOU_SCAN_LOCATION_WIFI)
                if (!stripVpa && !stripCursor && !stripHost && !stripLoc) return@Hooker result

                val field = bodyFieldRef.get()
                    ?: findField(thisObj.javaClass, "clientRequestBody")?.also { bodyFieldRef.compareAndSet(null, it) }
                val reqBody = runCatching { field?.get(thisObj) }.getOrNull() ?: return@Hooker result
                val report = SogouPayloadSanitizer.sanitizeWangzaiVpaRequest(
                    requestBody = reqBody,
                    stripDeviceDialogue = stripVpa,
                    stripLocation = stripVpa || stripLoc,
                    stripHostPackage = stripVpa || stripHost,
                    stripCursorContext = stripCursor
                )
                if (report.modified) {
                    if (stripVpa) registry.incrementBlocked(ruleId)
                    else {
                        if (stripCursor && "vpa.cursorContext" in report.strippedFields) {
                            registry.incrementBlocked(HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT)
                        }
                        if (stripLoc && "vpa.location" in report.strippedFields) {
                            registry.incrementBlocked(HookRuleCatalog.RULE_SOGOU_SCAN_LOCATION_WIFI)
                        }
                        if (stripHost && "vpa.clientPkg" in report.strippedFields) {
                            registry.incrementBlocked(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)
                        }
                    }
                }
                result
            }
        )

        if (vpaEnabled) {
            stats.finalizeRule(registry, ruleId, "WangzaiVpa")
        } else {
            registry.reportSkipped(ruleId, "规则已关闭")
        }
    }

    private fun installVoicePrivacyRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CLOUD_VOICE_PRIVACY
        val voiceEnabled = isPointEnabled(ruleId)
        val hostPkgEnabled = isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)

        if (!voiceEnabled) {
            registry.reportSkipped(ruleId, "规则已关闭")
        }
        if (!voiceEnabled && !hostPkgEnabled) return

        val stats = RuleStats()
        val asrConfigRes = resolver.resolveClass(
            cacheKey = "sogou.ai.AsrConfig",
            fastPathCandidates = listOf("com.sogou.ai.nsrss.modules.conf.AsrConfig"),
            dexKitFinder = { dk ->
                dk.findClass(
                    FindClass.create()
                        .searchPackages("com.sogou.ai.nsrss.modules.conf")
                        .matcher(ClassMatcher.create().addMethod(MethodMatcher.create().name("withAID")))
                ).singleOrNull()?.descriptor
            }
        )

        if (voiceEnabled) {
            val voiceModelFRes = resolver.resolveClass(
                cacheKey = "sogou.voice_input.models.f",
                fastPathCandidates = listOf("com.sogou.inputmethod.voice_input.models.f"),
                dexKitFinder = { dk -> findClassByStrings(dk, "namespace=speech_addressbook&key=") }
            )
            hookResolvedMethod(
                registry, resolver, stats, ruleId,
                hookId = "voice_f_contact_id",
                cacheKey = "sogou.voice_input.models.f#e",
                fastPath = { SymbolResolver.findDeclaredMethodOrNull(voiceModelFRes.value, "e", String::class.java) },
                dexKitFinder = { dk ->
                    findMethodBy(
                        dk = dk,
                        searchPackages = arrayOf("com.sogou.inputmethod.voice_input"),
                        returnType = "void",
                        paramTypes = arrayOf("java.lang.String"),
                        strings = arrayOf("namespace=speech_addressbook&key=")
                    )
                },
                primary = true,
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "voice_f_user_identity",
                cacheKey = "sogou.voice_input.models.f#j",
                classResult = voiceModelFRes,
                methodName = "j",
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
                returnType = "void",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    chain.proceed(arrayOf<Any?>("anonymous", "anonymous", false))
                }
            )

            listOf("withAID", "withQID", "withUUID").forEach { mName ->
                hookMethodOnClass(
                    registry, resolver, stats, ruleId,
                    hookId = "asr_$mName",
                    cacheKey = "sogou.ai.AsrConfig#$mName",
                    classResult = asrConfigRes,
                    methodName = mName,
                    String::class.java,
                    hooker = selfReturningHooker(ruleId)
                )
            }

            val mtConfigRes = resolver.resolveClass(
                cacheKey = "sogou.ai.MtConfig",
                fastPathCandidates = listOf("com.sogou.ai.nsrss.modules.conf.MtConfig"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.ai.nsrss.modules.conf")
                        matcher {
                            addMethod { name("withSourceLanguageCode", StringMatchType.Equals) }
                            addMethod { name("withUUID", StringMatchType.Equals) }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "mt_withUUID",
                cacheKey = "sogou.ai.MtConfig#withUUID",
                classResult = mtConfigRes,
                methodName = "withUUID",
                String::class.java,
                hooker = selfReturningHooker(ruleId)
            )
        }

        // Runtime host package & voice privacy shared sinks
        hookMethodOnClass(
            registry, resolver, stats, ruleId,
            hookId = "asr_withRuntimeProductId",
            cacheKey = "sogou.ai.AsrConfig#withRuntimeProductId",
            classResult = asrConfigRes,
            methodName = "withRuntimeProductId",
            String::class.java,
            hooker = XposedInterface.Hooker { chain ->
                val enabled = isPointEnabled(ruleId) || isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)
                if (!enabled) return@Hooker chain.proceed()
                registry.incrementBlocked(
                    if (isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)) {
                        HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE
                    } else ruleId
                )
                chain.getThisObject()
            }
        )

        val voiceModelNRes = resolver.resolveClass(
            cacheKey = "sogou.voice_input.models.n",
            fastPathCandidates = listOf("com.sogou.inputmethod.voice_input.models.n"),
            dexKitFinder = { dk -> findClassByStrings(dk, "VoiceEngineCreater") }
        )
        val metadataFieldRef = AtomicReference<Field?>(null)
        val runtimeInfoFieldRef = AtomicReference<Field?>(null)
        val consumerProductFieldRef = AtomicReference<Field?>(null)
        hookClassMethodsByName(
            registry, stats, ruleId,
            hookIdPrefix = "voice_n_d",
            classResult = voiceModelNRes,
            methodName = "d",
            hooker = XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val enabled = isPointEnabled(ruleId) || isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)
                if (!enabled) return@Hooker result
                val cfgArg = chain.args.getOrNull(0) ?: return@Hooker result
                val metaField = metadataFieldRef.get()
                    ?: findField(cfgArg.javaClass, "metadata")?.also { metadataFieldRef.compareAndSet(null, it) }
                val metadata = runCatching { metaField?.get(cfgArg) }.getOrNull() ?: return@Hooker result
                val rtField = runtimeInfoFieldRef.get()
                    ?: findField(metadata.javaClass, "runtimeInfo")?.also { runtimeInfoFieldRef.compareAndSet(null, it) }
                val runtimeInfo = runCatching { rtField?.get(metadata) }.getOrNull() ?: return@Hooker result
                val prodField = consumerProductFieldRef.get()
                    ?: findField(runtimeInfo.javaClass, "consumerProductId")?.also {
                        consumerProductFieldRef.compareAndSet(null, it)
                    } ?: return@Hooker result
                runCatching {
                    val prev = prodField.get(runtimeInfo) as? String
                    if (!prev.isNullOrEmpty()) {
                        prodField.set(runtimeInfo, "")
                        registry.incrementBlocked(
                            if (isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)) {
                                HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE
                            } else ruleId
                        )
                    }
                }
                result
            }
        )

        val voiceBeanDRes = resolver.resolveClass(
            cacheKey = "sogou.voice_input.bean.d",
            fastPathCandidates = listOf("com.sogou.inputmethod.voice_input.bean.d"),
            dexKitFinder = { dk -> findClassByStrings(dk, "notStopSpaceVoice", "clickKeyStartInput") }
        )
        hookMethodOnClass(
            registry, resolver, stats, ruleId,
            hookId = "voice_bean_d_c",
            cacheKey = "sogou.voice_input.bean.d#c",
            classResult = voiceBeanDRes,
            methodName = "c",
            returnType = "java.lang.String",
            hooker = XposedInterface.Hooker { chain ->
                val raw = chain.proceed() as? String ?: return@Hooker null
                val enabled = isPointEnabled(ruleId) || isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)
                if (!enabled || raw.isBlank()) return@Hooker raw
                runCatching {
                    val obj = JSONObject(raw)
                    if (obj.optString("packageName").isNotEmpty()) {
                        obj.put("packageName", "")
                        registry.incrementBlocked(
                            if (isPointEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE)) {
                                HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE
                            } else ruleId
                        )
                        obj.toString()
                    } else raw
                }.getOrDefault(raw)
            }
        )

        if (voiceEnabled) {
            stats.finalizeRule(registry, ruleId, "VoicePrivacy")
        } else {
            registry.reportSkipped(ruleId, "规则已关闭")
        }
    }

    private fun installMutualBigDataRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val settingMgrRes = resolver.resolveClass(
                cacheKey = "sogou.SettingManager",
                fastPathCandidates = listOf("com.sogou.bu.basic.settings.SettingManager"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sogou.bu.basic.settings")
                            .matcher(ClassMatcher.create().addMethod(MethodMatcher.create().name("t4").returnType("boolean")))
                    ).singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "setting_manager_t4",
                cacheKey = "sogou.SettingManager#t4",
                classResult = settingMgrRes,
                methodName = "t4",
                returnType = "boolean",
                primary = true,
                hooker = booleanFalseHooker(ruleId)
            )

            val mdmRes = resolver.resolveClass(
                cacheKey = "sogou.MutualDataManager",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.mutualdata.MutualDataManager"),
                dexKitFinder = { dk -> findClassByStrings(dk, "#^^#mdst=", "app=EMPTY#^^#mdst=") }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "mdm_q",
                cacheKey = "sogou.MutualDataManager#q",
                classResult = mdmRes,
                methodName = "q",
                returnType = "boolean",
                primary = true,
                hooker = booleanFalseHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "mdm_m",
                cacheKey = "sogou.MutualDataManager#m",
                classResult = mdmRes,
                methodName = "m",
                Int::class.javaPrimitiveType!!,
                hooker = nullHooker(ruleId)
            )
            listOf(
                Triple("mdm_w", "w", arrayOf<Class<*>>(String::class.java, String::class.java, Int::class.javaPrimitiveType!!)),
                Triple("mdm_C", "C", emptyArray()),
                Triple("mdm_D", "D", emptyArray()),
                Triple("mdm_z", "z", arrayOf<Class<*>>(String::class.java)),
                Triple("mdm_j", "j", emptyArray()),
                Triple("mdm_F", "F", arrayOf<Class<*>>(Int::class.javaPrimitiveType!!)),
                Triple("mdm_G", "G", arrayOf<Class<*>>(Int::class.javaPrimitiveType!!)),
                Triple("mdm_A", "A", arrayOf<Class<*>>(Double::class.javaPrimitiveType!!)),
                Triple("mdm_B", "B", arrayOf<Class<*>>(Double::class.javaPrimitiveType!!))
            ).forEach { (hookId, mName, params) ->
                hookMethodOnClass(
                    registry, resolver, stats, ruleId,
                    hookId = hookId,
                    cacheKey = "sogou.MutualDataManager#$mName",
                    classResult = mdmRes,
                    methodName = mName,
                    *params,
                    returnType = "void",
                    primary = mName in setOf("j", "F", "G"),
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val bigDataCRes = resolver.resolveClass(
                cacheKey = "sogou.bigdata.c",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.bigdata.c"),
                dexKitFinder = { dk -> findClassByStrings(dk, "load_sql_cipher_so_now_key", "BigDataManager") }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "bigdata_c_D",
                cacheKey = "sogou.bigdata.c#D",
                classResult = bigDataCRes,
                methodName = "D",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "bigdata_c_R",
                cacheKey = "sogou.bigdata.c#R",
                classResult = bigDataCRes,
                methodName = "R",
                Int::class.javaPrimitiveType!!,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "bigdata_c_E",
                cacheKey = "sogou.bigdata.c#E",
                classResult = bigDataCRes,
                methodName = "E",
                Int::class.javaPrimitiveType!!,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val bigDataMutualCRes = resolver.resolveClass(
                cacheKey = "sogou.bigdata.mutual.c",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.bigdata.mutual.c"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sohu.inputmethod.sogou.bigdata.mutual")
                            .matcher(ClassMatcher.create().addMethod(MethodMatcher.create().name("A")))
                    ).singleOrNull()?.descriptor
                }
            )
            listOf("m", "A").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "bigdata_mutual_c_$mName",
                    classResult = bigDataMutualCRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            stats.finalizeRule(registry, ruleId, "MutualBigData")
        }
    }

    // -------------------------------------------------------------------------
    // 5. sogou.clip.* (4-way split; preserves candidate chip `q.e().m`)
    // -------------------------------------------------------------------------

    private fun installClipSnifferAndKoulingRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CLIP_SNIFFER_AND_KOULING
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val vpaRes = resolver.resolveClass(
                cacheKey = "sogou.VpaClipboardManager",
                fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.vpaclipboard.VpaClipboardManager"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.clipboard.vpaclipboard")
                        matcher {
                            usingStrings(listOf("sp_key_vpa_clipboard_unlimit_show_times"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "vpa_clip_show",
                classResult = vpaRes,
                methodName = "showVpaClipboard",
                primary = true,
                hooker = voidNoOpHooker(ruleId)
            )

            val kouLingRes = resolver.resolveClass(
                cacheKey = "sogou.KouLingClipBoardManager",
                fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.KouLingClipBoardManager"),
                dexKitFinder = { dk -> findClassByStrings(dk, "clipboard_kouling.kl") }
            )
            listOf("n", "o", "p").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "kouling_$mName",
                    classResult = kouLingRes,
                    methodName = mName,
                    primary = mName == "n",
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val asyncPenRes = resolver.resolveClass(
                cacheKey = "sogou.asyncpenetrate.c",
                fastPathCandidates = listOf("com.sohu.inputmethod.asyncpenetrate.c"),
                dexKitFinder = { dk -> findClassByStrings(dk, "no penetrate_code_valid", "has penetrate_code_valid: ") }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "asyncpenetrate_m",
                cacheKey = "sogou.asyncpenetrate.c#m",
                classResult = asyncPenRes,
                methodName = "m",
                String::class.java,
                returnType = "boolean",
                hooker = booleanFalseHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "asyncpenetrate_n",
                cacheKey = "sogou.asyncpenetrate.c#n",
                classResult = asyncPenRes,
                methodName = "n",
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val quickPortalRes = resolver.resolveClass(
                cacheKey = "sogou.BusinessQuickPortalManager",
                fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.BusinessQuickPortal.BusinessQuickPortalManager"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.clipboard.BusinessQuickPortal")
                        matcher {
                            usingStrings(
                                listOf("BusinessQuickPortalManager.writeTpwdToClipboard(...)"),
                                StringMatchType.Contains
                            )
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "quickportal_x",
                classResult = quickPortalRes,
                methodName = "x",
                hooker = voidNoOpHooker(ruleId)
            )

            val clipLogRes = resolver.resolveClass(
                cacheKey = "sogou.ClipboardLogHelper",
                fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.ClipboardLogHelper"),
                dexKitFinder = { dk -> findClassByStrings(dk, "f07ad47b1e860f4fa62e87ff822dd48a", "Clipboard Feedback Error") }
            )
            listOf("d", "e", "q", "r", "t").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "cliplog_$mName",
                    classResult = clipLogRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            stats.finalizeRule(registry, ruleId, "ClipSnifferKouling")
        }
    }

    private fun installClipAutoTranslateAndJsRule(
        resolver: SymbolResolver,
        registry: HookRegistry,
        sharedFlxWebViewRes: Lazy<ResolveResult<Class<*>>>,
        sharedJsInterfaceRes: Lazy<ResolveResult<Class<*>>>
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CLIP_AUTO_TRANSLATE_AND_JS
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val copyAutoTrRes = resolver.resolveClass(
                cacheKey = "sogou.CopyAutoTranslateManager",
                fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.autotranslate.CopyAutoTranslateManager"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.clipboard.autotranslate")
                        matcher {
                            addInterface("android.view.View\$OnClickListener")
                            addMethod {
                                name("n", StringMatchType.Equals)
                                paramTypes("java.lang.String")
                                returnType("void")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "copy_auto_translate_n",
                cacheKey = "sogou.CopyAutoTranslateManager#n",
                classResult = copyAutoTrRes,
                methodName = "n",
                String::class.java,
                returnType = "void",
                primary = true,
                hooker = voidNoOpHooker(ruleId)
            )

            val jsInterfaceRes = sharedJsInterfaceRes.value
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "sogou_js_requestClipBoardText",
                classResult = jsInterfaceRes,
                methodName = "requestClipBoardText",
                primary = true,
                hooker = voidNoOpHooker(ruleId)
            )

            val hotwordsUtilsARes = resolver.resolveClass(
                cacheKey = "sogou.hotwords.utils.a",
                fastPathCandidates = listOf("base.sogou.mobile.hotwordsbase.utils.a"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("base.sogou.mobile.hotwordsbase.utils")
                        matcher {
                            usingStrings(listOf("clipboard"), StringMatchType.Contains)
                            addMethod {
                                name("B", StringMatchType.Equals)
                                paramTypes("android.content.Context")
                                returnType("java.lang.String")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("B", "A").forEach { mName ->
                hookMethodOnClass(
                    registry, resolver, stats, ruleId,
                    hookId = "hotwords_utils_a_$mName",
                    cacheKey = "sogou.hotwords.utils.a#$mName",
                    classResult = hotwordsUtilsARes,
                    methodName = mName,
                    Context::class.java,
                    returnType = "java.lang.String",
                    hooker = emptyStringHooker(ruleId)
                )
            }

            val flxWebViewRes = sharedFlxWebViewRes.value
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "flx_webview_getClipBoardContent",
                cacheKey = "sogou.flx.FlxImeWebView#getClipBoardContent",
                classResult = flxWebViewRes,
                methodName = "getClipBoardContent",
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )

            val flxJsRes = resolver.resolveClass(
                cacheKey = "sogou.flx.FlxProgramJsInterface",
                fastPathCandidates = listOf("com.sohu.inputmethod.flx.miniprogram.view.FlxImeWebView\$FlxProgramJsInterface"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.flx.miniprogram.view")
                        matcher {
                            addMethod { name("requestSogouClipBoardText", StringMatchType.Equals) }
                            addMethod { name("requestClipBoardText", StringMatchType.Equals) }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("requestClipBoardText", "requestSogouClipBoardText").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "flx_js_$mName",
                    classResult = flxJsRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            stats.finalizeRule(registry, ruleId, "ClipAutoTranslateJs")
        }
    }

    private fun installClipDbHistorySaveRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CLIP_DB_HISTORY_SAVE
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val clipPRes = resolver.resolveClass(
                cacheKey = "sogou.clipboard.p",
                fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.p"),
                dexKitFinder = { dk ->
                    findClassByStrings(dk, "ClipboardSPUtils_tryBackupLostClipboardDataFromSystem", "insertClipboard")
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "clipboard_p_y",
                classResult = clipPRes,
                methodName = "y",
                primary = true,
                hooker = voidNoOpHooker(ruleId)
            )
            listOf("z", "T").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "clipboard_p_$mName",
                    classResult = clipPRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            stats.finalizeRule(registry, ruleId, "ClipDbHistorySave")
        }
    }

    private fun installClipStrictBlockListenerRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CLIP_STRICT_BLOCK_LISTENER
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val clipXRes = resolver.resolveClass(
                cacheKey = "sogou.clipboard.x",
                fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.x"),
                dexKitFinder = { dk ->
                    findClassByStrings(dk, "SystemClipboardListener", "SystemClipboardListener_onPrimaryClipChanged_")
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "clipboard_x_onPrimaryClipChanged",
                cacheKey = "sogou.clipboard.x#onPrimaryClipChanged",
                classResult = clipXRes,
                methodName = "onPrimaryClipChanged",
                returnType = "void",
                primary = true,
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    val isShown = cachedImeUiRefs?.isInputViewShown() ?: false
                    if (SogouPayloadSanitizer.shouldBlockClipboardBackgroundCapture(isShown, ruleEnabled = true)) {
                        registry.incrementBlocked(ruleId)
                        return@Hooker null
                    }
                    chain.proceed()
                }
            )

            val clipQRes = resolver.resolveClass(
                cacheKey = "sogou.clipboard.q",
                fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.q"),
                dexKitFinder = { dk -> findClassByStrings(dk, "ClipboardToCandsController onCandidateChange") }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "clipboard_q_m",
                classResult = clipQRes,
                methodName = "m",
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "clipboard_q_n",
                cacheKey = "sogou.clipboard.q#n",
                classResult = clipQRes,
                methodName = "n",
                returnType = "boolean",
                hooker = booleanFalseHooker(ruleId)
            )

            stats.finalizeRule(registry, ruleId, "ClipStrictBlockListener")
        }
    }

    // -------------------------------------------------------------------------
    // 6. sogou.scan.* (DeviceInfo Harvester, App List, Contacts & Dict, Location/Wi-Fi)
    // -------------------------------------------------------------------------

    private fun installDeviceInfoHarvesterRule(
        resolver: SymbolResolver,
        registry: HookRegistry,
        sharedDevInfoServiceRes: Lazy<ResolveResult<Class<*>>>,
        sharedDevInfoBRes: Lazy<ResolveResult<Class<*>>>
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_SCAN_DEVICEINFO_HARVESTER
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val devInfoServiceRes = sharedDevInfoServiceRes.value
            listOf("addContactsInfo", "addOtherInfo", "addSmsInfo", "initData", "startDetectEmulator").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "devinfo_svc_$mName",
                    classResult = devInfoServiceRes,
                    methodName = mName,
                    primary = mName == "addContactsInfo",
                    hooker = voidNoOpHooker(ruleId)
                )
            }
            listOf("getCompressDeviceInfoObj", "getDeviceInfoObj").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "devinfo_svc_$mName",
                    classResult = devInfoServiceRes,
                    methodName = mName,
                    hooker = emptyStringHooker(ruleId)
                )
            }

            val devInfoBRes = sharedDevInfoBRes.value
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "devinfo_b_m_contacts",
                cacheKey = "sogou.deviceinfo.b#m",
                classResult = devInfoBRes,
                methodName = "m",
                Context::class.java,
                returnType = "org.json.JSONArray",
                primary = true,
                hooker = emptyJsonArrayHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "devinfo_b_s_sms",
                cacheKey = "sogou.deviceinfo.b#s",
                classResult = devInfoBRes,
                methodName = "s",
                Context::class.java,
                returnType = "org.json.JSONArray",
                primary = true,
                hooker = emptyJsonArrayHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "devinfo_b_b",
                cacheKey = "sogou.deviceinfo.b#b",
                classResult = devInfoBRes,
                methodName = "b",
                Context::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "devinfo_b_c",
                cacheKey = "sogou.deviceinfo.b#c",
                classResult = devInfoBRes,
                methodName = "c",
                Activity::class.java,
                List::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "devinfo_b_d",
                cacheKey = "sogou.deviceinfo.b#d",
                classResult = devInfoBRes,
                methodName = "d",
                Context::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "devinfo_b_h",
                cacheKey = "sogou.deviceinfo.b#h",
                classResult = devInfoBRes,
                methodName = "h",
                Context::class.java,
                returnType = "org.json.JSONObject",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    JSONObject()
                }
            )

            val acCheckerRes = resolver.resolveClass(
                cacheKey = "sogou.lib.ac.ACCheckerInterface",
                fastPathCandidates = listOf("com.lib.ac.ACCheckerInterface"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.lib.ac")
                        matcher {
                            addMethod {
                                name("getType", StringMatchType.Equals)
                                modifiers(Modifier.NATIVE, MatchType.Contains)
                                returnType("int")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "ac_checker_getType",
                cacheKey = "sogou.lib.ac.ACCheckerInterface#getType",
                classResult = acCheckerRes,
                methodName = "getType",
                returnType = "int",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    0
                }
            )

            stats.finalizeRule(registry, ruleId, "DeviceInfoHarvester")
        }
    }

    private fun installAppListScanRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_SCAN_APP_LIST
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val utilURes = resolver.resolveClass(
                cacheKey = "sohu.util.u",
                fastPathCandidates = listOf("com.sohu.util.u"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sohu.util")
                            .matcher(
                                ClassMatcher.create().addMethod(
                                    MethodMatcher.create()
                                        .name("b")
                                        .paramTypes(
                                            "android.content.Context",
                                            "android.content.pm.PackageManager",
                                            "int"
                                        )
                                        .returnType("java.util.List")
                                )
                            )
                    ).singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "util_u_a",
                cacheKey = "sohu.util.u#a",
                classResult = utilURes,
                methodName = "a",
                Context::class.java,
                returnType = "java.lang.String",
                primary = true,
                hooker = emptyStringHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "util_u_b",
                cacheKey = "sohu.util.u#b",
                classResult = utilURes,
                methodName = "b",
                Context::class.java,
                PackageManager::class.java,
                Int::class.javaPrimitiveType!!,
                returnType = "java.util.List",
                primary = true,
                hooker = emptyListHooker(ruleId)
            )

            val appStatRes = resolver.resolveClass(
                cacheKey = "sogou.settings.internet.v",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.v"),
                dexKitFinder = { dk -> findClassByStrings(dk, "messagetmp.xml", "SoftwareStatistics") }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "software_stats_v_onWork",
                classResult = appStatRes,
                methodName = "onWork",
                hooker = voidNoOpHooker(ruleId)
            )

            stats.finalizeRule(registry, ruleId, "AppListScan")
        }
    }

    private fun installContactsUploadAndDictRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_SCAN_CONTACTS_UPLOAD_AND_DICT
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val contactsB0Res = resolver.resolveClass(
                cacheKey = "sogou.settings.b0",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.b0"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.settings")
                        matcher {
                            usingStrings(listOf("display_name"), StringMatchType.Contains)
                            addMethod {
                                name("h", StringMatchType.Equals)
                                paramCount(0)
                                returnType("void")
                                usingNumbers(1296000000L)
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("f", "h", "j").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "contacts_b0_$mName",
                    classResult = contactsB0Res,
                    methodName = mName,
                    primary = mName == "h",
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val contactsDictRes = resolver.resolveClass(
                cacheKey = "sogou.settings.ContactsDictionary",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.ContactsDictionary"),
                dexKitFinder = { dk -> findClassByStrings(dk, "mimetype='vnd.android.cursor.item/phone_v2'") }
            )
            listOf("o", "p", "r").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "contacts_dict_$mName",
                    classResult = contactsDictRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            stats.finalizeRule(registry, ruleId, "ContactsUploadDict")
        }
    }

    private fun installLocationWifiRule(
        resolver: SymbolResolver,
        registry: HookRegistry,
        sharedInfoMgrRes: Lazy<ResolveResult<Class<*>>>,
        sharedDevInfoServiceRes: Lazy<ResolveResult<Class<*>>>,
        sharedDevInfoBRes: Lazy<ResolveResult<Class<*>>>
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_SCAN_LOCATION_WIFI
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val locRecorderRes = resolver.resolveClass(
                cacheKey = "sogou.flx.FlxLocationRecorder",
                fastPathCandidates = listOf("com.sohu.inputmethod.flx.util.recorder.FlxLocationRecorder"),
                dexKitFinder = { dk -> findClassByStrings(dk, "cdma", "network", "location") }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "flx_loc_i",
                cacheKey = "sogou.flx.FlxLocationRecorder#i",
                classResult = locRecorderRes,
                methodName = "i",
                returnType = "boolean",
                primary = true,
                hooker = booleanFalseHooker(ruleId)
            )
            listOf("k", "m", "n").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "flx_loc_$mName",
                    classResult = locRecorderRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val wifiCtrlRes = resolver.resolveClass(
                cacheKey = "sogou.flx.WifiLocationController",
                fastPathCandidates = listOf("com.sohu.inputmethod.flx.controller.WifiLocationController"),
                dexKitFinder = { dk -> findClassByStrings(dk, "innerdata=", "neighbors") }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "wifi_loc_e",
                classResult = wifiCtrlRes,
                methodName = "e",
                primary = true,
                hooker = voidNoOpHooker(ruleId)
            )

            val infoMgrRes = sharedInfoMgrRes.value
            listOf("m", "f").forEach { mName ->
                hookMethodOnClass(
                    registry, resolver, stats, ruleId,
                    hookId = "infomgr_$mName",
                    cacheKey = "sogou.bu.InfoManager#$mName",
                    classResult = infoMgrRes,
                    methodName = mName,
                    returnType = "java.lang.String",
                    hooker = emptyStringHooker(ruleId)
                )
            }

            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "devinfo_svc_getLocation",
                cacheKey = "sogou.deviceinfo.DeviceInfoServiceImpl#getLocation",
                classResult = sharedDevInfoServiceRes.value,
                methodName = "getLocation",
                Activity::class.java,
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "devinfo_b_p",
                cacheKey = "sogou.deviceinfo.b#p",
                classResult = sharedDevInfoBRes.value,
                methodName = "p",
                Activity::class.java,
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )

            val lbsNotifyBRes = resolver.resolveClass(
                cacheKey = "sogou.settings.lbsnetnotify.b",
                fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.lbsnetnotify.b"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.settings.internet.lbsnetnotify")
                        matcher {
                            usingStrings(listOf("sogou.action.delete.lbs.notification"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "lbs_notify_b_onWork",
                classResult = lbsNotifyBRes,
                methodName = "onWork",
                hooker = voidNoOpHooker(ruleId)
            )

            val commonLibRes = resolver.resolveClass(
                cacheKey = "sogou.hotwords.CommonLib",
                fastPathCandidates = listOf("base.sogou.mobile.hotwordsbase.utils.CommonLib"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("base.sogou.mobile.hotwordsbase.utils")
                        matcher {
                            addMethod {
                                name("getCellLAC", StringMatchType.Equals)
                                paramTypes("android.content.Context")
                                returnType("java.lang.String")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "hotwords_commonlib_getCellLAC",
                cacheKey = "sogou.hotwords.CommonLib#getCellLAC",
                classResult = commonLibRes,
                methodName = "getCellLAC",
                Context::class.java,
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )

            stats.finalizeRule(registry, ruleId, "LocationWifi")
        }
    }

    // -------------------------------------------------------------------------
    // 7. sogou.net.dynamic_dex_and_js & sogou.net.httpdns
    // -------------------------------------------------------------------------

    private fun installDynamicDexAndJsRule(
        resolver: SymbolResolver,
        registry: HookRegistry,
        sharedFlxWebViewRes: Lazy<ResolveResult<Class<*>>>,
        sharedDeviceMRes: Lazy<ResolveResult<Class<*>>>,
        sharedHjRequestWorkRes: Lazy<ResolveResult<Class<*>>>
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_NET_DYNAMIC_DEX_AND_JS
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            // Core plugin loader (com.sogou.hj.*)
            val reqWorkRes = sharedHjRequestWorkRes.value
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "hj_requestwork_o",
                classResult = reqWorkRes,
                methodName = "o",
                primary = true,
                hooker = voidNoOpHooker(ruleId)
            )

            val taskExecARes = resolver.resolveClass(
                cacheKey = "sogou.hj.TaskExecutor_a",
                fastPathCandidates = listOf("com.sogou.hj.task.TaskExecutor\$a"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sogou.hj.task")
                            .matcher(
                                ClassMatcher.create()
                                    .addInterface("java.lang.Runnable")
                                    .addMethod(MethodMatcher.create().name("run"))
                            )
                    ).singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "hj_task_executor_run",
                cacheKey = "sogou.hj.TaskExecutor_a#run",
                classResult = taskExecARes,
                methodName = "run",
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val hjTaskARes = resolver.resolveClass(
                cacheKey = "sogou.hj.task.a",
                fastPathCandidates = listOf("com.sogou.hj.task.a"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sogou.hj.task")
                            .matcher(
                                ClassMatcher.create()
                                    .addMethod(
                                        MethodMatcher.create()
                                            .paramTypes(
                                                "java.lang.String",
                                                "java.lang.String",
                                                "java.lang.String"
                                            )
                                    )
                            )
                    ).singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "hj_task_a_a",
                cacheKey = "sogou.hj.task.a#a",
                classResult = hjTaskARes,
                methodName = "a",
                Any::class.java,
                String::class.java,
                returnType = "java.lang.Object",
                hooker = nullHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "hj_task_a_b",
                cacheKey = "sogou.hj.task.a#b",
                classResult = hjTaskARes,
                methodName = "b",
                String::class.java,
                String::class.java,
                String::class.java,
                returnType = "java.lang.Class",
                primary = true,
                hooker = nullHooker(ruleId)
            )

            // Missed Loader 1: com.sogou.loaddex.{c, d}
            val loadDexCRes = resolver.resolveClass(
                cacheKey = "sogou.loaddex.c",
                fastPathCandidates = listOf("com.sogou.loaddex.c"),
                dexKitFinder = { dk -> findClassByStrings(dk, "opt_dex") }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "loaddex_c_a",
                classResult = loadDexCRes,
                methodName = "a",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    -1
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "loaddex_c_b",
                classResult = loadDexCRes,
                methodName = "b",
                hooker = nullHooker(ruleId)
            )
            val loadDexDRes = resolver.resolveClass(
                cacheKey = "sogou.loaddex.d",
                fastPathCandidates = listOf("com.sogou.loaddex.d"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.loaddex")
                        matcher {
                            usingStrings(listOf(".dex"), StringMatchType.Contains)
                            addMethod { name("onWork", StringMatchType.Equals) }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "loaddex_d_onWork",
                classResult = loadDexDRes,
                methodName = "onWork",
                hooker = voidNoOpHooker(ruleId)
            )

            // Missed Loader 2: Vibrator dynamic DEX loader (vibrator.f#f)
            val vibratorFRes = resolver.resolveClass(
                cacheKey = "sogou.vibrator.f",
                fastPathCandidates = listOf("com.sogou.common_components.vibratesound.vibrator.f"),
                dexKitFinder = { dk ->
                    findClassByStrings(dk, "com.sogou.common_components.vibratesound.vibrator.VibratorFactory")
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "vibrator_f_f",
                cacheKey = "sogou.vibrator.f#f",
                classResult = vibratorFRes,
                methodName = "f",
                Context::class.java,
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            // Missed Loader 3: BigData remote libsqlcipher.zip SO downloader (bigdata.so.a#w)
            val bigDataSoARes = resolver.resolveClass(
                cacheKey = "sogou.bigdata.so.a",
                fastPathCandidates = listOf("com.sohu.inputmethod.sogou.bigdata.so.a"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.sogou.bigdata.so")
                        matcher {
                            usingStrings(listOf("/sqlcipher/", "/temp/", "libsqlcipher.so"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "bigdata_so_a_w",
                cacheKey = "sogou.bigdata.so.a#w",
                classResult = bigDataSoARes,
                methodName = "w",
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            // Missed Loader 4: RFix HotFix initializer (com.sogou.base.hotfix.b#i)
            val hotfixBRes = resolver.resolveClass(
                cacheKey = "sogou.base.hotfix.b",
                fastPathCandidates = listOf("com.sogou.base.hotfix.b"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.base.hotfix")
                        matcher {
                            usingStrings(listOf("HotFixManager", "rfix_patch_"), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookResolvedMethod(
                registry, resolver, stats, ruleId,
                hookId = "hotfix_b_i",
                cacheKey = "sogou.base.hotfix.b#i",
                fastPath = {
                    safeDeclaredMethods(hotfixBRes.value).firstOrNull { method ->
                        method.name == "i" &&
                            method.parameterCount == 2 &&
                            method.parameterTypes[1].name == "com.sogou.base.hotfix.f"
                    }
                },
                dexKitFinder = { dk ->
                    val cls = hotfixBRes.value ?: return@hookResolvedMethod null
                    dk.findMethod {
                        searchPackages("com.sogou.base.hotfix")
                        matcher {
                            declaredClass(cls.name, StringMatchType.Equals)
                            name("i", StringMatchType.Equals)
                            paramTypes("com.tencent.rfix.entry.RFixApplicationLike", "com.sogou.base.hotfix.f")
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                },
                hooker = voidNoOpHooker(ruleId)
            )

            // Missed Loader 5: HotFix DEX patcher (com.sogou.hotfix.patcher.InstallDex#c)
            val installDexRes = resolver.resolveClass(
                cacheKey = "sogou.hotfix.InstallDex",
                fastPathCandidates = listOf("com.sogou.hotfix.patcher.InstallDex"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.hotfix.patcher")
                        matcher {
                            addMethod {
                                name("c", StringMatchType.Equals)
                                paramCount(5)
                                returnType("void")
                            }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "hotfix_install_dex_c",
                cacheKey = "sogou.hotfix.InstallDex#c",
                classResult = installDexRes,
                methodName = "c",
                Application::class.java,
                ClassLoader::class.java,
                File::class.java,
                List::class.java,
                Boolean::class.javaPrimitiveType!!,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            // Missed Loader 6: DebugDex + WebView JS evaluation bridges
            val debugBRes = resolver.resolveClass(
                cacheKey = "sogou.debug.b",
                fastPathCandidates = listOf("com.sogou.debug.b"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.debug")
                        matcher {
                            usingStrings(listOf("DebugDex", "loadDexImpl: clazzName="), StringMatchType.Contains)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            listOf("p", "s", "w").forEach { mName ->
                hookClassMethodsByName(
                    registry, stats, ruleId,
                    hookIdPrefix = "debug_b_$mName",
                    classResult = debugBRes,
                    methodName = mName,
                    hooker = voidNoOpHooker(ruleId)
                )
            }

            val flxWebViewRes = sharedFlxWebViewRes.value
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "flx_webview_loadUrlByJs",
                cacheKey = "sogou.flx.FlxImeWebView#loadUrlByJs",
                classResult = flxWebViewRes,
                methodName = "loadUrlByJs",
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val deviceMRes = sharedDeviceMRes.value
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "device_m_webview_js",
                cacheKey = "sogou.lib.device.m#l",
                classResult = deviceMRes,
                methodName = "l",
                WebView::class.java,
                String::class.java,
                returnType = "void",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    val scriptOrUrl = chain.getArg(1) as? String
                    if (scriptOrUrl != null && scriptOrUrl.trimStart().startsWith("javascript:", ignoreCase = true)) {
                        registry.incrementBlocked(ruleId)
                        return@Hooker null
                    }
                    chain.proceed()
                }
            )

            stats.finalizeRule(registry, ruleId, "DynamicDexJs")
        }
    }

    private fun installHttpDnsRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS
        registry.installRuleIfEnabled(ruleId) {
            val stats = RuleStats()

            val msdkRes = resolver.resolveClass(
                cacheKey = "tencent.msdk.MSDKDnsResolver",
                fastPathCandidates = listOf("com.tencent.msdk.dns.MSDKDnsResolver"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.tencent.msdk")
                            .matcher(
                                ClassMatcher.create().usingStrings(
                                    listOf("MSDKDnsResolver.getAddrByName() called."),
                                    StringMatchType.Contains
                                )
                            )
                    ).singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "msdk_dns_init",
                classResult = msdkRes,
                methodName = "init",
                hooker = voidNoOpHooker(ruleId)
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "msdk_dns_setOpenId",
                classResult = msdkRes,
                methodName = "WGSetDnsOpenId",
                primary = true,
                hooker = booleanFalseHooker(ruleId)
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "msdk_dns_getAddrByName",
                classResult = msdkRes,
                methodName = "getAddrByName",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    "0;0"
                }
            )
            val ipSetRetType = safeDeclaredMethods(msdkRes.value)
                .firstOrNull { it.name == "getAddrsByName" }
                ?.returnType
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "msdk_dns_getAddrsByName",
                classResult = msdkRes,
                methodName = "getAddrsByName",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    getEmptyIpSetInstance(ipSetRetType)
                }
            )

            val sogouDnsBRes = resolver.resolveClass(
                cacheKey = "sogou.http.monitor.dns.b",
                fastPathCandidates = listOf("com.sogou.http.monitor.dns.b"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sogou.http.monitor.dns")
                        matcher {
                            usingStrings(
                                listOf("get.sogou.com", "v2.get.sogou.com", "sec.sginput.qq.com"),
                                StringMatchType.Contains
                            )
                            addMethod { name("c", StringMatchType.Equals) }
                            addMethod { name("l", StringMatchType.Equals) }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "sogou_httpdns_b_c",
                classResult = sogouDnsBRes,
                methodName = "c",
                primary = true,
                hooker = emptyListHooker(ruleId)
            )
            hookClassMethodsByName(
                registry, stats, ruleId,
                hookIdPrefix = "sogou_httpdns_b_l",
                classResult = sogouDnsBRes,
                methodName = "l",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    chain.args.getOrNull(1) ?: emptyList<Any>()
                }
            )

            stats.finalizeRule(registry, ruleId, "HttpDns")
        }
    }

    private fun installNativeEngineStrictRule(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_NET_NATIVE_ENGINE_STRICT
        registry.installRuleIfEnabled(ruleId, disabledReason = "默认关闭，未安装 Hook") {
            val stats = RuleStats()

            val imeNativeRes = resolver.resolveClass(
                cacheKey = "sogou.engine.IMENativeInterface",
                fastPathCandidates = listOf("com.sohu.inputmethod.engine.IMENativeInterface"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("com.sohu.inputmethod.engine")
                        matcher {
                            addField { name("mNativeContext", StringMatchType.Equals) }
                            addMethod { name("getCloudPingbackInfo", StringMatchType.Equals) }
                            addMethod { name("getCloudWhiteDogInfo", StringMatchType.Equals) }
                            addMethod { name("pingbackAction", StringMatchType.Equals) }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_engine#getCloudPingbackInfo",
                cacheKey = "sogou.engine.IMENativeInterface#getCloudPingbackInfo",
                classResult = imeNativeRes,
                methodName = "getCloudPingbackInfo",
                returnType = "java.lang.String",
                primary = true,
                hooker = emptyStringHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_engine#getCorrectPingbackInfo",
                cacheKey = "sogou.engine.IMENativeInterface#getCorrectPingbackInfo",
                classResult = imeNativeRes,
                methodName = "getCorrectPingbackInfo",
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_engine#getIndividualPingback",
                cacheKey = "sogou.engine.IMENativeInterface#getIndividualPingback",
                classResult = imeNativeRes,
                methodName = "getIndividualPingback",
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_engine#getInputPingbackInfo",
                cacheKey = "sogou.engine.IMENativeInterface#getInputPingbackInfo",
                classResult = imeNativeRes,
                methodName = "getInputPingbackInfo",
                returnType = "java.lang.String",
                hooker = emptyStringHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_engine#getCloudWhiteDogInfo",
                cacheKey = "sogou.engine.IMENativeInterface#getCloudWhiteDogInfo",
                classResult = imeNativeRes,
                methodName = "getCloudWhiteDogInfo",
                Int::class.javaPrimitiveType!!,
                CharArray::class.java,
                returnType = "int",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    0
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_engine#checkUploadUsrDict",
                cacheKey = "sogou.engine.IMENativeInterface#checkUploadUsrDict",
                classResult = imeNativeRes,
                methodName = "checkUploadUsrDict",
                ByteArray::class.java,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                ByteArray::class.java,
                Int::class.javaPrimitiveType!!,
                returnType = "int",
                hooker = XposedInterface.Hooker { chain ->
                    if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                    registry.incrementBlocked(ruleId)
                    0
                }
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_engine#pingbackAction",
                cacheKey = "sogou.engine.IMENativeInterface#pingbackAction",
                classResult = imeNativeRes,
                methodName = "pingbackAction",
                Long::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                String::class.java,
                String::class.java,
                CharArray::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                ShortArray::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_engine#pingbackCloudAction",
                cacheKey = "sogou.engine.IMENativeInterface#pingbackCloudAction",
                classResult = imeNativeRes,
                methodName = "pingbackCloudAction",
                Long::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                IntArray::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_engine#setClientPackageName",
                cacheKey = "sogou.engine.IMENativeInterface#setClientPackageName",
                classResult = imeNativeRes,
                methodName = "setClientPackageName",
                String::class.java,
                returnType = "void",
                hooker = voidNoOpHooker(ruleId)
            )

            val secInterfaceRes = resolver.resolveClass(
                cacheKey = "sogou.security.SecurityInterface",
                fastPathCandidates = listOf("sogouime.security.SecurityInterface"),
                dexKitFinder = { dk ->
                    dk.findClass {
                        searchPackages("sogouime.security")
                        matcher {
                            usingStrings(listOf("native-sec"), StringMatchType.Equals)
                            addMethod { name("postguard", StringMatchType.Equals) }
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val emptyByteArrayHooker = XposedInterface.Hooker { chain ->
                if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
                registry.incrementBlocked(ruleId)
                ByteArray(0)
            }
            for (methodName in listOf("postguard", "proguard", "getRule")) {
                hookMethodOnClass(
                    registry, resolver, stats, ruleId,
                    hookId = "sogou_native_sec#$methodName",
                    cacheKey = "sogou.security.SecurityInterface#$methodName",
                    classResult = secInterfaceRes,
                    methodName = methodName,
                    ByteArray::class.java,
                    returnType = "byte[]",
                    hooker = emptyByteArrayHooker
                )
            }
            hookMethodOnClass(
                registry, resolver, stats, ruleId,
                hookId = "sogou_native_sec#applyRule",
                cacheKey = "sogou.security.SecurityInterface#applyRule",
                classResult = secInterfaceRes,
                methodName = "applyRule",
                ByteArray::class.java,
                ByteArray::class.java,
                returnType = "byte[]",
                hooker = emptyByteArrayHooker
            )

            stats.finalizeRule(registry, ruleId, "NativeEngineStrict")
        }
    }

    // -------------------------------------------------------------------------
    // 8. sogou.ui.dual_skin_emoji
    // -------------------------------------------------------------------------

    private fun installDualSkinEmojiRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_UI_DUAL_SKIN_EMOJI
        registry.installRuleIfEnabled(ruleId) {
            if (registry.processName != target.packageName) {
                registry.reportSkipped(ruleId, "非主输入法进程 (${registry.processName})")
                return@installRuleIfEnabled
            }
            SogouDualSkinPopupHook.install(registry, classLoader, resolver, ruleId)
        }
    }

    // -------------------------------------------------------------------------
    // Live Config Listener & CloudMgr Stop Helper
    // -------------------------------------------------------------------------

    private fun registerLiveCloudConfigListener(registry: HookRegistry) {
        activeRegistryRef = registry
        registeredCloudListener.getAndSet(null)?.let { old ->
            ConfigBridge.removeListener(old)
        }
        if (registry.processName != target.packageName) return

        val listener: (PrivacyConfig, PrivacyConfig) -> Unit = { oldCfg, newCfg ->
            val cloudChanged = oldCfg.effectiveCloudEnabled(target) != newCfg.effectiveCloudEnabled(target)
            val overrideChanged = oldCfg.cloudOverrideFor(target) != newCfg.cloudOverrideFor(target)

            if (newCfg.isImeManaged(target) && (cloudChanged || overrideChanged)) {
                val refs = cachedCloudMgrRefs
                val instance = runCatching { refs?.fieldZ?.get(null) }.getOrNull()
                if (refs != null && instance != null && refs.methodO != null) {
                    mainHandler.post {
                        runCatching { refs.methodO.invoke(instance) }
                    }
                }
            }
        }
        registeredCloudListener.set(listener)
        ConfigBridge.addListener(listener)
    }

    internal fun stopCloudMgrLocked(cloudMgrObj: Any?, refs: CloudMgrRefs) {
        if (cloudMgrObj == null) return
        runCatching {
            if (refs.methodH1 != null) {
                refs.methodH1.invoke(cloudMgrObj)
            } else {
                refs.methodV0?.invoke(cloudMgrObj)
                refs.methodV?.invoke(cloudMgrObj)
                refs.fieldJ2C0?.setBoolean(null, false)
            }
            refs.fieldX?.setBoolean(cloudMgrObj, false)
        }
    }

    // -------------------------------------------------------------------------
    // Shared Hook Installation, Reflection & DexKit Helpers
    // -------------------------------------------------------------------------

    private fun hookMethodOnClass(
        registry: HookRegistry,
        resolver: SymbolResolver,
        stats: RuleStats,
        ruleId: String,
        hookId: String,
        cacheKey: String,
        classResult: ResolveResult<Class<*>>,
        methodName: String,
        vararg paramTypes: Class<*>,
        returnType: String? = null,
        primary: Boolean = false,
        hooker: XposedInterface.Hooker
    ) {
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = hookId,
            cacheKey = cacheKey,
            fastPath = { SymbolResolver.findDeclaredMethodOrNull(classResult.value, methodName, *paramTypes) },
            dexKitFinder = { dk ->
                val cls = classResult.value ?: return@hookResolvedMethod null
                val expectedParamNames = paramTypes.map { it.dexKitTypeName() }.toTypedArray()
                dk.findMethod {
                    searchPackages(cls.name.substringBeforeLast('.', ""))
                    matcher {
                        declaredClass(cls.name, StringMatchType.Equals)
                        name(methodName, StringMatchType.Equals)
                        if (returnType != null) returnType(returnType)
                        if (expectedParamNames.isEmpty()) paramCount(0) else paramTypes(*expectedParamNames)
                    }
                }.singleOrNull()?.descriptor
            },
            primary = primary,
            hooker = hooker
        )
    }

    private fun Class<*>.dexKitTypeName(): String =
        if (isArray) "${componentType!!.dexKitTypeName()}[]" else name

    private fun hookResolvedMethod(
        registry: HookRegistry,
        resolver: SymbolResolver,
        stats: RuleStats,
        ruleId: String,
        hookId: String,
        cacheKey: String,
        fastPath: (ClassLoader) -> Method?,
        dexKitFinder: ((DexKitExecutor) -> String?)? = null,
        structuralFallback: ((ClassLoader) -> Method?)? = null,
        primary: Boolean = false,
        hooker: XposedInterface.Hooker
    ) {
        val resolved = resolver.resolveMethod(
            cacheKey = cacheKey,
            fastPath = fastPath,
            dexKitFinder = dexKitFinder,
            structuralFallback = structuralFallback
        )
        val hooked = registry.hookExecutable(
            ruleId = ruleId,
            hookId = hookId,
            resolveResult = resolved,
            priority = XposedInterface.PRIORITY_DEFAULT,
            onFailureStatus = if (primary) HookMatchStatus.FAILED else HookMatchStatus.DEGRADED,
            hooker = hooker
        )
        stats.record(registry, ruleId, hookId, resolved, hooked, primary)
    }

    private fun hookClassMethodsByName(
        registry: HookRegistry,
        stats: RuleStats,
        ruleId: String,
        hookIdPrefix: String,
        classResult: ResolveResult<Class<*>>,
        methodName: String,
        primary: Boolean = false,
        hooker: XposedInterface.Hooker
    ) {
        val count = registry.hookAllMethods(
            ruleId = ruleId,
            hookIdPrefix = hookIdPrefix,
            clazz = classResult.value,
            methodName = methodName,
            source = classResult.source.name,
            priority = XposedInterface.PRIORITY_DEFAULT,
            hooker = hooker
        )
        stats.recordCount(registry, ruleId, hookIdPrefix, classResult.source, count, primary)
    }

    private fun registryOrNullIncrement(ruleId: String) {
        activeRegistryRef?.incrementBlocked(ruleId)
    }

    private fun voidNoOpHooker(ruleId: String): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
        registryOrNullIncrement(ruleId)
        null
    }

    private fun nullHooker(ruleId: String): XposedInterface.Hooker = voidNoOpHooker(ruleId)

    private fun booleanFalseHooker(ruleId: String): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
        registryOrNullIncrement(ruleId)
        false
    }

    private fun emptyStringHooker(ruleId: String): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
        registryOrNullIncrement(ruleId)
        ""
    }

    private fun emptyListHooker(ruleId: String): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
        registryOrNullIncrement(ruleId)
        emptyList<Any>()
    }

    private fun emptyJsonArrayHooker(ruleId: String): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
        registryOrNullIncrement(ruleId)
        JSONArray()
    }

    private fun selfReturningHooker(ruleId: String): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!isPointEnabled(ruleId)) return@Hooker chain.proceed()
        registryOrNullIncrement(ruleId)
        chain.getThisObject()
    }

    private fun createBlockedBeaconEventResult(returnType: Class<*>?): Any? {
        if (returnType == null) return null
        return runCatching {
            val ctor = returnType.getConstructor(
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                String::class.java
            )
            ctor.newInstance(0, -1L, "blocked")
        }.getOrNull()
    }

    private fun getEmptyIpSetInstance(returnType: Class<*>?): Any? {
        if (returnType == null) return null
        return runCatching {
            findField(returnType, "EMPTY")?.get(null)
        }.getOrNull()
    }

    private fun findClassByStrings(dk: DexKitExecutor, vararg strings: String): String? {
        return dk.findClass(
            FindClass.create()
                .searchPackages(*SEARCH_PACKAGES)
                .matcher(ClassMatcher.create().usingStrings(strings.toList(), StringMatchType.Contains))
        ).singleOrNull()?.descriptor
    }

    private fun findMethodBy(
        dk: DexKitExecutor,
        searchPackages: Array<String> = SEARCH_PACKAGES,
        returnType: String? = null,
        paramCount: Int? = null,
        paramTypes: Array<String>? = null,
        strings: Array<String>
    ): String? {
        val matcher = MethodMatcher.create()
        if (strings.isNotEmpty()) matcher.usingStrings(strings.toList(), StringMatchType.Contains)
        if (returnType != null) matcher.returnType(returnType)
        if (paramCount != null) matcher.paramCount(paramCount)
        if (paramTypes != null) matcher.paramTypes(*paramTypes)
        return dk.findMethod(
            FindMethod.create()
                .searchPackages(*searchPackages)
                .matcher(matcher)
        ).singleOrNull()?.descriptor
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var cursor: Class<*>? = clazz
        while (cursor != null && cursor != Any::class.java) {
            val field = runCatching { cursor.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return field
            }
            cursor = cursor.superclass
        }
        return null
    }

    private fun safeDeclaredMethods(clazz: Class<*>?): Array<Method> =
        if (clazz == null) emptyArray() else runCatching { clazz.declaredMethods }.getOrDefault(emptyArray())

    private fun findMethodInHierarchy(clazz: Class<*>, name: String, vararg params: Class<*>): Method? {
        var cursor: Class<*>? = clazz
        while (cursor != null && cursor != Any::class.java) {
            val method = runCatching { cursor.getDeclaredMethod(name, *params) }.getOrNull()
            if (method != null) {
                method.isAccessible = true
                return method
            }
            cursor = cursor.superclass
        }
        return null
    }

    private fun extractOkHttpUrlOrToString(requestObj: Any?): String? {
        if (requestObj == null) return null
        val asText = runCatching { requestObj.toString() }.getOrNull()
        if (!asText.isNullOrBlank() && (asText.contains("http://") || asText.contains("https://"))) {
            return asText
        }
        return runCatching {
            for (field in requestObj.javaClass.declaredFields) {
                if (field.type.isPrimitive) continue
                field.isAccessible = true
                val value = field.get(requestObj) ?: continue
                val str = value.toString()
                if (str.startsWith("http://") || str.startsWith("https://")) {
                    return@runCatching str
                }
            }
            asText
        }.getOrNull()
    }
}
