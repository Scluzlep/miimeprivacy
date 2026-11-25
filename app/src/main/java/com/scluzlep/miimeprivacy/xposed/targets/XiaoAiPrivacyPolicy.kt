package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure, JVM-testable decision and payload-sanitization policies for XiaoAi IME (`com.xiaomi.type`).
 * All rules resolve dynamically from [PrivacyConfig.isRuleEnabled] using canonical v2.0
 * `HookRuleCatalog.RULE_XIAOAI_*` identifiers with zero deprecated v1.x global boolean reads.
 */
internal object XiaoAiPrivacyPolicy {

    private val TAG_HEADER_REGEX = Regex("""^\[([a-zA-Z0-9_]+):""")

    private val SENSITIVE_DICT_SYNC_KEYS = setOf(
        "deviceid", "device_id", "oaid", "androidid", "android_id",
        "uid", "userid", "user_id", "imei", "mac", "fromapp",
        "from_app", "packagename", "package_name", "pkg", "apppackage",
        "app_package", "phone", "phonenumber", "phone_number"
    )

    private val ANTIHOOK_SECURITY_PRIVACY_TYPES = setOf(
        "android_id", "device_id", "miui_device_id"
    )

    private val OAID_PHONENUM_PRIVACY_TYPES = setOf(
        "oaid", "line_1_number", "iccid", "imsi", "sub_id",
        "mccmnc", "network_mccmnc", "bluetooth_name", "bluetooth_address",
        "mac_address", "ssid", "bssid", "configured_ssids"
    )

    fun isRuleEnabled(config: PrivacyConfig, ruleId: String): Boolean =
        config.xiaoaiManaged && config.isRuleEnabled(ruleId)

