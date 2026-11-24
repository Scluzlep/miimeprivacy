package com.scluzlep.miimeprivacy.xposed.targets

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ImeHookPack
import com.scluzlep.miimeprivacy.xposed.ImePackInstallResult
import com.scluzlep.miimeprivacy.xposed.ResolveResult
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

/**
 * Xposed hook pack for Sogou Xiaomi IME (`com.sohu.inputmethod.sogou.xiaomi`, versionCode 20160).
 *
 * Implements all 8 non-overlay privacy rules in [HookRuleCatalog.rulesFor]:
 * 1. [HookRuleCatalog.RULE_SOGOU_PRIVACY_GATE] (`critical = true`)
 * 2. [HookRuleCatalog.RULE_SOGOU_CLOUD_SANITIZE] (`critical = true`)
 * 3. [HookRuleCatalog.RULE_SOGOU_TELEMETRY]
 * 4. [HookRuleCatalog.RULE_SOGOU_MUTUAL_DATA]
 * 5. [HookRuleCatalog.RULE_SOGOU_CLIPBOARD]
 * 6. [HookRuleCatalog.RULE_SOGOU_CONTACTS_APP_SCAN]
 * 7. [HookRuleCatalog.RULE_SOGOU_DYNAMIC_DEX]
 * 8. [HookRuleCatalog.RULE_SOGOU_HTTPDNS] (default OFF)
 */
object SogouHookPack : ImeHookPack {

    override val target: ImeTarget = ImeTarget.SOGOU

    private val SEARCH_PACKAGES = arrayOf("com.sohu", "com.sogou", "sogou")

    private val registeredCloudListener = AtomicReference<((PrivacyConfig, PrivacyConfig) -> Unit)?>(null)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var cachedCloudMgrRefs: CloudMgrRefs? = null

    @Volatile
    private var cachedImeUiRefs: ImeUiRefs? = null

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
                if (source != ResolveSource.FAST_PATH) {
                    usedFallback = true
                }
                val sig = matchedMethod?.takeIf { it.isNotBlank() }
                val srcName = reportedSource?.takeIf { it.isNotBlank() } ?: source.name
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
            val source = primarySource?.takeIf { it.isNotBlank() }
                ?: lastSource?.takeIf { it.isNotBlank() }
                ?: existing?.source.orEmpty()
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

