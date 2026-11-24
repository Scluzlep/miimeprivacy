package com.scluzlep.miimeprivacy.model

import org.json.JSONArray
import org.json.JSONObject

enum class HookMatchStatus {
    PENDING,
    ACTIVE,
    DEGRADED,
    FALLBACK,
    SKIPPED,
    FAILED
}

data class HookRuleReport(
    val ruleId: String,
    val status: HookMatchStatus,
    val matchedMethod: String = "",
    val source: String = "",
    val blockedCount: Long = 0L,
    val detail: String = ""
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put(KEY_RULE_ID, ruleId)
        put(KEY_STATUS, status.name)
        put(KEY_MATCHED_METHOD, matchedMethod)
        put(KEY_SOURCE, source)
        put(KEY_BLOCKED_COUNT, blockedCount.coerceAtLeast(0L))
        put(KEY_DETAIL, detail)
    }

    companion object {
        private const val KEY_RULE_ID = "ruleId"
        private const val KEY_STATUS = "status"
        private const val KEY_MATCHED_METHOD = "matchedMethod"
        private const val KEY_SOURCE = "source"
        private const val KEY_BLOCKED_COUNT = "blockedCount"
        private const val KEY_DETAIL = "detail"

        fun fromJsonObject(obj: JSONObject): HookRuleReport? {
            val ruleId = obj.optString(KEY_RULE_ID, "").trim()
            if (ruleId.isEmpty()) return null
            val rawStatus = obj.optString(KEY_STATUS, HookMatchStatus.PENDING.name)
            val status = HookMatchStatus.entries.firstOrNull {
                it.name.equals(rawStatus, ignoreCase = true)
            } ?: HookMatchStatus.PENDING
            return HookRuleReport(
                ruleId = ruleId,
                status = status,
                matchedMethod = obj.optString(KEY_MATCHED_METHOD, ""),
                source = obj.optString(KEY_SOURCE, ""),
                blockedCount = obj.optLong(KEY_BLOCKED_COUNT, 0L).coerceAtLeast(0L),
                detail = obj.optString(KEY_DETAIL, "")
            )
        }
    }
}

/**
 * Runtime diagnostics snapshot pushed from a hooked IME process back to the module UI.
 */
data class ImeRuntimeDiagnostics(
    val target: ImeTarget,
    val packageVersionCode: Long = 0L,
    val packageVersionName: String = "",
    val processName: String = "",
    val moduleApiVersion: Int = 102,
    val frameworkName: String = "",
    val activeCloudEnabled: Boolean = true,
    val lastUpdatedAtMs: Long = 0L,
    val rules: List<HookRuleReport> = emptyList(),
    val note: String = ""
) {
    val totalBlockedCount: Long
        get() = rules.sumOf { it.blockedCount }

    val hasCriticalFailure: Boolean
        get() = rules.any { report ->
            report.status == HookMatchStatus.FAILED &&
                (HookRuleCatalog.find(report.ruleId)?.critical == true)
        }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put(KEY_TARGET, target.name)
        put(KEY_TARGET_PACKAGE, target.packageName)
        put(KEY_VERSION_CODE, packageVersionCode)
        put(KEY_VERSION_NAME, packageVersionName)
        put(KEY_PROCESS_NAME, processName)
        put(KEY_MODULE_API_VERSION, moduleApiVersion)
        put(KEY_FRAMEWORK_NAME, frameworkName)
        put(KEY_ACTIVE_CLOUD_ENABLED, activeCloudEnabled)
        put(KEY_LAST_UPDATED_AT_MS, lastUpdatedAtMs)
        put(KEY_NOTE, note)
        put(KEY_RULES, JSONArray().apply {
            rules.forEach { put(it.toJsonObject()) }
        })
    }

    fun toJson(): String = toJsonObject().toString()

    companion object {
        private const val KEY_TARGET = "target"
        private const val KEY_TARGET_PACKAGE = "targetPackage"
        private const val KEY_VERSION_CODE = "packageVersionCode"
        private const val KEY_VERSION_NAME = "packageVersionName"
        private const val KEY_PROCESS_NAME = "processName"
        private const val KEY_MODULE_API_VERSION = "moduleApiVersion"
        private const val KEY_FRAMEWORK_NAME = "frameworkName"
        private const val KEY_ACTIVE_CLOUD_ENABLED = "activeCloudEnabled"
        private const val KEY_LAST_UPDATED_AT_MS = "lastUpdatedAtMs"
        private const val KEY_RULES = "rules"
        private const val KEY_NOTE = "note"

        fun fromJson(json: String?): ImeRuntimeDiagnostics? {
            if (json.isNullOrBlank()) return null
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val targetName = obj.optString(KEY_TARGET, "")
            val targetPkg = obj.optString(KEY_TARGET_PACKAGE, "")
            val target = ImeTarget.entries.firstOrNull { it.name.equals(targetName, ignoreCase = true) }
                ?: ImeTarget.fromPackage(targetPkg)
                ?: return null
            val rulesArr = obj.optJSONArray(KEY_RULES)
            val parsedRules = buildList {
                if (rulesArr != null) {
                    for (i in 0 until rulesArr.length()) {
                        val item = rulesArr.optJSONObject(i) ?: continue
                        HookRuleReport.fromJsonObject(item)?.let(::add)
                    }
                }
            }
            return ImeRuntimeDiagnostics(
                target = target,
                packageVersionCode = obj.optLong(KEY_VERSION_CODE, 0L),
                packageVersionName = obj.optString(KEY_VERSION_NAME, ""),
                processName = obj.optString(KEY_PROCESS_NAME, ""),
                moduleApiVersion = obj.optInt(KEY_MODULE_API_VERSION, 102),
                frameworkName = obj.optString(KEY_FRAMEWORK_NAME, ""),
                activeCloudEnabled = obj.optBoolean(KEY_ACTIVE_CLOUD_ENABLED, true),
                lastUpdatedAtMs = obj.optLong(KEY_LAST_UPDATED_AT_MS, 0L),
                rules = parsedRules,
                note = obj.optString(KEY_NOTE, "")
            )
        }
    }
}