    fun shouldAllowCtaAccept(config: PrivacyConfig, hasExplicitUserAccept: Boolean): Boolean {
        if (!isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_GATE_CTA_FAIL_OPEN)) {
            return true
        }
        return hasExplicitUserAccept
    }

    fun shouldBypassUnlockDegrade(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_GATE_UNLOCK_DEGRADE_BYPASS)

    fun shouldBlockOneTrackTelemetry(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK)

    fun shouldBlockOneTrackEvent(config: PrivacyConfig, isPasswordSession: Boolean): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK) ||
            (isPasswordSession && isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION))

    fun shouldBlockSessionStats(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION)

    fun shouldBlockTaiyiAndJank(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_TAIYI_AND_JANK)

    fun shouldBlockBugreportAndXCrash(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH)

    fun shouldSpoofAntiHookAndSecReport(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT)

    fun shouldBlockOaidInsidPhoneNum(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM)

    fun shouldBlockPrivacyDataType(config: PrivacyConfig, typeName: String?): String? {
        val normalized = typeName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if (normalized in ANTIHOOK_SECURITY_PRIVACY_TYPES && shouldSpoofAntiHookAndSecReport(config)) {
            return HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT
        }
        if (normalized in OAID_PHONENUM_PRIVACY_TYPES && shouldBlockOaidInsidPhoneNum(config)) {
            return HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM
        }
        return null
    }

    fun shouldBlockCloudService(config: PrivacyConfig, ruleId: String): Boolean =
        isRuleEnabled(config, ruleId) && !config.effectiveCloudEnabled(ImeTarget.XIAOAI)

    fun shouldSanitizeMiclawPrompt(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM) ||
            isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG) ||
            isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP) ||
            isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE)

    fun shouldStripMiclawAttachments(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE) ||
            isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP)

    fun shouldBlockScreenCapture(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE)

    fun shouldMaskSpeechHostPackage(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CLOUD_SPEECH_ASR) ||
            isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)

    fun shouldMaskTopActivityAndPkg(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)

    fun shouldBlockVoiceAssistLaunch(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CLOUD_VOICEASSIST_LAUNCH)

    fun shouldBlockBackgroundClipboardRead(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CLIP_BACKGROUND_GUARD)

    fun shouldBlockHotwordAicr(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)

    fun shouldBlockSystemCloudControl(config: PrivacyConfig): Boolean =
        isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL)

    /**
     * Bounded `[tag:...]` prompt sanitizer for `r8.b`, `lb.a`, `jb.q`, and `t7.j3` payloads.
     *
     * Tracks nested bracket depth per `[tag:...]` section so multiline `[peer_text:...]` or
     * `[cursor:...]` content containing inner `[...]` spans is stripped cleanly without swallowing
     * trailing `[lang:...]` headers, `<text>` blocks, or plain body lines.
     */
    fun sanitizeAgentPrompt(prompt: String, config: PrivacyConfig): String {
        if (prompt.isEmpty() || !config.xiaoaiManaged) return prompt

        val dropApp = isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)
        val dropCursorOrDict = isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP)
        val dropScreenshotFlag = isRuleEnabled(config, HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE)
        if (!dropApp && !dropCursorOrDict && !dropScreenshotFlag) return prompt

        val lines = prompt.split('\n')
        if (lines.none { TAG_HEADER_REGEX.containsMatchIn(it) }) {
            return prompt
        }

        val keptSections = ArrayList<String>()
        var currentTag: String? = null
        var currentBuffer: StringBuilder? = null
        var bracketDepth = 0

        fun shouldDropTag(tag: String?): Boolean = when (tag) {
            "app" -> dropApp
            "cursor", "dict", "selected", "peer_text" -> dropCursorOrDict
            "has_screenshot" -> dropScreenshotFlag
            else -> false
        }

        fun flushSection() {
            val buf = currentBuffer ?: return
            if (!shouldDropTag(currentTag)) {
                keptSections.add(buf.toString())
            }
            currentBuffer = null
            currentTag = null
            bracketDepth = 0
        }

        fun updateBracketDepth(line: String, initialDepth: Int): Int {
            var depth = initialDepth
            for (ch in line) {
                if (ch == '[') {
                    depth++
                } else if (ch == ']') {
                    if (depth > 0) depth--
                }
            }
            return depth
        }

        for (line in lines) {
            val headerMatch = TAG_HEADER_REGEX.find(line)
            if (headerMatch != null) {
                flushSection()
                currentTag = headerMatch.groupValues[1]
                currentBuffer = StringBuilder(line)
                bracketDepth = updateBracketDepth(line, 0)
                if (bracketDepth == 0) {
                    flushSection()
                }
            } else if (currentBuffer != null) {
                // Prevent an unclosed sensitive tag from swallowing XML body blocks like `<text>...</text>`
                val trimmed = line.trimStart()
                if (shouldDropTag(currentTag) && (trimmed.startsWith("<text") || trimmed.startsWith("</text"))) {
                    flushSection()
                    keptSections.add(line)
                } else {
                    currentBuffer!!.append('\n').append(line)
                    bracketDepth = updateBracketDepth(line, bracketDepth)
                    if (bracketDepth == 0) {
                        flushSection()
                    }
                }
            } else {
                keptSections.add(line)
            }
        }
        flushSection()

        return keptSections.joinToString("\n")
    }

    /**
     * Strips `from_app`, `asr_text`, and contextual fields from JSON payloads sent via `ca.w#e`/`f`
     * or built by `fb.y5#c`.
     */
    fun sanitizeHotwordJson(rawJson: String?): String {
        if (rawJson.isNullOrBlank()) return rawJson ?: ""
        return runCatching {
            val obj = JSONObject(rawJson)
            if (obj.has("from_app")) obj.put("from_app", "")
            if (obj.has("fromApp")) obj.put("fromApp", "")
            if (obj.has("package_name")) obj.put("package_name", "")
            if (obj.has("packageName")) obj.put("packageName", "")
            obj.remove("asr_text")
            obj.remove("asrText")
            obj.remove("text")
            obj.remove("context")
            obj.toString()
        }.getOrDefault("{}")
    }

    /**
     * Neutralizes anti-hook / emulator / root / debug / device signals in `xc.i#c` JSON.
     */
    fun sanitizeAntiHookEnvJson(rawJson: String?): String? {
        if (rawJson.isNullOrBlank()) return rawJson
        return runCatching {
            val root = JSONObject(rawJson)
            val env = root.optJSONObject("env")
            if (env != null) {
                env.put("xposed", 0)
                env.put("substrate", 0)
                env.put("root", 0)
                env.put("vpn", 0)
                env.put("debug", 0)
                env.put("simulator", 0)
                env.put("virtualapp", 0)
                env.put("never_lock_screen", 0)
                env.put("aps", JSONArray())
                if (env.has("device_id")) env.put("device_id", "")
                if (env.has("boot_time")) env.put("boot_time", 0L)
                if (env.has("install_time")) env.put("install_time", 0L)
            }
            root.toString()
        }.getOrDefault(rawJson)
    }

    /**
     * Scrubs sensitive device/user/host parameters from `i.o0#b` DictPack catalog requests.
     */
    fun sanitizeDictSyncParams(params: Map<*, *>?): Map<Any?, Any?>? {
        params ?: return null
        var changed = false
        val cleaned = LinkedHashMap<Any?, Any?>(params.size)
        for ((k, v) in params) {
            val keyLower = (k as? String)?.trim()?.lowercase()
            if (keyLower != null && keyLower in SENSITIVE_DICT_SYNC_KEYS) {
                changed = true
            } else {
                cleaned[k] = v
            }
        }
        return if (changed) cleaned else null
    }
}