    override fun install(
        context: Context?,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ): ImePackInstallResult {
        activeRegistryRef = registry
        cachedImeUiRefs = ImeUiRefs(classLoader)

        registry.createSymbolResolver(classLoader).use { resolver ->
            installPrivacyGateRule(classLoader, resolver, registry)
            installCloudSanitizeRule(classLoader, resolver, registry)
            installTelemetryRule(classLoader, resolver, registry)
            installMutualDataRule(classLoader, resolver, registry)
            installClipboardRule(classLoader, resolver, registry)
            installContactsAndAppScanRule(classLoader, resolver, registry)
            installDynamicDexRule(classLoader, resolver, registry)
            installHttpDnsRule(classLoader, resolver, registry)
        }

        val rules = HookRuleCatalog.rulesFor(target)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_SOGOU_OVERLAY_PILL }
        return ImePackInstallResult(
            target = target,
            reports = rules.mapNotNull { registry.getReport(it.ruleId) },
            note = "Sogou Xiaomi v20160 privacy hooks active (${registry.processName})"
        )
    }

    // -------------------------------------------------------------------------
    // 1. Privacy Gate & Alive Consent Bypass
    // -------------------------------------------------------------------------

    private fun installPrivacyGateRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_PRIVACY_GATE
        val gate: (PrivacyConfig) -> Boolean = { it.blockTelemetry }
        val stats = RuleStats()

        // com.sogou.alive.a: c():void, d(String):boolean, e(okhttp3.z):boolean
        val aliveClass = resolver.resolveClass(
            cacheKey = "sogou.alive.a",
            fastPathCandidates = listOf("com.sogou.alive.a"),
            dexKitFinder = { dk ->
                dk.findClass(
                    FindClass.create()
                        .searchPackages("com.sogou.alive")
                        .matcher(
                            ClassMatcher.create()
                                .addMethod(MethodMatcher.create().returnType("boolean").paramTypes("java.lang.String"))
                                .addMethod(MethodMatcher.create().returnType("boolean").paramTypes("okhttp3.z"))
                        )
                ).firstOrNull()?.descriptor
            }
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "alive_a_c",
            cacheKey = "sogou.alive.a#c",
            fastPath = { cl ->
                val cls = aliveClass.value ?: SymbolResolver.loadClassOrNull("com.sogou.alive.a", cl)
                SymbolResolver.findDeclaredMethodOrNull(cls, "c")
            },
            primary = true,
            hooker = voidNoOpHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "alive_a_d",
            cacheKey = "sogou.alive.a#d",
            fastPath = { cl ->
                val cls = aliveClass.value ?: SymbolResolver.loadClassOrNull("com.sogou.alive.a", cl)
                SymbolResolver.findDeclaredMethodOrNull(cls, "d", String::class.java)
            },
            primary = true,
            hooker = booleanFalseHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "alive_a_e",
            cacheKey = "sogou.alive.a#e",
            fastPath = { cl ->
                val cls = aliveClass.value ?: SymbolResolver.loadClassOrNull("com.sogou.alive.a", cl)
                val reqCls = SymbolResolver.loadClassOrNull("okhttp3.z", cl)
                if (cls != null && reqCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(cls, "e", reqCls)
                } else {
                    null
                }
            },
            structuralFallback = { cl ->
                val cls = aliveClass.value ?: SymbolResolver.loadClassOrNull("com.sogou.alive.a", cl)
                cls?.declaredMethods?.firstOrNull { method ->
                    method.returnType == Boolean::class.javaPrimitiveType &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] != String::class.java
                }
            },
            primary = true,
            hooker = booleanFalseHooker(ruleId, gate)
        )

        // com.sogou.alive.strategy.a#a():boolean (special_alive) & strategy.b#h():void
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "alive_strategy_a",
            cacheKey = "sogou.alive.strategy.a#a",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull("com.sogou.alive.strategy.a", cl),
                    "a"
                )
            },
            dexKitFinder = { dk ->
                findMethodBy(
                    dk = dk,
                    returnType = "boolean",
                    paramCount = 0,
                    strings = arrayOf("special_alive")
                )
            },
            primary = true,
            hooker = booleanFalseHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "alive_strategy_b_h",
            cacheKey = "sogou.alive.strategy.b#h",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull("com.sogou.alive.strategy.b", cl),
                    "h"
                )
            },
            hooker = voidNoOpHooker(ruleId, gate)
        )

        // InstallAliveResolver (com.sohu.inputmethod.settings.internet.alive.e): a(), b()
        val aliveInstallerRes = resolver.resolveClass(
            cacheKey = "sogou.alive.install.e",
            fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.alive.e"),
            dexKitFinder = { dk -> findClassByStrings(dk, "/v1/alive/install") }
        )
        listOf("a", "b").forEach { mName ->
            hookResolvedMethod(
                registry = registry,
                resolver = resolver,
                stats = stats,
                ruleId = ruleId,
                hookId = "alive_install_$mName",
                cacheKey = "sogou.alive.install.e#$mName",
                fastPath = { cl ->
                    val cls = aliveInstallerRes.value
                        ?: SymbolResolver.loadClassOrNull("com.sohu.inputmethod.settings.internet.alive.e", cl)
                    SymbolResolver.findDeclaredMethodOrNull(cls, mName)
                },
                primary = true,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }

        // com.sohu.inputmethod.sogou.privacy.a#a(okhttp3.z):boolean — close enforce_send bypass on alive URLs ONLY
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "privacy_enforce_send_guard",
            cacheKey = "sogou.privacy.a#a",
            fastPath = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.privacy.a", cl)
                val reqCls = SymbolResolver.loadClassOrNull("okhttp3.z", cl)
                if (cls != null && reqCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(cls, "a", reqCls)
                } else {
                    null
                }
            },
            structuralFallback = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.privacy.a", cl)
                cls?.declaredMethods?.firstOrNull { method ->
                    method.returnType == Boolean::class.javaPrimitiveType &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] != String::class.java
                }
            },
            primary = true,
            hooker = XposedInterface.Hooker { chain ->
                val allowed = chain.proceed() as? Boolean ?: false
                if (!allowed || !ConfigBridge.isRuleEnabled(target, gate)) {
                    return@Hooker allowed
                }
                val requestObj = chain.getArg(0)
                val url = extractOkHttpUrlOrToString(requestObj)
                if (SogouPayloadSanitizer.shouldBlockAliveEnforceSendUrl(url, ConfigBridge.current)) {
                    registry.incrementBlocked(ruleId)
                    return@Hooker false
                }
                allowed
            }
        )

        // Pre-consent alive S-COOKIE header bypass (com.sohu.inputmethod.sogou.launch.d$d)
        val preConsentProviderRes = resolver.resolveClass(
            cacheKey = "sogou.launch.d_d",
            fastPathCandidates = listOf("com.sohu.inputmethod.sogou.launch.d\$d"),
            dexKitFinder = { dk ->
                dk.findClass(
                    FindClass.create()
                        .searchPackages("com.sohu.inputmethod.sogou.launch")
                        .matcher(ClassMatcher.create().addInterface("com.sogou.networking.reporter.okhttp.a"))
                ).firstOrNull()?.descriptor
            },
            structuralFallback = { cl ->
                val outer = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.launch.d", cl)
                val iface = SymbolResolver.loadClassOrNull("com.sogou.networking.reporter.okhttp.a", cl)
                outer?.declaredClasses?.firstOrNull { inner ->
                    iface != null && iface.isAssignableFrom(inner)
                }
            }
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "preconsent_cookie_map",
            cacheKey = "sogou.launch.d_d#b",
            fastPath = { cl ->
                val cls = preConsentProviderRes.value
                    ?: SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.launch.d\$d", cl)
                SymbolResolver.findDeclaredMethodOrNull(cls, "b")
            },
            hooker = XposedInterface.Hooker { chain ->
                if (!ConfigBridge.isRuleEnabled(target, gate)) return@Hooker chain.proceed()
                registry.incrementBlocked(ruleId)
                // reporter/okhttp/b.java:33 mutates map via remove("S-COOKIE") -> MUST be mutable HashMap
                HashMap<String, String>()
            }
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "preconsent_cookie_str",
            cacheKey = "sogou.launch.d_d#c",
            fastPath = { cl ->
                val cls = preConsentProviderRes.value
                    ?: SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.launch.d\$d", cl)
                SymbolResolver.findDeclaredMethodOrNull(cls, "c")
            },
            hooker = emptyStringHooker(ruleId, gate)
        )

        // com.sogou.networking.reporter.a#j(Context, f, d):void
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "networking_reporter_j",
            cacheKey = "sogou.networking.reporter.a#j",
            fastPath = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sogou.networking.reporter.a", cl)
                val fCls = SymbolResolver.loadClassOrNull("com.sogou.networking.f", cl)
                val dCls = SymbolResolver.loadClassOrNull("com.sogou.networking.d", cl)
                if (cls != null && fCls != null && dCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(cls, "j", Context::class.java, fCls, dCls)
                } else {
                    null
                }
            },
            structuralFallback = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sogou.networking.reporter.a", cl)
                cls?.declaredMethods?.firstOrNull { method ->
                    method.name == "j" &&
                        method.parameterCount == 3 &&
                        method.parameterTypes[0] == Context::class.java
                }
            },
            hooker = voidNoOpHooker(ruleId, gate)
        )

        stats.finalizeRule(registry, ruleId, "PrivacyGate")
    }

    // -------------------------------------------------------------------------
    // 2. Cloud Input Master Switch + Outbound Payload Sanitization
    // -------------------------------------------------------------------------

    private fun installCloudSanitizeRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CLOUD_SANITIZE
        val stats = RuleStats()

        // CloudSettings master switch
        val cloudSettingsCls = resolver.resolveClass(
            cacheKey = "sogou.cloud.CloudSettings",
            fastPathCandidates = listOf("com.sohu.inputmethod.cloud.CloudSettings")
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "cloud_settings_on",
            cacheKey = "sogou.CloudSettings#isCloudSinkOn",
            fastPath = { cl ->
                val cls = cloudSettingsCls.value
                    ?: SymbolResolver.loadClassOrNull("com.sohu.inputmethod.cloud.CloudSettings", cl)
                SymbolResolver.findDeclaredMethodOrNull(cls, "isCloudSinkOn", Context::class.java)
            },
            primary = true,
            hooker = XposedInterface.Hooker { chain ->
                if (!ConfigBridge.isTargetManaged(target)) return@Hooker chain.proceed()
                ConfigBridge.isCloudEnabled(target)
            }
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "cloud_settings_send_down",
            cacheKey = "sogou.CloudSettings#isCloudSinkAndSendWhenDown",
            fastPath = { cl ->
                val cls = cloudSettingsCls.value
                    ?: SymbolResolver.loadClassOrNull("com.sohu.inputmethod.cloud.CloudSettings", cl)
                SymbolResolver.findDeclaredMethodOrNull(cls, "isCloudSinkAndSendWhenDown", Context::class.java)
            },
            hooker = XposedInterface.Hooker { chain ->
                if (ConfigBridge.isTargetManaged(target) && !ConfigBridge.isCloudEnabled(target)) {
                    return@Hooker false
                }
                chain.proceed()
            }
        )

        // CloudMgr controls + live config sync
        val cloudMgrRes = resolver.resolveClass(
            cacheKey = "sogou.cloud.CloudMgr",
            fastPathCandidates = listOf("com.sogou.inputmethod.cloud.api.ctrl.CloudMgr")
        )
        val cloudMgrCls = cloudMgrRes.value
        if (cloudMgrCls != null) {
            val j2Class = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.settings.j2", classLoader)
            cachedCloudMgrRefs = CloudMgrRefs(cloudMgrCls, j2Class)
            val refs = cachedCloudMgrRefs!!

            listOf(
                Triple("cloudmgr_O0", "O0", arrayOf<Class<*>>(Boolean::class.javaPrimitiveType!!)),
                Triple("cloudmgr_m", "m", arrayOf<Class<*>>(Boolean::class.javaPrimitiveType!!)),
                Triple("cloudmgr_P0", "P0", emptyArray())
            ).forEach { (hookId, mName, params) ->
                hookResolvedMethod(
                    registry = registry,
                    resolver = resolver,
                    stats = stats,
                    ruleId = ruleId,
                    hookId = hookId,
                    cacheKey = "sogou.CloudMgr#$mName",
                    fastPath = { SymbolResolver.findDeclaredMethodOrNull(cloudMgrCls, mName, *params) },
                    hooker = voidNoOpHooker(ruleId) { !it.effectiveCloudEnabled(target) }
                )
            }

            hookResolvedMethod(
                registry = registry,
                resolver = resolver,
                stats = stats,
                ruleId = ruleId,
                hookId = "cloudmgr_k_assoc",
                cacheKey = "sogou.CloudMgr#k",
                fastPath = {
                    SymbolResolver.findDeclaredMethodOrNull(
                        cloudMgrCls,
                        "k",
                        CharSequence::class.java,
                        Int::class.javaPrimitiveType!!,
                        Char::class.javaPrimitiveType!!,
                        Int::class.javaPrimitiveType!!,
                        String::class.java,
                        Int::class.javaPrimitiveType!!
                    )
                },
                hooker = XposedInterface.Hooker { chain ->
                    if (ConfigBridge.isTargetManaged(target) && !ConfigBridge.isCloudEnabled(target)) {
                        registry.incrementBlocked(ruleId)
                        return@Hooker null
                    }
                    chain.proceed()
                }
            )

            hookResolvedMethod(
                registry = registry,
                resolver = resolver,
                stats = stats,
                ruleId = ruleId,
                hookId = "cloudmgr_p_state",
                cacheKey = "sogou.CloudMgr#p",
                fastPath = { SymbolResolver.findDeclaredMethodOrNull(cloudMgrCls, "p") },
                hooker = XposedInterface.Hooker { chain ->
                    if (ConfigBridge.isTargetManaged(target) && !ConfigBridge.isCloudEnabled(target)) {
                        return@Hooker 0
                    }
                    chain.proceed()
                }
            )

            hookResolvedMethod(
                registry = registry,
                resolver = resolver,
                stats = stats,
                ruleId = ruleId,
                hookId = "cloudmgr_o_refresh",
                cacheKey = "sogou.CloudMgr#o",
                fastPath = { SymbolResolver.findDeclaredMethodOrNull(cloudMgrCls, "o") },
                hooker = XposedInterface.Hooker { chain ->
                    if (!ConfigBridge.isTargetManaged(target)) {
                        return@Hooker chain.proceed()
                    }
                    val thisObj = chain.getThisObject()
                    val cfg = ConfigBridge.current
                    if (!cfg.effectiveCloudEnabled(target)) {
                        stopCloudMgrLocked(thisObj, refs)
                        registry.incrementBlocked(ruleId)
                        return@Hooker null
                    }
                    val currentM = runCatching { refs.fieldM?.getInt(thisObj) ?: 0 }.getOrDefault(0)
                    if (SogouPayloadSanitizer.shouldForceCloudMgrStateOn(currentM, cfg)) {
                        runCatching { refs.fieldM?.setInt(thisObj, 3) }
                    }
                    chain.proceed()
                }
            )
        } else {
            stats.record(registry, ruleId, "cloud_mgr_class", cloudMgrRes, hooked = false)
        }

        registerLiveCloudConfigListener(registry)

        // CloudWebSocketSender#g(Context, BaseInputRequestInfo) & InputRequestsSender#b(BaseInputRequestInfo)
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

        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "cloud_ws_sender_g",
            cacheKey = "sogou.CloudWebSocketSender#g",
            fastPath = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.input.CloudWebSocketSender", cl)
                val reqCls = SymbolResolver.loadClassOrNull(
                    "com.sohu.inputmethod.input.data.BaseInputRequestInfo",
                    cl
                )
                if (cls != null && reqCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(cls, "g", Context::class.java, reqCls)
                } else {
                    null
                }
            },
            structuralFallback = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.input.CloudWebSocketSender", cl)
                cls?.declaredMethods?.firstOrNull { method ->
                    method.name == "g" &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0] == Context::class.java
                }
            },
            primary = true,
            hooker = XposedInterface.Hooker { chain ->
                val ctx = chain.getArg(0) as? Context
                val reqObj = chain.getArg(1) ?: return@Hooker chain.proceed()
                resolveReqMethods(reqObj)
                val reqType = runCatching { reqTypeMethodRef.get()?.invoke(reqObj) as? Int }.getOrNull()
                    ?: return@Hooker chain.proceed()
                when (SogouPayloadSanitizer.evaluateWebSocketRequest(reqType, ConfigBridge.current)) {
                    SogouPayloadSanitizer.OutboundDecision.ALLOW -> chain.proceed()
                    SogouPayloadSanitizer.OutboundDecision.BLOCK_CLOUD -> {
                        if (ctx != null) runCatching { failMethodRef.get()?.invoke(reqObj, ctx) }
                        registry.incrementBlocked(ruleId)
                        null
                    }
                    SogouPayloadSanitizer.OutboundDecision.BLOCK_MUTUAL -> {
                        if (ctx != null) runCatching { failMethodRef.get()?.invoke(reqObj, ctx) }
                        registry.incrementBlocked(HookRuleCatalog.RULE_SOGOU_MUTUAL_DATA)
                        null
                    }
                }
            }
        )

        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "input_requests_sender_b",
            cacheKey = "sogou.InputRequestsSender#b",
            fastPath = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.input.InputRequestsSender", cl)
                val reqCls = SymbolResolver.loadClassOrNull(
                    "com.sohu.inputmethod.input.data.BaseInputRequestInfo",
                    cl
                )
                if (cls != null && reqCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(cls, "b", reqCls)
                } else {
                    null
                }
            },
            structuralFallback = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.input.InputRequestsSender", cl)
                cls?.declaredMethods?.firstOrNull { method ->
                    method.name == "b" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] != String::class.java
                }
            },
            hooker = XposedInterface.Hooker { chain ->
                val reqObj = chain.getArg(0) ?: return@Hooker chain.proceed()
                resolveReqMethods(reqObj)
                val reqType = runCatching { reqTypeMethodRef.get()?.invoke(reqObj) as? Int }.getOrNull()
                    ?: return@Hooker chain.proceed()
                when (SogouPayloadSanitizer.evaluateWebSocketRequest(reqType, ConfigBridge.current)) {
                    SogouPayloadSanitizer.OutboundDecision.ALLOW -> chain.proceed()
                    SogouPayloadSanitizer.OutboundDecision.BLOCK_CLOUD -> {
                        registry.incrementBlocked(ruleId)
                        null
                    }
                    SogouPayloadSanitizer.OutboundDecision.BLOCK_MUTUAL -> {
                        registry.incrementBlocked(HookRuleCatalog.RULE_SOGOU_MUTUAL_DATA)
                        null
                    }
                }
            }
        )

        // Payload Field Sanitizer: com.sohu.inputmethod.sogou.cloud.b#h(CloudRequestInfo, Context)
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "cloud_body_builder_b_h",
            cacheKey = "sogou.cloud.b#h",
            fastPath = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.cloud.b", cl)
                val reqInfoCls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.cloud.CloudRequestInfo", cl)
                if (cls != null && reqInfoCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(cls, "h", reqInfoCls, Context::class.java)
                } else {
                    null
                }
            },
            dexKitFinder = { dk ->
                findMethodBy(
                    dk = dk,
                    paramTypes = arrayOf(
                        "com.sohu.inputmethod.cloud.CloudRequestInfo",
                        "android.content.Context"
                    ),
                    strings = arrayOf("#%#", "&nbsp", "UTF-16LE")
                )
            },
            structuralFallback = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.cloud.b", cl)
                val reqInfoCls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.cloud.CloudRequestInfo", cl)
                cls?.declaredMethods?.firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 2 &&
                        (reqInfoCls == null || method.parameterTypes[0] == reqInfoCls) &&
                        method.parameterTypes[1] == Context::class.java &&
                        method.returnType != Void.TYPE
                }
            },
            primary = true,
            hooker = payloadSanitizerHooker(ruleId)
        )

        // Cloud Assoc Payload Builder: com.sohu.inputmethod.sogou.cloud.a#i(CloudRequestInfo, Context, Bundle)
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "cloud_assoc_builder_a_i",
            cacheKey = "sogou.cloud.a#i",
            fastPath = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.cloud.a", cl)
                val reqInfoCls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.cloud.CloudRequestInfo", cl)
                if (cls != null && reqInfoCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(
                        cls,
                        "i",
                        reqInfoCls,
                        Context::class.java,
                        Bundle::class.java
                    )
                } else {
                    null
                }
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
            structuralFallback = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.sogou.cloud.a", cl)
                val reqInfoCls = SymbolResolver.loadClassOrNull("com.sohu.inputmethod.cloud.CloudRequestInfo", cl)
                cls?.declaredMethods?.firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 3 &&
                        (reqInfoCls == null || method.parameterTypes[0] == reqInfoCls) &&
                        method.parameterTypes[1] == Context::class.java &&
                        method.parameterTypes[2] == Bundle::class.java &&
                        method.returnType != Void.TYPE
                }
            },
            primary = true,
            hooker = payloadSanitizerHooker(ruleId)
        )

        stats.finalizeRule(registry, ruleId, "CloudSanitize")
    }

    private fun payloadSanitizerHooker(ruleId: String): XposedInterface.Hooker {
        return XposedInterface.Hooker { chain ->
            val body = chain.proceed()
            if (body != null && ConfigBridge.isTargetManaged(target)) {
                if (SogouPayloadSanitizer.sanitizeClientRequestBody(body, ConfigBridge.current)) {
                    registryOrNullIncrement(ruleId)
                }
            }
            body
        }
    }

    @Volatile
    private var activeRegistryRef: HookRegistry? = null

    private fun registryOrNullIncrement(ruleId: String) {
        activeRegistryRef?.incrementBlocked(ruleId)
    }

    private fun registerLiveCloudConfigListener(registry: HookRegistry) {
        activeRegistryRef = registry
        registeredCloudListener.getAndSet(null)?.let { old ->
            ConfigBridge.removeListener(old)
        }
        if (registry.processName != target.packageName) return

        val listener: (PrivacyConfig, PrivacyConfig) -> Unit = { oldCfg, newCfg ->
            val cloudChanged = oldCfg.effectiveCloudEnabled(target) != newCfg.effectiveCloudEnabled(target)
            if (newCfg.sogouManaged && cloudChanged) {
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
    // 3. Telemetry, Pingback, WhiteDog, LogDiag, Bugly, Beacon, BigData Upload
    // -------------------------------------------------------------------------

    private fun installTelemetryRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_TELEMETRY
        val gate: (PrivacyConfig) -> Boolean = { it.blockTelemetry }
        val stats = RuleStats()

        // Pingback delegate (sogou.pingback.f): d, e, f, i, j
        val pingbackFRes = resolver.resolveClass(
            cacheKey = "sogou.pingback.f",
            fastPathCandidates = listOf("sogou.pingback.f"),
            dexKitFinder = { dk ->
                findClassByStrings(dk, "http://kpi2.android.shouji.sogou.com/kpi2.gif")
            }
        )
        listOf("d", "e", "f", "i", "j").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "pingback_f_$mName",
                classResult = pingbackFRes,
                methodName = mName,
                primary = true,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }

        // Sogou pingback/statistic facade com.sohu.inputmethod.sogou.x2: d, f, g, i, j, k, l, m
        val x2Res = resolver.resolveClass(
            cacheKey = "sogou.x2",
            fastPathCandidates = listOf("com.sohu.inputmethod.sogou.x2")
        )
        listOf("d", "f", "g", "i", "j", "k", "l", "m").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "x2_$mName",
                classResult = x2Res,
                methodName = mName,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }

        // WhiteDog collector (com.sogou.lib.whitedog.step.f) & improve.gz uploader (settings.internet.k)
        val whiteDogRes = resolver.resolveClass(
            cacheKey = "sogou.whitedog.step.f",
            fastPathCandidates = listOf("com.sogou.lib.whitedog.step.f"),
            dexKitFinder = { dk ->
                dk.findClass(
                    FindClass.create()
                        .searchPackages("com.sogou.lib.whitedog")
                        .matcher(
                            ClassMatcher.create().addMethod(
                                MethodMatcher.create().paramTypes(
                                    "java.lang.String",
                                    "com.sogou.lib.whitedog.step.InputCollectorCommons\$InputCollectorPrepareStep",
                                    "java.lang.Object[]"
                                )
                            )
                        )
                ).firstOrNull()?.descriptor
            }
        )
        listOf("r", "g", "x").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "whitedog_f_$mName",
                classResult = whiteDogRes,
                methodName = mName,
                primary = true,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }
        val whiteDogUploaderRes = resolver.resolveClass(
            cacheKey = "sogou.whitedog.uploader.k",
            fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.k")
        )
        hookClassMethodsByName(
            registry = registry,
            stats = stats,
            ruleId = ruleId,
            hookIdPrefix = "whitedog_uploader",
            classResult = whiteDogUploaderRes,
            methodName = "onWork",
            hooker = voidNoOpHooker(ruleId, gate)
        )

        // LogDiagSyncLifecycle & imskit.feature.lib.log
        val logDiagRes = resolver.resolveClass(
            cacheKey = "sogou.imskit.LogDiagSyncLifecycle",
            fastPathCandidates = listOf("com.sogou.imskit.feature.lib.log.sync.LogDiagSyncLifecycle")
        )
        listOf("onStartInput", "onStartInputView").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "logdiag_$mName",
                classResult = logDiagRes,
                methodName = mName,
                primary = true,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }
        val logFRes = resolver.resolveClass(
            cacheKey = "sogou.imskit.log.f",
            fastPathCandidates = listOf("com.sogou.imskit.feature.lib.log.f")
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "imskit_log_f_c",
            cacheKey = "sogou.imskit.log.f#c",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    logFRes.value,
                    "c",
                    Boolean::class.javaPrimitiveType!!
                )
            },
            hooker = voidNoOpHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "imskit_log_f_g",
            cacheKey = "sogou.imskit.log.f#g",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    logFRes.value,
                    "g",
                    Long::class.javaPrimitiveType!!,
                    Long::class.javaPrimitiveType!!,
                    String::class.java
                )
            },
            hooker = voidNoOpHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "imskit_log_init_a",
            cacheKey = "sogou.imskit.LogInitServiceImpl_a#a",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull(
                        "com.sogou.imskit.feature.lib.log.initservice.LogInitServiceImpl\$a",
                        cl
                    ),
                    "a"
                )
            },
            hooker = voidNoOpHooker(ruleId, gate)
        )

        // Independent crash/perf & beacon uploaders (local typing DB untouched)
        val scrashlyRes = resolver.resolveClass(
            cacheKey = "sogou.scrashly.a",
            fastPathCandidates = listOf("com.sogou.scrashly.a")
        )
        listOf("d", "f", "g").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "scrashly_$mName",
                classResult = scrashlyRes,
                methodName = mName,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }
        val beaconRes = resolver.resolveClass(
            cacheKey = "sogou.beaconbridge.c",
            fastPathCandidates = listOf("com.sohu.inputmethod.beaconbridge.c")
        )
        listOf("e", "f").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "beacon_$mName",
                classResult = beaconRes,
                methodName = mName,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }
        val bigDataUploaderRes = resolver.resolveClass(
            cacheKey = "sogou.bigdata.internet.a",
            fastPathCandidates = listOf("com.sohu.inputmethod.sogou.bigdata.internet.a")
        )
        listOf("g", "i", "j", "k").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "bigdata_net_$mName",
                classResult = bigDataUploaderRes,
                methodName = mName,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }

        // Status process changeIme.gif
        val statusRes = resolver.resolveClass(
            cacheKey = "sogou.settings.status.a",
            fastPathCandidates = listOf("com.sohu.inputmethod.settings.status.a")
        )
        listOf("h", "i").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "status_a_$mName",
                classResult = statusRes,
                methodName = mName,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }

        stats.finalizeRule(registry, ruleId, "Telemetry")
    }

    // -------------------------------------------------------------------------
    // 4. Mutual Data & BigData Uploaders (Local Typing DB Intact)
    // -------------------------------------------------------------------------

    private fun installMutualDataRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_MUTUAL_DATA
        val gate: (PrivacyConfig) -> Boolean = { it.blockMutualData }
        val stats = RuleStats()

        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "setting_manager_t4",
            cacheKey = "sogou.SettingManager#t4",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull("com.sogou.bu.basic.settings.SettingManager", cl),
                    "t4"
                )
            },
            primary = true,
            hooker = booleanFalseHooker(ruleId, gate)
        )

        val mdmRes = resolver.resolveClass(
            cacheKey = "sogou.MutualDataManager",
            fastPathCandidates = listOf("com.sohu.inputmethod.sogou.mutualdata.MutualDataManager")
        )
        val mdmCls = mdmRes.value
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "mdm_q",
            cacheKey = "sogou.MutualDataManager#q",
            fastPath = { SymbolResolver.findDeclaredMethodOrNull(mdmCls, "q") },
            primary = true,
            hooker = booleanFalseHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "mdm_m",
            cacheKey = "sogou.MutualDataManager#m",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(mdmCls, "m", Int::class.javaPrimitiveType!!)
            },
            hooker = nullHooker(ruleId, gate)
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
            hookResolvedMethod(
                registry = registry,
                resolver = resolver,
                stats = stats,
                ruleId = ruleId,
                hookId = hookId,
                cacheKey = "sogou.MutualDataManager#$mName",
                fastPath = { SymbolResolver.findDeclaredMethodOrNull(mdmCls, mName, *params) },
                primary = mName in setOf("j", "F", "G"),
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }

        val bigDataCRes = resolver.resolveClass(
            cacheKey = "sogou.bigdata.c",
            fastPathCandidates = listOf("com.sohu.inputmethod.sogou.bigdata.c")
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "bigdata_c_D",
            cacheKey = "sogou.bigdata.c#D",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    bigDataCRes.value,
                    "D",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType!!
                )
            },
            hooker = voidNoOpHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "bigdata_c_R",
            cacheKey = "sogou.bigdata.c#R",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    bigDataCRes.value,
                    "R",
                    Int::class.javaPrimitiveType!!
                )
            },
            hooker = voidNoOpHooker(ruleId, gate)
        )

        stats.finalizeRule(registry, ruleId, "MutualData")
    }

    // -------------------------------------------------------------------------
    // 5. Clipboard Background Capture & Sniffer Sinks (User Paste Preserved)
    // -------------------------------------------------------------------------

    private fun installClipboardRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CLIPBOARD
        val gate: (PrivacyConfig) -> Boolean = { it.clipboardPrivacy }
        val stats = RuleStats()

        val backgroundClipHooker = XposedInterface.Hooker { chain ->
            val cfg = ConfigBridge.current
            val shown = cachedImeUiRefs?.isInputViewShown() ?: false
            if (SogouPayloadSanitizer.shouldBlockClipboardBackgroundCapture(shown, cfg)) {
                registry.incrementBlocked(ruleId)
                return@Hooker null
            }
            chain.proceed()
        }

        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "clipboard_x_onPrimaryClipChanged",
            cacheKey = "sogou.clipboard.x#onPrimaryClipChanged",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull("com.sohu.inputmethod.clipboard.x", cl),
                    "onPrimaryClipChanged"
                )
            },
            primary = true,
            hooker = backgroundClipHooker
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "clipboard_p_T",
            cacheKey = "sogou.clipboard.p#T",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull("com.sohu.inputmethod.clipboard.p", cl),
                    "T"
                )
            },
            hooker = backgroundClipHooker
        )

        // Passcode sniffer / background parse & telemetry sinks
        val asyncPenetrateRes = resolver.resolveClass(
            cacheKey = "sogou.asyncpenetrate.c",
            fastPathCandidates = listOf("com.sohu.inputmethod.asyncpenetrate.c")
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "asyncpenetrate_c_m",
            cacheKey = "sogou.asyncpenetrate.c#m",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(asyncPenetrateRes.value, "m", String::class.java)
            },
            primary = true,
            hooker = booleanFalseHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "asyncpenetrate_c_n",
            cacheKey = "sogou.asyncpenetrate.c#n",
            fastPath = { SymbolResolver.findDeclaredMethodOrNull(asyncPenetrateRes.value, "n") },
            hooker = voidNoOpHooker(ruleId, gate)
        )

        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "business_quick_portal_x",
            cacheKey = "sogou.BusinessQuickPortalManager#x",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull(
                        "com.sohu.inputmethod.clipboard.BusinessQuickPortal.BusinessQuickPortalManager",
                        cl
                    ),
                    "x",
                    String::class.java,
                    Boolean::class.javaPrimitiveType!!
                )
            },
            hooker = voidNoOpHooker(ruleId, gate)
        )

        val kouLingRes = resolver.resolveClass(
            cacheKey = "sogou.KouLingClipBoardManager",
            fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.KouLingClipBoardManager")
        )
        listOf("n", "o", "p").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "kouling_$mName",
                classResult = kouLingRes,
                methodName = mName,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }

        val clipLogRes = resolver.resolveClass(
            cacheKey = "sogou.ClipboardLogHelper",
            fastPathCandidates = listOf("com.sohu.inputmethod.clipboard.ClipboardLogHelper")
        )
        listOf("r", "q").forEach { mName ->
            hookClassMethodsByName(
                registry = registry,
                stats = stats,
                ruleId = ruleId,
                hookIdPrefix = "cliplog_$mName",
                classResult = clipLogRes,
                methodName = mName,
                hooker = voidNoOpHooker(ruleId, gate)
            )
        }

        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "vpa_clipboard_show",
            cacheKey = "sogou.VpaClipboardManager#showVpaClipboard",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull(
                        "com.sohu.inputmethod.clipboard.vpaclipboard.VpaClipboardManager",
                        cl
                    ),
                    "showVpaClipboard",
                    String::class.java,
                    Int::class.javaPrimitiveType!!
                )
            },
            hooker = voidNoOpHooker(ruleId, gate)
        )

        stats.finalizeRule(registry, ruleId, "Clipboard")
    }

    // -------------------------------------------------------------------------
    // 6. Contacts Upload & Installed App Scan
    // -------------------------------------------------------------------------

    private fun installContactsAndAppScanRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_CONTACTS_APP_SCAN
        val gate: (PrivacyConfig) -> Boolean = { it.blockContactsAndAppScan }
        val stats = RuleStats()

        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "contacts_b0_h",
            cacheKey = "sogou.settings.b0#h",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull("com.sohu.inputmethod.settings.b0", cl),
                    "h"
                )
            },
            primary = true,
            hooker = voidNoOpHooker(ruleId, gate)
        )

        val utilURes = resolver.resolveClass(
            cacheKey = "sohu.util.u",
            fastPathCandidates = listOf("com.sohu.util.u")
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "util_u_a",
            cacheKey = "sohu.util.u#a",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(utilURes.value, "a", Context::class.java)
            },
            primary = true,
            hooker = emptyStringHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "util_u_b",
            cacheKey = "sohu.util.u#b",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    utilURes.value,
                    "b",
                    Context::class.java,
                    PackageManager::class.java,
                    Int::class.javaPrimitiveType!!
                )
            },
            primary = true,
            hooker = emptyListHooker(ruleId, gate)
        )

        val softwareStatsRes = resolver.resolveClass(
            cacheKey = "sogou.settings.internet.v",
            fastPathCandidates = listOf("com.sohu.inputmethod.settings.internet.v")
        )
        hookClassMethodsByName(
            registry = registry,
            stats = stats,
            ruleId = ruleId,
            hookIdPrefix = "software_stats_v",
            classResult = softwareStatsRes,
            methodName = "onWork",
            hooker = voidNoOpHooker(ruleId, gate)
        )

        stats.finalizeRule(registry, ruleId, "ContactsAppScan")
    }

    // -------------------------------------------------------------------------
    // 7. Surgical Unsigned Remote hj Dynamic DEX Block
    // -------------------------------------------------------------------------

    private fun installDynamicDexRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_DYNAMIC_DEX
        val gate: (PrivacyConfig) -> Boolean = { it.blockUnsignedDynamicDex }
        val stats = RuleStats()

        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "hj_request_work_o",
            cacheKey = "sogou.hj.RequestWork#o",
            fastPath = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sogou.hj.RequestWork", cl)
                val qCls = SymbolResolver.loadClassOrNull("com.sogou.http.okhttp.q", cl)
                if (cls != null && qCls != null) {
                    SymbolResolver.findDeclaredMethodOrNull(cls, "o", qCls)
                } else {
                    null
                }
            },
            structuralFallback = { cl ->
                val cls = SymbolResolver.loadClassOrNull("com.sogou.hj.RequestWork", cl)
                cls?.declaredMethods?.firstOrNull { method ->
                    method.name == "o" && method.parameterCount == 1
                }
            },
            primary = true,
            hooker = voidNoOpHooker(ruleId, gate)
        )

        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "hj_task_executor_run",
            cacheKey = "sogou.hj.TaskExecutor_a#run",
            fastPath = { cl ->
                SymbolResolver.findDeclaredMethodOrNull(
                    SymbolResolver.loadClassOrNull("com.sogou.hj.task.TaskExecutor\$a", cl),
                    "run"
                )
            },
            primary = true,
            hooker = voidNoOpHooker(ruleId, gate)
        )

        val hjTaskARes = resolver.resolveClass(
            cacheKey = "sogou.hj.task.a",
            fastPathCandidates = listOf("com.sogou.hj.task.a")
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "hj_task_a_a",
            cacheKey = "sogou.hj.task.a#a",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    hjTaskARes.value,
                    "a",
                    Any::class.java,
                    String::class.java
                )
            },
            hooker = nullHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "hj_task_a_b",
            cacheKey = "sogou.hj.task.a#b",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    hjTaskARes.value,
                    "b",
                    String::class.java,
                    String::class.java,
                    String::class.java
                )
            },
            hooker = XposedInterface.Hooker { chain ->
                if (!ConfigBridge.isRuleEnabled(target, gate)) {
                    return@Hooker chain.proceed()
                }
                registry.incrementBlocked(ruleId)
                throw ClassNotFoundException("Blocked unsigned hj dynamic dex")
            }
        )

        stats.finalizeRule(registry, ruleId, "DynamicDex")
    }

    // -------------------------------------------------------------------------
    // 8. Optional HTTPDNS Bypass (Default OFF)
    // -------------------------------------------------------------------------

    private fun installHttpDnsRule(
        classLoader: ClassLoader,
        resolver: SymbolResolver,
        registry: HookRegistry
    ) {
        val ruleId = HookRuleCatalog.RULE_SOGOU_HTTPDNS
        val gate: (PrivacyConfig) -> Boolean = { it.blockHttpDns }
        val stats = RuleStats()

        val httpDnsRes = resolver.resolveClass(
            cacheKey = "tencent.msdk.MSDKDnsResolver",
            fastPathCandidates = listOf("com.tencent.msdk.dns.MSDKDnsResolver")
        )
        hookClassMethodsByName(
            registry = registry,
            stats = stats,
            ruleId = ruleId,
            hookIdPrefix = "httpdns_init",
            classResult = httpDnsRes,
            methodName = "init",
            primary = true,
            hooker = voidNoOpHooker(ruleId, gate)
        )
        hookResolvedMethod(
            registry = registry,
            resolver = resolver,
            stats = stats,
            ruleId = ruleId,
            hookId = "httpdns_openid",
            cacheKey = "tencent.MSDKDnsResolver#WGSetDnsOpenId",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    httpDnsRes.value,
                    "WGSetDnsOpenId",
                    String::class.java
                )
            },
            hooker = booleanFalseHooker(ruleId, gate)
        )

        stats.finalizeRule(registry, ruleId, "HttpDns")
    }

    // -------------------------------------------------------------------------
    // Shared Hook & Reflection Helpers
    // -------------------------------------------------------------------------

    private fun hookResolvedMethod(
        registry: HookRegistry,
        resolver: SymbolResolver,
        stats: RuleStats,
        ruleId: String,
        hookId: String,
        cacheKey: String,
        fastPath: (ClassLoader) -> Method?,
        dexKitFinder: ((DexKitBridge) -> String?)? = null,
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

    private fun voidNoOpHooker(
        ruleId: String,
        gate: (PrivacyConfig) -> Boolean
    ): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!ConfigBridge.isRuleEnabled(target, gate)) {
            return@Hooker chain.proceed()
        }
        registryOrNullIncrement(ruleId)
        null
    }

    private fun nullHooker(
        ruleId: String,
        gate: (PrivacyConfig) -> Boolean
    ): XposedInterface.Hooker = voidNoOpHooker(ruleId, gate)

    private fun booleanFalseHooker(
        ruleId: String,
        gate: (PrivacyConfig) -> Boolean
    ): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!ConfigBridge.isRuleEnabled(target, gate)) {
            return@Hooker chain.proceed()
        }
        registryOrNullIncrement(ruleId)
        false
    }

    private fun emptyStringHooker(
        ruleId: String,
        gate: (PrivacyConfig) -> Boolean
    ): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!ConfigBridge.isRuleEnabled(target, gate)) {
            return@Hooker chain.proceed()
        }
        registryOrNullIncrement(ruleId)
        ""
    }

    private fun emptyListHooker(
        ruleId: String,
        gate: (PrivacyConfig) -> Boolean
    ): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        if (!ConfigBridge.isRuleEnabled(target, gate)) {
            return@Hooker chain.proceed()
        }
        registryOrNullIncrement(ruleId)
        emptyList<Any>()
    }

    private fun findClassByStrings(dk: DexKitBridge, vararg strings: String): String? {
        return dk.findClass(
            FindClass.create()
                .searchPackages(*SEARCH_PACKAGES)
                .matcher(ClassMatcher.create().usingStrings(strings.toList(), StringMatchType.Contains))
        ).firstOrNull()?.descriptor
    }

    private fun findMethodBy(
        dk: DexKitBridge,
        returnType: String? = null,
        paramCount: Int? = null,
        paramTypes: Array<String>? = null,
        strings: Array<String>
    ): String? {
        val matcher = MethodMatcher.create().usingStrings(strings.toList(), StringMatchType.Contains)
        if (returnType != null) matcher.returnType(returnType)
        if (paramCount != null) matcher.paramCount(paramCount)
        if (paramTypes != null) matcher.paramTypes(*paramTypes)
        return dk.findMethod(
            FindMethod.create()
                .searchPackages(*SEARCH_PACKAGES)
                .matcher(matcher)
        ).firstOrNull()?.descriptor
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

    /**
     * Safely extracts the target URL from an obfuscated OkHttp `okhttp3.z` (`Request`) instance
     * without assuming fixed field names across minor builds.
     */
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
