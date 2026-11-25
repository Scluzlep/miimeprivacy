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
    const val REQUEST_TYPE_CLOUD_ENTERPRISE = 10
    const val REQUEST_TYPE_DICT_UPGRADE = 11
    const val REQUEST_TYPE_LOCATION = 12
    const val REQUEST_TYPE_PERSONALIZED_DICT = 13
    const val REQUEST_TYPE_REAL_TIME_RECOMMEND = 14
    const val REQUEST_TYPE_CLOUD_FREQUENCY = 15

    private val CLOUD_CANDIDATE_REQUEST_TYPES = setOf(
        REQUEST_TYPE_CLOUD_INPUT,
        REQUEST_TYPE_CLOUD_ASSOC,
        REQUEST_TYPE_CLOUD_ENTERPRISE,
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

    data class CloudMgrStateTransition(
        val newState: Int?,
        val nextSavedPriorState: Int?
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

    private class VpaBodyFieldAccessor(clazz: Class<*>) {
        val deviceInfo: Field? = findField(clazz, "b")
        val locationInfo: Field? = findField(clazz, "c")
        val deviceStrings: Field? = findField(clazz, "e")
        val contextWrapper: Field? = findField(clazz, "n")
        val dialogue: Field? = findField(clazz, "z")
    }

    private class VpaDeviceFieldAccessor(clazz: Class<*>) {
        val clientPkg: Field? = findField(clazz, "b")
        val androidId: Field? = findField(clazz, "d")
        val accountId: Field? = findField(clazz, "n")
    }

    private class VpaContextWrapperFieldAccessor(clazz: Class<*>) {
        val contextContainer: Field? = findField(clazz, "f")
    }

    private class VpaContextContainerFieldAccessor(clazz: Class<*>) {
        val cursorContext: Field? = findField(clazz, "c")
    }

    private val bodyAccessors = ConcurrentHashMap<Class<*>, BodyFieldAccessor>()
    private val deviceAccessors = ConcurrentHashMap<Class<*>, DeviceFieldAccessor>()
    private val vpaBodyAccessors = ConcurrentHashMap<Class<*>, VpaBodyFieldAccessor>()
    private val vpaDeviceAccessors = ConcurrentHashMap<Class<*>, VpaDeviceFieldAccessor>()
    private val vpaContextWrapperAccessors = ConcurrentHashMap<Class<*>, VpaContextWrapperFieldAccessor>()
    private val vpaContextContainerAccessors = ConcurrentHashMap<Class<*>, VpaContextContainerFieldAccessor>()

    fun sanitizeClientRequestBody(body: Any?, config: PrivacyConfig): Boolean {
        return sanitizeClientRequestBodyDetailed(body, config).modified
    }

    fun sanitizeClientRequestBody(
        body: Any?,
        sanitizePayloadDeviceDialogue: Boolean,
        stripCursorContext: Boolean,
        maskHostPackage: Boolean,
        stripTouchPositions: Boolean
    ): Boolean {
        return sanitizeClientRequestBodyDetailed(
            body = body,
            sanitizePayloadDeviceDialogue = sanitizePayloadDeviceDialogue,
            stripCursorContext = stripCursorContext,
            maskHostPackage = maskHostPackage,
            stripTouchPositions = stripTouchPositions
        ).modified
    }

    /**
     * Strips sensitive privacy fields from a Sogou `ClientRequestBody` (or equivalent JVM fixture)
     * according to [config], while keeping `input`, `data`, `syllablefilterinfo`, and `clientcands`
     * completely untouched so cloud candidates and dictionary features remain functional.
     */
    fun sanitizeClientRequestBodyDetailed(body: Any?, config: PrivacyConfig): SanitizeReport {
        return sanitizeClientRequestBodyDetailed(
            body = body,
            sanitizePayloadDeviceDialogue = config.sanitizeCloudPayload,
            stripCursorContext = config.stripCursorContext,
            maskHostPackage = config.maskHostPackage,
            stripTouchPositions = config.stripTouchPositions
        )
    }

    /**
     * Granular v2.0 overload evaluating each cloud/context sanitization rule independently.
     */
    fun sanitizeClientRequestBodyDetailed(
        body: Any?,
        sanitizePayloadDeviceDialogue: Boolean,
        stripCursorContext: Boolean,
        maskHostPackage: Boolean,
        stripTouchPositions: Boolean
    ): SanitizeReport {
        if (body == null) return SanitizeReport(modified = false, strippedFields = emptySet())

        val accessor = bodyAccessors.computeIfAbsent(body.javaClass, ::BodyFieldAccessor)
        val stripped = linkedSetOf<String>()

        if (sanitizePayloadDeviceDialogue) {
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

        if (stripCursorContext) {
            if (clearByteArrayField(body, accessor.stdata)) {
                stripped.add("stdata")
            }
            if (clearByteArrayField(body, accessor.stdataBack)) {
                stripped.add("stdataBack")
            }
        }

        if (maskHostPackage) {
            if (clearStringField(body, accessor.env)) {
                stripped.add("env")
            }
        }

        if (stripTouchPositions) {
            if (clearFieldToNull(body, accessor.keypositions)) {
                stripped.add("keypositions")
            }
            if (clearBooleanField(body, accessor.usePosAdjust)) {
                stripped.add("usePosAdjust")
            }
        }

        return SanitizeReport(modified = stripped.isNotEmpty(), strippedFields = stripped)
    }

    /**
     * Sanitizes Wangzai VPA (`FanlingxiParam.clientRequestBody`, protobuf `com.sohu.inputmethod.flx.nano.d`)
     * by stripping dialogue snippets (`z`), device IDs (`b.d`/`b.n`), geo coordinates (`c`),
     * and host package (`b.b`) according to enabled v2.0 rules.
     */
    fun sanitizeWangzaiVpaRequest(
        requestBody: Any?,
        stripDeviceDialogue: Boolean,
        stripLocation: Boolean,
        stripHostPackage: Boolean,
        stripCursorContext: Boolean = false
    ): SanitizeReport {
        if (requestBody == null) return SanitizeReport(modified = false, strippedFields = emptySet())
        val accessor = vpaBodyAccessors.computeIfAbsent(requestBody.javaClass, ::VpaBodyFieldAccessor)
        val stripped = linkedSetOf<String>()

        if (stripDeviceDialogue) {
            if (clearFieldToNull(requestBody, accessor.dialogue)) {
                stripped.add("vpa.dialogue")
            }
            if (clearStringArrayField(requestBody, accessor.deviceStrings)) {
                stripped.add("vpa.deviceStrings")
            }
        }
        if (stripLocation && clearFieldToNull(requestBody, accessor.locationInfo)) {
            stripped.add("vpa.location")
        }

        if (stripCursorContext) {
            val wrapperObj = runCatching { accessor.contextWrapper?.get(requestBody) }.getOrNull()
            if (wrapperObj != null) {
                val wrapperAccessor = vpaContextWrapperAccessors.computeIfAbsent(
                    wrapperObj.javaClass,
                    ::VpaContextWrapperFieldAccessor
                )
                val containerObj = runCatching { wrapperAccessor.contextContainer?.get(wrapperObj) }.getOrNull()
                if (containerObj != null) {
                    val containerAccessor = vpaContextContainerAccessors.computeIfAbsent(
                        containerObj.javaClass,
                        ::VpaContextContainerFieldAccessor
                    )
                    if (clearFieldToNull(containerObj, containerAccessor.cursorContext)) {
                        stripped.add("vpa.cursorContext")
                    }
                }
            }
        }

        val devObj = runCatching { accessor.deviceInfo?.get(requestBody) }.getOrNull()
        if (devObj != null) {
            val devAccessor = vpaDeviceAccessors.computeIfAbsent(devObj.javaClass, ::VpaDeviceFieldAccessor)
            if (stripDeviceDialogue) {
                if (clearStringField(devObj, devAccessor.androidId)) {
                    stripped.add("vpa.androidId")
                }
                if (clearStringField(devObj, devAccessor.accountId)) {
                    stripped.add("vpa.accountId")
                }
            }
            if (stripHostPackage && clearStringField(devObj, devAccessor.clientPkg)) {
                stripped.add("vpa.clientPkg")
            }
        }

        return SanitizeReport(modified = stripped.isNotEmpty(), strippedFields = stripped)
    }

    fun evaluateWebSocketRequest(requestType: Int, config: PrivacyConfig): OutboundDecision {
        return evaluateWebSocketRequest(
            requestType = requestType,
            targetManaged = config.sogouManaged,
            cloudEnabled = config.effectiveCloudEnabled(ImeTarget.SOGOU),
            blockMutualData = config.blockMutualData
        )
    }

    fun evaluateWebSocketRequest(
        requestType: Int,
        targetManaged: Boolean,
        cloudEnabled: Boolean,
        blockMutualData: Boolean
    ): OutboundDecision {
        if (!targetManaged) return OutboundDecision.ALLOW
        if (requestType == REQUEST_TYPE_MUTUAL_DATA && blockMutualData) {
            return OutboundDecision.BLOCK_MUTUAL
        }
        if (requestType in CLOUD_CANDIDATE_REQUEST_TYPES && !cloudEnabled) {
            return OutboundDecision.BLOCK_CLOUD
        }
        return OutboundDecision.ALLOW
    }

    fun shouldBlockClipboardBackgroundCapture(isInputViewShown: Boolean, config: PrivacyConfig): Boolean {
        return shouldBlockClipboardBackgroundCapture(
            isInputViewShown = isInputViewShown,
            ruleEnabled = config.sogouManaged && config.clipboardPrivacy
        )
    }

    fun shouldBlockClipboardBackgroundCapture(isInputViewShown: Boolean, ruleEnabled: Boolean): Boolean {
        return ruleEnabled && !isInputViewShown
    }

    fun shouldBlockAliveEnforceSendUrl(url: String?, config: PrivacyConfig): Boolean {
        return shouldBlockAliveEnforceSendUrl(
            url = url,
            ruleEnabled = config.sogouManaged && config.blockTelemetry
        )
    }

    fun shouldBlockAliveEnforceSendUrl(url: String?, ruleEnabled: Boolean): Boolean {
        if (!ruleEnabled || url.isNullOrBlank()) return false
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

    /**
     * Computes `CloudMgr.m` transitions for `CloudOverride.FORCE_ON` and restores the original
     * `m` value cleanly as soon as the override leaves `FORCE_ON`.
     */
    fun resolveCloudMgrStateTransition(
        currentState: Int,
        savedPriorState: Int?,
        forceOn: Boolean
    ): CloudMgrStateTransition {
        return if (forceOn) {
            if (currentState != 3) {
                CloudMgrStateTransition(
                    newState = 3,
                    nextSavedPriorState = savedPriorState ?: currentState
                )
            } else {
                CloudMgrStateTransition(
                    newState = null,
                    nextSavedPriorState = savedPriorState
                )
            }
        } else {
            if (savedPriorState != null) {
                CloudMgrStateTransition(
                    newState = savedPriorState,
                    nextSavedPriorState = null
                )
            } else {
                CloudMgrStateTransition(
                    newState = null,
                    nextSavedPriorState = null
                )
            }
        }
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

    private fun clearStringArrayField(target: Any, field: Field?): Boolean {
        if (field == null) return false
        return runCatching {
            val current = field.get(target) as? Array<*>
            val hadContent = current != null && current.isNotEmpty()
            if (current == null || current.isNotEmpty()) {
                field.set(target, emptyArray<String>())
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
