package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.ImeTarget
import java.lang.reflect.Array as JvmArray
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure-JVM sanitizer and policy evaluator for Sogou Xiaomi IME cloud payloads and outbound decisions.
 *
 * Designed without Android framework dependencies so both the live Xposed hook on
 * `com.sohu.inputmethod.sogou.cloud.nano.CloudAssocData$ClientRequestBody` and unit tests using
 * plain JVM fixture objects execute the exact same field-stripping logic.
 */
object SogouPayloadSanitizer {

    const val REQUEST_TYPE_CLOUD_INPUT = 1
    const val REQUEST_TYPE_CLOUD_ASSOC = 3
    const val REQUEST_TYPE_NET_SWITCH = 6
    const val REQUEST_TYPE_MUTUAL_DATA = 7
    const val REQUEST_TYPE_DICT_UPGRADE = 11
    const val REQUEST_TYPE_PERSONALIZED_DICT = 13
    const val REQUEST_TYPE_CLOUD_FREQUENCY = 15

    private val CLOUD_CANDIDATE_REQUEST_TYPES = setOf(
        REQUEST_TYPE_CLOUD_INPUT,
        REQUEST_TYPE_CLOUD_ASSOC,
        REQUEST_TYPE_CLOUD_FREQUENCY
    )

    enum class OutboundDecision {
        ALLOW,
        BLOCK_CLOUD,
        BLOCK_MUTUAL
    }

    data class SanitizeReport(
        val modified: Boolean,
        val strippedFields: Set<String>
    )

    private class BodyFieldAccessor(clazz: Class<*>) {
        val dialogue: Field? = findField(clazz, "dialogue")
        val env: Field? = findField(clazz, "env")
        val keypositions: Field? = findField(clazz, "keypositions")
        val usePosAdjust: Field? = findField(clazz, "usePosAdjust")
        val stdata: Field? = findField(clazz, "stdata")
        val stdataBack: Field? = findField(clazz, "stdataBack")
        val device: Field? = findField(clazz, "device")
    }

    private class DeviceFieldAccessor(clazz: Class<*>) {
        val geo: Field? = findField(clazz, "geo")
        val imei: Field? = findField(clazz, "imei")
        val imsi: Field? = findField(clazz, "imsi")
        val h: Field? = findField(clazz, "h")
    }

    private val bodyAccessors = ConcurrentHashMap<Class<*>, BodyFieldAccessor>()
    private val deviceAccessors = ConcurrentHashMap<Class<*>, DeviceFieldAccessor>()

    fun sanitizeClientRequestBody(body: Any?, config: PrivacyConfig): Boolean {
        return sanitizeClientRequestBodyDetailed(body, config).modified
    }

    /**
     * Strips sensitive privacy fields from a Sogou `ClientRequestBody` (or equivalent JVM fixture)
     * according to [config], while keeping `input`, `data`, `syllablefilterinfo`, and `clientcands`
     * completely untouched so cloud candidates and dictionary features remain functional.
     */
    fun sanitizeClientRequestBodyDetailed(body: Any?, config: PrivacyConfig): SanitizeReport {
        if (body == null) return SanitizeReport(modified = false, strippedFields = emptySet())

        val accessor = bodyAccessors.computeIfAbsent(body.javaClass, ::BodyFieldAccessor)
        val stripped = linkedSetOf<String>()

        if (config.sanitizeCloudPayload) {
            if (clearFieldToNull(body, accessor.dialogue)) {
                stripped.add("dialogue")
            }
            val deviceObj = runCatching { accessor.device?.get(body) }.getOrNull()
            if (deviceObj != null) {
                val devAccessor = deviceAccessors.computeIfAbsent(deviceObj.javaClass, ::DeviceFieldAccessor)
                if (clearFieldToNull(deviceObj, devAccessor.geo)) {
                    stripped.add("device.geo")
                }
                if (clearStringField(deviceObj, devAccessor.imei)) {
                    stripped.add("device.imei")
                }
                if (clearStringField(deviceObj, devAccessor.imsi)) {
                    stripped.add("device.imsi")
                }
                if (clearStringField(deviceObj, devAccessor.h)) {
                    stripped.add("device.h")
                }
            }
        }

        if (config.stripCursorContext) {
            if (clearByteArrayField(body, accessor.stdata)) {
                stripped.add("stdata")
            }
            if (clearByteArrayField(body, accessor.stdataBack)) {
                stripped.add("stdataBack")
            }
        }

        if (config.maskHostPackage) {
            if (clearStringField(body, accessor.env)) {
                stripped.add("env")
            }
        }

        if (config.stripTouchPositions) {
            if (clearFieldToNull(body, accessor.keypositions)) {
                stripped.add("keypositions")
            }
            if (clearBooleanField(body, accessor.usePosAdjust)) {
                stripped.add("usePosAdjust")
            }
        }

        return SanitizeReport(modified = stripped.isNotEmpty(), strippedFields = stripped)
    }

    fun evaluateWebSocketRequest(requestType: Int, config: PrivacyConfig): OutboundDecision {
        if (!config.sogouManaged) return OutboundDecision.ALLOW
        if (requestType == REQUEST_TYPE_MUTUAL_DATA && config.blockMutualData) {
            return OutboundDecision.BLOCK_MUTUAL
        }
        if (requestType in CLOUD_CANDIDATE_REQUEST_TYPES && !config.effectiveCloudEnabled(ImeTarget.SOGOU)) {
            return OutboundDecision.BLOCK_CLOUD
        }
        return OutboundDecision.ALLOW
    }

    fun shouldBlockClipboardBackgroundCapture(isInputViewShown: Boolean, config: PrivacyConfig): Boolean {
        return config.sogouManaged && config.clipboardPrivacy && !isInputViewShown
    }

    fun shouldBlockAliveEnforceSendUrl(url: String?, config: PrivacyConfig): Boolean {
        if (!config.sogouManaged || !config.blockTelemetry || url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("/v1/alive/") ||
            lower.contains("special_alive") ||
            lower.contains("trackinstall")
    }

    fun shouldForceCloudMgrStateOn(currentState: Int, config: PrivacyConfig): Boolean {
        return config.sogouManaged &&
            config.cloudOverrideFor(ImeTarget.SOGOU) == CloudOverride.FORCE_ON &&
            currentState == 0
    }

    private fun clearFieldToNull(target: Any, field: Field?): Boolean {
        if (field == null) return false
        return runCatching {
            val current = field.get(target) ?: return@runCatching false
            val hadContent = !current.javaClass.isArray || JvmArray.getLength(current) > 0
            field.set(target, null)
            hadContent
        }.getOrDefault(false)
    }

    private fun clearStringField(target: Any, field: Field?): Boolean {
        if (field == null) return false
        return runCatching {
            val current = field.get(target) as? String
            val hadContent = !current.isNullOrEmpty()
            if (current != "") {
                field.set(target, "")
            }
            hadContent
        }.getOrDefault(false)
    }

    private fun clearByteArrayField(target: Any, field: Field?): Boolean {
        if (field == null) return false
        return runCatching {
            val current = field.get(target) as? ByteArray
            val hadContent = current != null && current.isNotEmpty()
            if (current == null || current.isNotEmpty()) {
                field.set(target, ByteArray(0))
            }
            hadContent
        }.getOrDefault(false)
    }

    private fun clearBooleanField(target: Any, field: Field?): Boolean {
        if (field == null) return false
        return runCatching {
            val current = field.getBoolean(target)
            if (current) {
                field.setBoolean(target, false)
            }
            current
        }.getOrDefault(false)
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
}