/**
 * Pure JVM-testable guard helpers for XiaoAi's OneTrack initialization, superclass `c` safety,
 * and `ar` dispatch paths.
 */
internal object XiaoAiOneTrackGuard {

    private val noOpArProxyCache = ConcurrentHashMap<Class<*>, Any>()

    fun defaultReturnValue(returnType: Class<*>): Any? = when (returnType) {
        Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
        Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
        Float::class.javaPrimitiveType, Float::class.javaObjectType -> 0f
        Double::class.javaPrimitiveType, Double::class.javaObjectType -> 0.0
        String::class.java -> ""
        else -> null
    }

    /**
     * Handles `au#a(Context)` (`OneTrackImp`).
     *
     * Short-circuits **BEFORE** `proceed()` when `RULE_XIAOAI_TELEMETRY_ONETRACK` is active so
     * `ay` (`ServiceConnectManager` `ba` `bindService`) is never instantiated and irreversible
     * static `OneTrack.b` is never mutated.
     */
    fun handleOneTrackImpInit(
        config: PrivacyConfig,
        proceed: () -> Any?,
        incrementBlocked: () -> Unit
    ): Any? {
        if (XiaoAiPrivacyPolicy.shouldBlockOneTrackTelemetry(config)) {
            incrementBlocked()
            return null
        }
        return proceed()
    }

    /**
     * Returns a non-null no-op dynamic proxy implementing `com.xiaomi.onetrack.api.ar` when
     * `au#a(Context)` short-circuited leaving `c.f4218a == null`, preventing NPE in callers such
     * as `com.xiaomi.onetrack.api.m#onActivityStarted` (`c.j().a(1)`).
     */
    fun ensureSafeArDelegate(
        delegateResult: Any?,
        arInterface: Class<*>?,
        config: PrivacyConfig
    ): Any? {
        if (delegateResult != null || arInterface == null || !arInterface.isInterface) {
            return delegateResult
        }
        if (!XiaoAiPrivacyPolicy.shouldBlockOneTrackTelemetry(config)) {
            return null
        }
        return noOpArProxyCache.computeIfAbsent(arInterface) { iface ->
            Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, _ ->
                when (method.name) {
                    "toString" -> "NoOpOneTrackArProxy"
                    "hashCode" -> 0
                    "equals" -> false
                    else -> defaultReturnValue(method.returnType)
                }
            }
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

    fun invokeDeclaredVoidNoArg(target: Any?, methodName: String): Boolean {
        val m = SymbolResolver.findDeclaredMethodOrNull(target?.javaClass, methodName) ?: return false
        return runCatching {
            m.isAccessible = true
            m.invoke(target)
            true
        }.getOrDefault(false)
    }
}
