package com.scluzlep.miimeprivacy.xposed

import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.targets.XiaoAiAnchors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Contract tests verifying that every FastPath class, method, field, and DexKit string anchor
 * referenced by [XiaoAiHookPack], [SogouHookPack], and [BaiduHookPack] exists in the real
 * decompiled Xiaomi IME sources (baselines: XiaoAi 21363, Sogou 20160, Baidu 7108).
 *
 * Skips gracefully via [Assume.assumeTrue] on CI runners where `decompiled/` is not present.
 */
class RealImeDexContractTest {

    private val decompiledRoot = File("d:/antigproj/xiaomiime/decompiled")
    private val codesourceRoot = File("d:/antigproj/xiaomiime/codesource")

    @Before
    fun requireDecompiledSources() {
        Assume.assumeTrue(
            "Skipping real DEX contract verification: ${decompiledRoot.absolutePath} not present",
            decompiledRoot.isDirectory
        )
    }

    @Test
    fun manifestBaselinesAndImeServicesMatchDecompiledApks() {
        val manifests = mapOf(
            ImeTarget.XIAOAI to "xiaoai",
            ImeTarget.SOGOU to "sogou",
            ImeTarget.BAIDU to "baidu"
        )
        for ((target, folder) in manifests) {
            val manifestFile = File(decompiledRoot, "$folder/resources/AndroidManifest.xml")
            assertTrue("Missing manifest for $folder: ${manifestFile.path}", manifestFile.isFile)
            val xml = manifestFile.readText()
            assertTrue(
                "Package mismatch in $folder manifest; expected ${target.packageName}",
                xml.contains("""package="${target.packageName}"""")
            )
            assertTrue(
                "VersionCode mismatch in $folder manifest; expected ${target.expectedVersionCode}",
                xml.contains("""android:versionCode="${target.expectedVersionCode}"""")
            )
            assertClassExists(folder, target.imeServiceClassName)
        }
    }

    @Test
    fun xiaoAiFastPathSymbolsAndDexKitAnchorsExistInDecompiledV21363() {
        val ime = "xiaoai"
        val classContracts = listOf(
            ClassContract(XiaoAiAnchors.CLASS_MI_IME_SERVICE),
            ClassContract(XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY, listOf("onActivityResult", "requestCtaDialog", "onCtaAccepted")),
            ClassContract(XiaoAiAnchors.CLASS_DEGRADE_LEVEL),
            ClassContract(XiaoAiAnchors.CLASS_SENSOR_ANTIHOOK_HELPER),
            ClassContract(XiaoAiAnchors.CLASS_SECURITY_DEVICE_REPORTER),
            ClassContract(XiaoAiAnchors.CLASS_PRIVACY_DATA_MASTER),
            ClassContract(XiaoAiAnchors.CLASS_PRIVACY_DATA_TYPE),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_OAID_IMPL),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_OAID_HELPER),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_INSID_HELPER),
            ClassContract(XiaoAiAnchors.CLASS_PHONENUM_OBTAIN),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_SDK, listOf(XiaoAiAnchors.METHOD_ONETRACK_SET_NET) + XiaoAiAnchors.ONETRACK_SDK_TRACK_METHODS),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_HELPER, listOf("b", "e", "f")),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_BASE_IMP),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_AR_INTERFACE),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_IMP, listOf("a")),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_SYSTEM_IMP),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_LOCAL_IMP),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_SERVICE_CONN),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_TRACEID_CALLABLE),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_LOG_CONFIG),
            ClassContract(XiaoAiAnchors.CLASS_CRASH_ANALYSIS),
            ClassContract(XiaoAiAnchors.CLASS_DUMP_LOG_PROVIDER),
            ClassContract(XiaoAiAnchors.CLASS_JANK_REPORTER),
            ClassContract(XiaoAiAnchors.CLASS_TAIYI_APP_TRACK_RUNNABLE, listOf("run")),
            ClassContract(XiaoAiAnchors.CLASS_SESSION_COLLECTOR, listOf("a", "b", "c", "d", "e"), listOf(XiaoAiAnchors.FIELD_SESSION_HOLDER)),
            ClassContract(XiaoAiAnchors.CLASS_SESSION_HOLDER, listOf("a"), listOf(XiaoAiAnchors.FIELD_SESSION_PASSWORD)),
            ClassContract(XiaoAiAnchors.CLASS_SESSION_CACHE, listOf("b")),
            ClassContract(XiaoAiAnchors.CLASS_AGENT_CONNECTION, listOf("a")),
            ClassContract(XiaoAiAnchors.CLASS_VOICEASSIST_LAUNCH, listOf("L", "t")),
            ClassContract(XiaoAiAnchors.CLASS_VOICEASSIST_QUERY_HELPER, listOf("W")),
            ClassContract(XiaoAiAnchors.CLASS_VOICEASSIST_STATE),
            ClassContract(XiaoAiAnchors.CLASS_MICLAW_MANAGER, listOf("q")),
            ClassContract(XiaoAiAnchors.CLASS_MICLAW_SESSION, listOf("b")),
            ClassContract(XiaoAiAnchors.CLASS_MICLAW_FAILED),
            ClassContract(XiaoAiAnchors.CLASS_SCREENSHOT_CAPTURE, listOf("c")),
            ClassContract(XiaoAiAnchors.CLASS_TRANSLATE_ENGINE, listOf("f")),
            ClassContract(XiaoAiAnchors.CLASS_TRANSLATE_START, fields = listOf("c")),
            ClassContract(XiaoAiAnchors.CLASS_TRANSLATE_IDLE, fields = listOf("a")),
            ClassContract(XiaoAiAnchors.CLASS_TRANSLATE_ERROR),
            ClassContract(XiaoAiAnchors.CLASS_SPEECH_ASR_MANAGER, listOf("b")),
            ClassContract(XiaoAiAnchors.CLASS_SPEECH_MANAGER),
            ClassContract(XiaoAiAnchors.CLASS_HOTWORD_CLOSURE),
            ClassContract(XiaoAiAnchors.CLASS_DICTPACK_SCHEDULER),
            ClassContract(XiaoAiAnchors.CLASS_DICTPACK_CATALOG_WORKER),
            ClassContract(XiaoAiAnchors.CLASS_DICTPACK_DOWNLOAD_WORKER),
            ClassContract(XiaoAiAnchors.CLASS_HOTDICT_SYNC_WORKER),
            ClassContract(XiaoAiAnchors.CLASS_WORK_RESULT_SUCCESS),
            ClassContract(XiaoAiAnchors.CLASS_CATALOG_HTTP_CLIENT),
            ClassContract(XiaoAiAnchors.CLASS_TAIYI_SERVICE, listOf("d")),
            ClassContract(XiaoAiAnchors.CLASS_TAIYI_MANAGER, listOf("e")),
            ClassContract(XiaoAiAnchors.CLASS_TAIYI_REQUEST),
            ClassContract(XiaoAiAnchors.CLASS_TAIYI_RESPONSE, listOf("create")),
            ClassContract(XiaoAiAnchors.CLASS_CLIPBOARD_MONITOR, listOf("c")),
            ClassContract(XiaoAiAnchors.CLASS_APP_LABEL_HELPER, listOf("x")),
            ClassContract(XiaoAiAnchors.CLASS_HOTWORD_CLIENT, listOf("a", "b")),
            ClassContract(XiaoAiAnchors.CLASS_HOTWORD_INIT_TASK),
            ClassContract(XiaoAiAnchors.CLASS_HOTWORD_EDIT_TRACKER, listOf("a", "f", "h")),
            ClassContract(XiaoAiAnchors.CLASS_VOICE_HOTWORD_CACHE),
            ClassContract(XiaoAiAnchors.CLASS_CLOUD_CONTROL_HELPER, listOf("a")),
            ClassContract(XiaoAiAnchors.CLASS_CLOUD_CONTROL_FLOW, listOf("s")),
            ClassContract(XiaoAiAnchors.CLASS_SCENARIO_RECOGNITION, listOf("getInstance")),
            ClassContract(XiaoAiAnchors.CLASS_SCENARIO_BINDER, listOf("onTransact")),
            ClassContract(XiaoAiAnchors.CLASS_KOTLIN_UNIT),
            ClassContract("cf.c", listOf("c", "a")),
            ClassContract("com.xiaomi.onetrack.util.DeviceUtil", listOf("k", "e")),
            ClassContract("com.xiaomi.onetrack.b.a", listOf("b")),
            ClassContract("com.xiaomi.onetrack.b.g", listOf("f")),
            ClassContract("com.xiaomi.onetrack.b.e", listOf("a")),
            ClassContract("com.xiaomi.onetrack.d.f", listOf("c"), listOf("a")),
            ClassContract("com.xiaomi.onetrack.g.b", listOf("a")),
            ClassContract("com.xiaomi.onetrack.api.as", listOf("a")),
            ClassContract("ca.h", fields = listOf("f2822a")),
            ClassContract("com.iflytek.inputmethod.smartengine.x1", listOf("httpPost")),
            ClassContract(
                "com.iflytek.inputmethod.service.smart.engine.XFInputCommonInfo",
                listOf("nativeSetUid", "nativeSetOsid", "nativeSetPinyinVoiceCloudUrl", "nativeSetSentenceAssociateCloudUrl")
            ),
            ClassContract("com.iflytek.inputmethod.smartengine.n2", listOf("getCursorPretext", "getCloudContextContactText")),
            ClassContract("com.iflytek.inputmethod.service.smart.engine.XFIptLogMgr", listOf("nativeLogInfo", "nativeIptlog_ExtendChoose")),
            ClassContract("com.iflytek.inputmethod.smartengine.d1", listOf("collectStatLogFromNative"))
        )
        assertClassContracts(ime, classContracts)

        val anchorContracts = listOf(
            XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY to (
                XiaoAiAnchors.CTA_ACTIVITY_STRINGS +
                    XiaoAiAnchors.CTA_DIALOG_STRINGS +
                    XiaoAiAnchors.CTA_CONSENTED_STRINGS +
                    XiaoAiAnchors.CTA_RESULT_STRINGS
                ),
            XiaoAiAnchors.CLASS_SENSOR_ANTIHOOK_HELPER to (XiaoAiAnchors.SENSOR_ANTIHOOK_STRINGS + XiaoAiAnchors.SENSOR_ANTIHOOK_ENV_STRINGS),
            XiaoAiAnchors.CLASS_ONETRACK_OAID_IMPL to XiaoAiAnchors.ONETRACK_OAID_IMPL_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_INSID_HELPER to XiaoAiAnchors.ONETRACK_INSID_STRINGS,
            XiaoAiAnchors.CLASS_PHONENUM_OBTAIN to XiaoAiAnchors.PHONENUM_OBTAIN_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_HELPER to (XiaoAiAnchors.ONETRACK_HELPER_STRINGS + XiaoAiAnchors.ONETRACK_HELPER_ERROR_STRINGS),
            XiaoAiAnchors.CLASS_ONETRACK_BASE_IMP to XiaoAiAnchors.ONETRACK_BASE_IMP_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_IMP to XiaoAiAnchors.ONETRACK_IMP_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_SYSTEM_IMP to XiaoAiAnchors.ONETRACK_SYSTEM_IMP_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_LOCAL_IMP to XiaoAiAnchors.ONETRACK_LOCAL_IMP_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_SERVICE_CONN to XiaoAiAnchors.ONETRACK_SERVICE_CONN_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_TRACEID_CALLABLE to XiaoAiAnchors.ONETRACK_TRACEID_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_LOG_CONFIG to XiaoAiAnchors.ONETRACK_LOG_CONFIG_STRINGS,
            XiaoAiAnchors.CLASS_CRASH_ANALYSIS to (XiaoAiAnchors.CRASH_ANALYSIS_HOOKER_STRINGS + XiaoAiAnchors.CRASH_ANALYSIS_REPORT_STRINGS),
            XiaoAiAnchors.CLASS_JANK_REPORTER to XiaoAiAnchors.JANK_REPORTER_STRINGS,
            XiaoAiAnchors.CLASS_TAIYI_APP_TRACK_RUNNABLE to XiaoAiAnchors.TAIYI_APP_TRACK_STRINGS,
            XiaoAiAnchors.CLASS_SESSION_COLLECTOR to (
                XiaoAiAnchors.SESSION_COLLECTOR_STRINGS +
                    XiaoAiAnchors.SESSION_ACTION_BACK_STRINGS +
                    XiaoAiAnchors.SESSION_ACTION_DEL_STRINGS
                ),
            XiaoAiAnchors.CLASS_SESSION_HOLDER to XiaoAiAnchors.SESSION_HOLDER_STRINGS,
            XiaoAiAnchors.CLASS_SESSION_CACHE to XiaoAiAnchors.SESSION_CACHE_STRINGS,
            XiaoAiAnchors.CLASS_AGENT_CONNECTION to XiaoAiAnchors.AGENT_CONNECTION_STRINGS,
            XiaoAiAnchors.CLASS_VOICEASSIST_LAUNCH to (XiaoAiAnchors.VOICEASSIST_ASSIST_STRINGS + XiaoAiAnchors.DEGRADE_HELPER_STRINGS),
            XiaoAiAnchors.CLASS_VOICEASSIST_QUERY_HELPER to XiaoAiAnchors.VOICEASSIST_QUERY_STRINGS,
            XiaoAiAnchors.CLASS_MICLAW_MANAGER to (XiaoAiAnchors.MICLAW_MANAGER_STRINGS + XiaoAiAnchors.MICLAW_SUBMIT_STRINGS),
            XiaoAiAnchors.CLASS_MICLAW_FAILED to XiaoAiAnchors.MICLAW_FAILED_STRINGS,
            XiaoAiAnchors.CLASS_SCREENSHOT_CAPTURE to XiaoAiAnchors.SCREENSHOT_CAPTURE_STRINGS,
            XiaoAiAnchors.CLASS_TRANSLATE_ENGINE to XiaoAiAnchors.TRANSLATE_ENGINE_STRINGS,
            XiaoAiAnchors.CLASS_TRANSLATE_START to XiaoAiAnchors.TRANSLATE_START_STRINGS,
            XiaoAiAnchors.CLASS_TRANSLATE_IDLE to XiaoAiAnchors.TRANSLATE_IDLE_STRINGS,
            XiaoAiAnchors.CLASS_TRANSLATE_ERROR to XiaoAiAnchors.TRANSLATE_ERROR_STRINGS,
            XiaoAiAnchors.CLASS_SPEECH_ASR_MANAGER to (XiaoAiAnchors.SPEECH_ASR_STRINGS + XiaoAiAnchors.SPEECH_ASR_START_STRINGS),
            XiaoAiAnchors.CLASS_SPEECH_MANAGER to XiaoAiAnchors.SPEECH_MANAGER_STRINGS,
            XiaoAiAnchors.CLASS_HOTWORD_CLOSURE to XiaoAiAnchors.HOTWORD_CLOSURE_STRINGS,
            XiaoAiAnchors.CLASS_DICTPACK_SCHEDULER to XiaoAiAnchors.DICTPACK_SCHEDULER_STRINGS,
            XiaoAiAnchors.CLASS_WORK_RESULT_SUCCESS to XiaoAiAnchors.WORK_RESULT_SUCCESS_STRINGS,
            XiaoAiAnchors.CLASS_CATALOG_HTTP_CLIENT to XiaoAiAnchors.CATALOG_HTTP_CLIENT_STRINGS,
            XiaoAiAnchors.CLASS_TAIYI_SERVICE to (XiaoAiAnchors.TAIYI_SERVICE_STRINGS + XiaoAiAnchors.TAIYI_CONNECT_STRINGS),
            XiaoAiAnchors.CLASS_TAIYI_MANAGER to XiaoAiAnchors.TAIYI_MANAGER_STRINGS,
            XiaoAiAnchors.CLASS_CLIPBOARD_MONITOR to XiaoAiAnchors.CLIPBOARD_MONITOR_STRINGS,
            XiaoAiAnchors.CLASS_HOTWORD_CLIENT to (XiaoAiAnchors.HOTWORD_CLIENT_STRINGS + XiaoAiAnchors.HOTWORD_STATIC_STRINGS + XiaoAiAnchors.HOTWORD_STATIC_PAGED_STRINGS),
            XiaoAiAnchors.CLASS_HOTWORD_INIT_TASK to XiaoAiAnchors.HOTWORD_INIT_TASK_STRINGS,
            XiaoAiAnchors.CLASS_HOTWORD_EDIT_TRACKER to XiaoAiAnchors.HOTWORD_TRACKER_STRINGS,
            XiaoAiAnchors.CLASS_VOICE_HOTWORD_CACHE to XiaoAiAnchors.VOICE_HOTWORD_CACHE_STRINGS,
            XiaoAiAnchors.CLASS_CLOUD_CONTROL_HELPER to XiaoAiAnchors.CLOUD_CONTROL_STRINGS,
            XiaoAiAnchors.CLASS_CLOUD_CONTROL_FLOW to XiaoAiAnchors.CLOUD_CONTROL_FLOW_STRINGS,
            XiaoAiAnchors.CLASS_SCENARIO_BINDER to XiaoAiAnchors.SCENARIO_BINDER_STRINGS,
            XiaoAiAnchors.CLASS_KOTLIN_UNIT to XiaoAiAnchors.KOTLIN_UNIT_STRINGS,
            "com.iflytek.inputmethod.smartengine.x1" to listOf("XFInputCommonWorkImpl", "onSuccess sid: "),
            "com.iflytek.inputmethod.smartengine.n2" to listOf("XFInputTextConnectionImpl", "CloudContextContact handle before text:"),
            "com.iflytek.inputmethod.smartengine.d1" to listOf("GeneralDecoder", "p_initCloud: getAid"),
            "cf.c" to listOf("MiuiAccountPhoneNumberManager", "getAccountCertifications succeed", "getSIMInfos succeed"),
            "com.xiaomi.onetrack.util.DeviceUtil" to listOf("ro.mi.xms.version.incremental", "persist.sys.xms.version", "android_id"),
            "com.xiaomi.onetrack.b.a" to listOf("pullCloudData start, appIds: "),
            "com.xiaomi.onetrack.b.g" to listOf("CommonConfigUpdater", "regionUrl", "requestCloudData: "),
            "com.xiaomi.onetrack.b.e" to listOf("CloudControlClawConfigUpdater"),
            "com.xiaomi.onetrack.d.f" to listOf("SecretKeyManager", "secretKey"),
            "com.xiaomi.onetrack.g.b" to listOf("doGetAdMonitor response true ,monitor or url is null", "upload file error"),
            "com.xiaomi.onetrack.api.as" to listOf("OneTrackExceptionHandler", ".java.xcrash")
        )
        assertAnchorStrings(ime, anchorContracts)
        assertAllSourceClassLiteralsExist(ime, "targets/XiaoAiHookPack.kt")

        // Verify XiaoAiAnchors.FIELD_DEGRADE_STRICT matches real DEX field "c" (JADX renames it to f17039c)
        assertTrue(
            "Expected XiaoAiAnchors.FIELD_DEGRADE_STRICT == \"c\", got ${XiaoAiAnchors.FIELD_DEGRADE_STRICT}",
            XiaoAiAnchors.FIELD_DEGRADE_STRICT == "c"
        )
        val degradeLevelSrc = readDecompiledClass(ime, XiaoAiAnchors.CLASS_DEGRADE_LEVEL)
        assertTrue(
            "Expected t7.e to contain JADX rename comment for '${XiaoAiAnchors.FIELD_DEGRADE_STRICT}' and STRICT",
            degradeLevelSrc.contains("renamed from: ${XiaoAiAnchors.FIELD_DEGRADE_STRICT}") &&
                degradeLevelSrc.contains("\"STRICT\"")
        )
    }

    @Test
    fun xiaoAiHookPackDoesNotTargetCtaConsentGetterJ9EN() {
        // Confirm j9.e#n() really exists as the cta_consented getter in XiaoAi 21363
        val j9eSource = readDecompiledClass("xiaoai", "j9.e")
        assertTrue(
            "Expected j9.e#n() to read cta_consented in decompiled XiaoAi",
            j9eSource.contains("boolean n()") && j9eSource.contains("\"cta_consented\"")
        )

        val xiaoAiHookFile = File(
            codesourceRoot,
            "app/src/main/java/com/scluzlep/miimeprivacy/xposed/targets/XiaoAiHookPack.kt"
        )
        assertTrue("Missing XiaoAiHookPack.kt at ${xiaoAiHookFile.path}", xiaoAiHookFile.isFile)
        val codeWithoutComments = stripKotlinComments(xiaoAiHookFile.readText())
        assertFalse(
            "XiaoAiHookPack.kt must NOT reference or target j9.e in executable code",
            codeWithoutComments.contains("j9.e")
        )
        assertFalse(
            "XiaoAiHookPack.kt must NOT call or hook method n() on consent getter",
            Regex("""fastPathCandidates\s*=\s*listOf\("j9\.e"\)""").containsMatchIn(codeWithoutComments)
        )
    }

    @Test
    fun sogouFastPathSymbolsAndDexKitAnchorsExistInDecompiledV20160() {
        val ime = "sogou"
        val classContracts = listOf(
            ClassContract("com.sogou.alive.a", listOf("c", "d", "e")),
            ClassContract("com.sogou.alive.strategy.a", listOf("a")),
            ClassContract("com.sogou.alive.strategy.b", listOf("h")),
            ClassContract("com.sohu.inputmethod.settings.internet.alive.e", listOf("a", "b", "c", "d")),
            ClassContract("com.sohu.inputmethod.sogou.privacy.a", listOf("a")),
            ClassContract("com.sohu.inputmethod.sogou.launch.d\$d", listOf("b", "c")),
            ClassContract("com.sogou.networking.reporter.okhttp.a"),
            ClassContract("com.sogou.networking.reporter.a", listOf("j")),
            ClassContract("com.sogou.networking.f"),
            ClassContract("com.sogou.networking.d"),
            ClassContract("okhttp3.z"),
            ClassContract("com.sogou.lib.device.oaid.c", listOf("h")),
            ClassContract("com.sogou.lib.device.f", listOf("A")),
            ClassContract("com.sogou.lib.device.m", listOf("b", "c", "d", "f", "k", "l", "m")),
            ClassContract("com.sogou.lib.device.n"),
            ClassContract("com.sogou.lib.device.support.b", listOf("b")),
            ClassContract("com.weibo.ssosdk.WeiboSsoSdk", listOf("riseWind", "j", "l")),
            ClassContract("com.sogou.inputmethod.oem.util.j"),
            ClassContract("com.sogou.lib.device.i", listOf("h", "j")),
            ClassContract("com.sogou.bu.basic.util.InfoManager", listOf("getAndroidID", "m", "f")),
            ClassContract("com.sogou.bu.basic.util.b", listOf("e")),
            ClassContract("com.sohu.inputmethod.businessadvisement.b", listOf("d")),
            ClassContract("com.sohu.inputmethod.businessadvisement.d", listOf("c")),
            ClassContract("com.sohu.inputmethod.sogou.j0", listOf("b")),
            ClassContract("com.sohu.inputmethod.settings.NetworkProcessHandler", listOf("k0")),
            ClassContract("com.sohu.inputmethod.settings.internet.t", listOf("onWork")),
            ClassContract("com.sohu.inputmethod.settings.internet.d", listOf("onWork")),
            ClassContract("com.sohu.inputmethod.settings.internet.g", listOf("onWork")),
            ClassContract("com.sohu.inputmethod.settings.internet.n", listOf("onWork")),
            ClassContract("com.sohu.inputmethod.settings.internet.upgrade.h", listOf("onWork")),
            ClassContract("com.sohu.inputmethod.voiceinput.pingback.a", listOf("onWork")),
            ClassContract("base.sogou.mobile.hotwordsbase.http.a", listOf("d", "e", "f", "g", "h", "i", "j")),
            ClassContract("com.sogou.hj.d", listOf("b")),
            ClassContract("com.sogou.hj.RequestWork", listOf("o", "s")),
            ClassContract("com.sohu.inputmethod.internet.okhttp.g", listOf("a")),
            ClassContract("com.tencent.raft.measure.report.ATTAReporter", listOf("doPostBatchReport")),
            ClassContract("com.sdk.sogou.pingback.a", listOf("i")),
            ClassContract("com.sdk.doutu.request.HttpClient", listOf("sendPingback")),
            ClassContract("com.sdk.sogou.pingback.base.b", listOf("d")),
            ClassContract("com.sogou.theme.network.e", listOf("t", "v", "w")),
            ClassContract("com.sohu.inputmethod.messageAnr.net.a", listOf("a")),
            ClassContract("com.sogou.lib.performance.fluency.FluencyNativeInterface", listOf("refreshHookFromSettings", "hookBinderNative", "hookIoNative", "hookAssetManagerNative")),
            ClassContract("base.sogou.mobile.hotwordsbase.utils.a", listOf("A", "B")),
            ClassContract("com.lib.ac.ACCheckerInterface", listOf("getType")),
            ClassContract("base.sogou.mobile.hotwordsbase.utils.CommonLib", listOf("getCellLAC")),
            ClassContract("com.sohu.inputmethod.settings.internet.lbsnetnotify.b", listOf("onWork")),
            ClassContract("com.sohu.inputmethod.flx.nano.d", fields = listOf("e", "n")),
            ClassContract("com.sohu.inputmethod.flx.nano.g", fields = listOf("f")),
            ClassContract("com.sohu.inputmethod.flx.nano.i", fields = listOf("c")),
            ClassContract("com.sohu.inputmethod.flx.nano.b0", fields = listOf("c")),
            ClassContract("com.sohu.inputmethod.cloud.CloudSettings", listOf("isCloudSinkOn", "isCloudSinkAndSendWhenDown")),
            ClassContract(
                "com.sogou.inputmethod.cloud.api.ctrl.CloudMgr",
                listOf("O0", "m", "P0", "k", "p", "o", "h1", "V0", "v"),
                listOf("z", "m", "x")
            ),
            ClassContract("com.sohu.inputmethod.sogou.j2", fields = listOf("C0")),
            ClassContract("com.sohu.inputmethod.sogou.v2", listOf("l", "e")),
            ClassContract("com.sohu.inputmethod.sogou.MainImeServiceDel", listOf("getInstance", "isInputViewShown")),
            ClassContract("com.sohu.inputmethod.input.CloudWebSocketSender", listOf("g")),
            ClassContract("com.sohu.inputmethod.input.InputRequestsSender", listOf("b")),
            ClassContract("com.sogou.inputmethod.sogou.cloud.data.BaseInputRequestInfo", listOf("getRequestType", "onDownloadFail")),
            ClassContract("com.sohu.inputmethod.sogou.cloud.b", listOf("h")),
            ClassContract("com.sohu.inputmethod.sogou.cloud.a", listOf("h", "i")),
            ClassContract("com.sohu.inputmethod.cloud.CloudRequestInfo"),
            ClassContract(
                "com.sohu.inputmethod.sogou.cloud.nano.CloudAssocData",
                fields = listOf(
                    "dialogue", "env", "keypositions", "usePosAdjust", "stdata", "stdataBack",
                    "device", "geo", "imei", "imsi", "h", "input", "data", "syllablefilterinfo", "clientcands"
                )
            ),
            ClassContract("com.sohu.inputmethod.flx.trigger.FanlingxiParam", listOf("assemble")),
            ClassContract("com.sogou.inputmethod.voice_input.models.f", listOf("e")),
            ClassContract("com.sogou.ai.nsrss.modules.conf.AsrConfig", listOf("withRuntimeProductId")),
            ClassContract("com.sogou.ai.nsrss.modules.conf.MtConfig", listOf("withUUID")),
            ClassContract("com.sogou.inputmethod.voice_input.models.n", listOf("d")),
            ClassContract("com.sogou.inputmethod.voice_input.bean.d", listOf("c")),
            ClassContract("sogou.pingback.f", listOf("d", "e", "f", "i", "j")),
            ClassContract("com.sohu.inputmethod.sogou.x2", listOf("d", "f", "g", "i", "j", "k", "l", "m")),
            ClassContract("com.sohu.inputmethod.settings.internet.l", listOf("onWork")),
            ClassContract("base.sogou.mobile.hotwordsbase.pingback.SendPingBackTask"),
            ClassContract("com.sohu.inputmethod.sogou.r3"),
            ClassContract("com.sogou.lib.whitedog.step.f", listOf("r", "g", "x")),
            ClassContract("com.sogou.lib.whitedog.step.InputCollectorCommons\$InputCollectorPrepareStep"),
            ClassContract("com.sohu.inputmethod.settings.internet.k", listOf("onWork")),
            ClassContract("com.sogou.imskit.feature.lib.log.sync.LogDiagSyncLifecycle", listOf("onStartInput", "onStartInputView")),
            ClassContract("com.sogou.imskit.feature.lib.log.f", listOf("c", "g")),
            ClassContract("com.sogou.imskit.feature.lib.log.initservice.LogInitServiceImpl\$a", listOf("a")),
            ClassContract("com.sogou.scrashly.a", listOf("d", "f", "g")),
            ClassContract("com.sohu.inputmethod.sogou.u3", listOf("i")),
            ClassContract("com.sohu.inputmethod.engine.ErrorTrace"),
            ClassContract("com.sogou.nativecrashcollector.d", listOf("h")),
            ClassContract("com.sohu.inputmethod.beaconbridge.c", listOf("e", "f")),
            ClassContract("com.sogou.inputmethod.beacon.d"),
            ClassContract("com.sogou.inputmethod.beacon.e", listOf("f")),
            ClassContract("com.sogou.plus.qimei.QimeiHelper", listOf("getQimeiSync")),
            ClassContract("com.sohu.util.w"),
            ClassContract("com.sogou.inputmethod.oem.xiaomi.miadvertise.d"),
            ClassContract("com.sogou.inputmethod.oem.xiaomi.miadvertise.g"),
            ClassContract("com.sogou.inputmethod.oem.xiaomi.miadvertise.MiAdvertiseDataHelper", listOf("k", "p")),
            ClassContract("com.sogou.inputmethod.oem.xiaomi.miadvertise.f", listOf("a")),
            ClassContract("com.sohu.inputmethod.sogou.bigdata.internet.a", listOf("g", "i", "j", "k")),
            ClassContract("com.sohu.inputmethod.settings.status.a", listOf("h", "i")),
            ClassContract("com.sogou.bu.basic.settings.SettingManager", listOf("t4")),
            ClassContract("com.sohu.inputmethod.sogou.mutualdata.MutualDataManager", listOf("q", "m", "w", "C", "D", "z", "j", "F", "G", "A", "B")),
            ClassContract("com.sohu.inputmethod.sogou.bigdata.c", listOf("D", "E", "R")),
            ClassContract("com.sohu.inputmethod.sogou.bigdata.mutual.c"),
            ClassContract("com.sohu.inputmethod.clipboard.x", listOf("onPrimaryClipChanged")),
            ClassContract("com.sohu.inputmethod.clipboard.p", listOf("T", "y")),
            ClassContract("com.sohu.inputmethod.clipboard.q", listOf("m", "n")),
            ClassContract("com.sohu.inputmethod.asyncpenetrate.c", listOf("m", "n")),
            ClassContract("com.sohu.inputmethod.clipboard.BusinessQuickPortal.BusinessQuickPortalManager", listOf("x")),
            ClassContract("com.sohu.inputmethod.clipboard.KouLingClipBoardManager", listOf("n", "o", "p")),
            ClassContract("com.sohu.inputmethod.clipboard.ClipboardLogHelper", listOf("r", "q")),
            ClassContract("com.sohu.inputmethod.clipboard.vpaclipboard.VpaClipboardManager", listOf("showVpaClipboard")),
            ClassContract("com.sohu.inputmethod.clipboard.autotranslate.CopyAutoTranslateManager", listOf("n")),
            ClassContract("base.sogou.mobile.hotwordsbase.utils.SogouJSInterface", listOf("requestClipBoardText", "getQ36")),
            ClassContract("com.sohu.inputmethod.flx.miniprogram.view.FlxImeWebView", listOf("getClipBoardContent", "loadUrlByJs")),
            ClassContract("com.sohu.inputmethod.flx.miniprogram.view.FlxImeWebView\$FlxProgramJsInterface"),
            ClassContract("com.sohu.inputmethod.settings.deviceinfo.b", listOf("m", "p", "s")),
            ClassContract("com.sohu.inputmethod.settings.deviceinfo.DeviceInfoServiceImpl", listOf("getLocation")),
            ClassContract("com.sohu.inputmethod.settings.ContactsDictionary"),
            ClassContract("com.sohu.inputmethod.flx.util.recorder.FlxLocationRecorder", listOf("i")),
            ClassContract("com.sohu.inputmethod.flx.controller.WifiLocationController", listOf("e")),
            ClassContract("com.sohu.inputmethod.settings.b0", listOf("h")),
            ClassContract("com.sohu.util.u", listOf("a", "b")),
            ClassContract("com.sohu.inputmethod.settings.internet.v", listOf("onWork")),
            ClassContract("com.sogou.http.okhttp.q"),
            ClassContract("com.sogou.hj.task.TaskExecutor\$a", listOf("run")),
            ClassContract("com.sogou.hj.task.a", listOf("a", "b")),
            ClassContract("com.sogou.loaddex.c", listOf("a", "b")),
            ClassContract("com.sogou.loaddex.d", listOf("onWork")),
            ClassContract("com.sogou.common_components.vibratesound.vibrator.f", listOf("f")),
            ClassContract("com.sohu.inputmethod.sogou.bigdata.so.a", listOf("w")),
            ClassContract("com.sogou.hotfix.patcher.InstallDex", listOf("c")),
            ClassContract("com.sogou.base.hotfix.b"),
            ClassContract("com.sogou.debug.b"),
            ClassContract("com.tencent.msdk.dns.MSDKDnsResolver", listOf("init", "WGSetDnsOpenId")),
            ClassContract("com.sogou.http.monitor.dns.b", listOf("c", "l")),
            ClassContract("com.sohu.inputmethod.beaconbridge.c\$a"),
            ClassContract("com.sohu.inputmethod.internet.InternetConnection"),
            ClassContract("com.sogou.threadpool.net.a"),
            ClassContract(
                "com.sogou.expressionplugin.ui.MoreColorEmojiPopupView",
                listOf("setEmojiDrawable", "dealMoreColorCommitEmoji", "changeCurrentIndex")
            ),
            ClassContract(
                "com.sogou.sogou_router_base.base_bean.BaseExpressionInfo",
                listOf("canShowGroupEmojiAnnotation", "isWeiXinCandidate")
            ),
            ClassContract(
                "com.sohu.inputmethod.engine.IMENativeInterface",
                listOf(
                    "getCloudPingbackInfo",
                    "getCorrectPingbackInfo",
                    "getIndividualPingback",
                    "getInputPingbackInfo",
                    "getCloudWhiteDogInfo",
                    "checkUploadUsrDict",
                    "pingbackAction",
                    "pingbackCloudAction",
                    "setClientPackageName"
                ),
                fields = listOf("mNativeContext")
            ),
            ClassContract(
                "sogouime.security.SecurityInterface",
                listOf("postguard", "proguard", "getRule", "applyRule")
            )
        )
        assertClassContracts(ime, classContracts)

        val anchorContracts = listOf(
            "com.sogou.alive.strategy.a" to listOf("special_alive"),
            "com.sohu.inputmethod.settings.internet.alive.e" to listOf("/v1/alive/install"),
            "com.sohu.inputmethod.sogou.privacy.a" to listOf("enforce_send=", "do not send network request without allowing privacy"),
            "com.sogou.lib.device.oaid.c" to listOf("tencent_oaid", "200100"),
            "com.sogou.lib.device.f" to listOf("pref_mac", "ro.serialno"),
            "com.sogou.lib.device.m" to listOf("socketwrapper", "doPostBatchReport: isPrivate: %b"),
            "com.sogou.lib.device.n" to listOf("310260000000000"),
            "com.sogou.lib.device.i" to listOf("pref_android_id", "/sogou/files/uuid"),
            "com.sogou.bu.basic.util.InfoManager" to listOf("gsm_", "cdma_"),
            "com.sohu.inputmethod.sogou.v2" to listOf("pwpur", "offpwpur", "indiv_applist.zip"),
            "com.sohu.inputmethod.sogou.cloud.b" to listOf("#%#", "&nbsp", "UTF-16LE"),
            "com.sohu.inputmethod.sogou.cloud.a" to listOf("cloud_assoc_before_context", "#%#", "UTF-16LE"),
            "com.sohu.inputmethod.flx.trigger.FanlingxiParam" to listOf("android_oem_xiaomi_sogou", "vc8v7vghw7v278vn2v8239vh29vh890m"),
            "com.sogou.inputmethod.voice_input.models.f" to listOf("namespace=speech_addressbook&key="),
            "com.sogou.inputmethod.voice_input.bean.d" to listOf("notStopSpaceVoice", "clickKeyStartInput"),
            "sogou.pingback.f" to listOf("http://kpi2.android.shouji.sogou.com/kpi2.gif"),
            "com.sohu.inputmethod.sogou.x2" to listOf("http://instantpb.android.shouji.sogou.com/instantpb.gif"),
            "com.sohu.inputmethod.settings.internet.l" to listOf("http://pb6.sogou.com/androidinput.gif"),
            "base.sogou.mobile.hotwordsbase.pingback.SendPingBackTask" to listOf("http://androidsdk.ping.mse.sogou.com/androidSDKBrowser.png"),
            "com.sohu.inputmethod.sogou.r3" to listOf("app_data_sp", "b1227"),
            "com.sogou.imskit.feature.lib.log.f" to listOf("用户反馈上报"),
            "com.sohu.inputmethod.sogou.bigdata.internet.a" to listOf("app_info_count", "app_info_time"),
            "com.sogou.inputmethod.beacon.d" to listOf("beaconManager", "eventName"),
            "com.sogou.inputmethod.beacon.e" to listOf("ostar_mmkv", "ostar36", "ostar16"),
            "com.sogou.plus.qimei.QimeiHelper" to listOf("1AE26A39Q74EU7JH", "sogou-plus"),
            "com.sogou.inputmethod.oem.xiaomi.miadvertise.d" to listOf("https://adv.sec.miui.com/info/icon", "XiaomiVoiceAssistUtils"),
            "com.sogou.inputmethod.oem.xiaomi.miadvertise.g" to listOf("https://adv.sec.miui.com/info/icon_st"),
            "com.sogou.inputmethod.oem.xiaomi.miadvertise.MiAdvertiseDataHelper" to listOf("isAdDataValid", "cme_5", "cme_15"),
            "com.sogou.scrashly.a" to listOf("Bugly oa 2.0 is open!", "BU_INPUT:"),
            "com.sohu.inputmethod.sogou.u3" to listOf("a508ec6e-be5b-41a1-8f1c-5eed3a608db0", "c56bbe5e1b"),
            "com.sohu.inputmethod.engine.ErrorTrace" to listOf("native_crash.txt", "sogou_anr.txt"),
            "com.sogou.nativecrashcollector.d" to listOf("crash_collect"),
            "com.sohu.inputmethod.sogou.bigdata.c" to listOf("load_sql_cipher_so_now_key", "BigDataManager"),
            "com.sohu.inputmethod.sogou.bigdata.mutual.c" to listOf("send_hujin_data_error", "send_hujin_data_success", "SyncStatus"),
            "com.sohu.inputmethod.clipboard.vpaclipboard.VpaClipboardManager" to listOf("sp_key_vpa_clipboard_unlimit_show_times"),
            "com.sohu.inputmethod.clipboard.KouLingClipBoardManager" to listOf("KouLing_Service"),
            "com.sohu.inputmethod.asyncpenetrate.c" to listOf("no penetrate_code_valid", "has penetrate_code_valid: "),
            "com.sohu.inputmethod.clipboard.BusinessQuickPortal.BusinessQuickPortalManager" to listOf("replace_tpwd"),
            "com.sohu.inputmethod.clipboard.ClipboardLogHelper" to listOf("f07ad47b1e860f4fa62e87ff822dd48a", "Clipboard Feedback Error"),
            "com.sohu.inputmethod.clipboard.autotranslate.CopyAutoTranslateManager" to listOf("translate_words", "copy_auto_translate_entrance"),
            "base.sogou.mobile.hotwordsbase.utils.SogouJSInterface" to listOf("SogouHotwordsUtils", "requestClipBoardText"),
            "com.sohu.inputmethod.flx.miniprogram.view.FlxImeWebView" to listOf("responseClipBoardText", "key_sogou_clipboard"),
            "com.sohu.inputmethod.clipboard.p" to listOf("ClipboardSPUtils_tryBackupLostClipboardDataFromSystem", "insertClipboard"),
            "com.sohu.inputmethod.clipboard.x" to listOf("SystemClipboardListener", "SystemClipboardListener_onPrimaryClipChanged_"),
            "com.sohu.inputmethod.clipboard.q" to listOf("ClipboardToCandsController onCandidateChange"),
            "com.sohu.inputmethod.settings.deviceinfo.b" to listOf("data_set desc  limit  0,5000", "date desc  limit  0,5000"),
            "com.sohu.inputmethod.settings.b0" to listOf("display_name"),
            "com.sohu.inputmethod.settings.ContactsDictionary" to listOf("mimetype='vnd.android.cursor.item/phone_v2'"),
            "com.sohu.inputmethod.flx.util.recorder.FlxLocationRecorder" to listOf("cdma", "network", "location"),
            "com.sohu.inputmethod.flx.controller.WifiLocationController" to listOf("innerdata=", "neighbors"),
            "com.sohu.inputmethod.settings.internet.v" to listOf("SoftwareStatistics-messagetmp.xml", "SoftwareStatistics"),
            "com.sogou.hj.RequestWork" to listOf("http://srv.android.shouji.sogou.com/v1/config/get_mpkg"),
            "com.sogou.loaddex.c" to listOf("opt_dex"),
            "com.sogou.loaddex.d" to listOf("/dex/"),
            "com.sogou.common_components.vibratesound.vibrator.f" to listOf("com.sogou.common_components.vibratesound.vibrator.VibratorFactory"),
            "com.sohu.inputmethod.sogou.bigdata.so.a" to listOf("libsqlcipher.zip", "libsqlcipher.so_"),
            "com.sogou.hotfix.patcher.InstallDex" to listOf("dexElements", "loadDexFile"),
            "com.sogou.base.hotfix.b" to listOf("HotFixManager", "rfix_patch_"),
            "com.sogou.debug.b" to listOf("debug_dex_local.jar", "DebugDex"),
            "com.sogou.http.monitor.dns.b" to listOf("get.sogou.com", "v2.get.sogou.com", "sec.sginput.qq.com"),
            "com.sogou.expressionplugin.ui.MoreColorEmojiPopupView" to listOf("MoreColorEmojiPopupView", "_1F3FB"),
            "com.sogou.lib.device.support.b" to listOf("cat /sys/block/mmcblk0/device/cid", "pref_sdcard_serial_id"),
            "com.weibo.ssosdk.WeiboSsoSdk" to listOf("weibo_sso_sdk_aid", "https://login.sina.com.cn/visitor/signin"),
            "com.sogou.bu.basic.util.b" to listOf("http://instantpb.android.shouji.sogou.com/deeplinkpb.gif"),
            "com.sohu.inputmethod.sogou.j0" to listOf("http://instantpb.android.shouji.sogou.com/ucenter.gif?type=tip_show"),
            "com.sohu.inputmethod.settings.NetworkProcessHandler" to listOf("input_efficiency_pingback", "cloud_efficiency_pingback"),
            "com.sohu.inputmethod.settings.internet.upgrade.h" to listOf("utf-8"),
            "base.sogou.mobile.hotwordsbase.http.a" to listOf("https://flxpb.shouji.sogou.com/", "https://pb.sogou.com/pv.gif"),
            "com.sogou.hj.d" to listOf("http://data.ping.shouji.sogou.com/bigdata.gif"),
            "com.sohu.inputmethod.internet.InternetConnection" to listOf("offlinespeechpb.gz"),
            "com.sohu.inputmethod.internet.okhttp.g" to listOf("http://instantpb.android.shouji.sogou.com/skinsharebtn.gif"),
            "com.tencent.raft.measure.report.ATTAReporter" to listOf("https://h.trace.qq.com/kv", "doPostBatchReport err"),
            "com.sdk.sogou.pingback.a" to listOf("PingbackUtils_2_0"),
            "com.sdk.sogou.pingback.base.b" to listOf("uigs_productid=tugele&version="),
            "com.sogou.theme.network.e" to listOf("http://instantpb.android.shouji.sogou.com/skinsharebtn.gif"),
            "com.sohu.inputmethod.messageAnr.net.a" to listOf("http://ping-msganr.android.shouji.sogou.com/msganr"),
            "com.sogou.lib.performance.fluency.FluencyNativeInterface" to listOf("fluency_monitor"),
            "base.sogou.mobile.hotwordsbase.utils.a" to listOf("clipboard"),
            "com.sohu.inputmethod.settings.internet.lbsnetnotify.b" to listOf("sogou.action.delete.lbs.notification"),
            "sogouime.security.SecurityInterface" to listOf("native-sec")
        )
        assertAnchorStrings(ime, anchorContracts)
        assertAllSourceClassLiteralsExist(ime, "targets/SogouHookPack.kt")
        assertAllSourceClassLiteralsExist(ime, "../emoji/SogouDualSkinPopupHook.kt")
    }

    @Test
    fun baiduFastPathSymbolsAndDexKitAnchorsExistInDecompiledV7108() {
        val ime = "baidu"
        val classContracts = listOf(
            ClassContract("e20.g", listOf("a")),
            ClassContract("tn0.e", listOf("isPrivacyAgreed")),
            ClassContract("ll.a"),
            ClassContract("com.baidu.ubc.UBCHelper", listOf("isAgreePrivacy")),
            ClassContract("dj0.y", listOf("h", "A")),
            ClassContract("ph0.e", listOf("h")),
            ClassContract("kh0.j"),
            ClassContract("wc1.a", listOf("a")),
            ClassContract("b51.g", listOf("a")),
            ClassContract("fa.a"),
            ClassContract("n9.c", listOf("b")),
            ClassContract("n9.e"),
            ClassContract("q9.b", listOf("S")),
            ClassContract("n7.a"),
            ClassContract("com.baidu.util.ImeCommonParam", listOf("uploadIdentifiers")),
            ClassContract("v9.a", listOf("run")),
            ClassContract("wi0.a"),
            ClassContract("tk1.c"),
            ClassContract("kq0.c", listOf("d")),
            ClassContract("dj0.g0", listOf("e")),
            ClassContract("dj0.p", fields = listOf("f33941v")),
            ClassContract("com.baidu.input.b", listOf("b")),
            ClassContract("com.baidu.sofire.rp.Report"),
            ClassContract("p4.a", listOf("F")),
            ClassContract("ay.g", listOf("e")),
            ClassContract("com.baidu.stats.impl.StatsUploader", listOf("a")),
            ClassContract("gz0.b", listOf("d", "onFail")),
            ClassContract("com.baidu.stats.realtime.a"),
            ClassContract("sj0.a", listOf("b"), listOf("b")),
            ClassContract("ju.a", listOf("k0")),
            ClassContract("com.baidu.ubc.BehaviorUploader", listOf("obUploadDataRequest", "uploadDataRequest")),
            ClassContract("com.baidu.mobstat.LogSender", listOf("saveLogData")),
            ClassContract("com.baidu.input.ime.voicerecognize.voicetrace.a", listOf("b", "c", "d"), listOf("f15013d")),
            ClassContract("com.baidu.input.ime.voicerecognize.voicetrace.TraceBean"),
            ClassContract("rj0.a", listOf("b"), listOf("f60199d")),
            ClassContract("rj0.c", listOf("c")),
            ClassContract("b7.c", listOf("w")),
            ClassContract("rh0.a", listOf("a")),
            ClassContract("xcrash.b", listOf("a")),
            ClassContract("zl1.f"),
            ClassContract("com.baidu.webkit.sdk.dumper.ZeusCrashHandler", listOf("init")),
            ClassContract("com.baidu.webkit.sdk.dumper.ZeusCrashHandler\$ZeusCrashHandlerClient", listOf("onUploadLogFile")),
            ClassContract("c9.f", listOf("a")),
            ClassContract("c9.i", listOf("U")),
            ClassContract("x00.b", listOf("c")),
            ClassContract("com.baidu.iptcore.net.IptCloudStream", listOf("send")),
            ClassContract("com.baidu.input.ime.cloudinput.CloudInfo", listOf("getSugPackageInfo", "get_app_name", "get_city", "get_json_buf")),
            ClassContract("com.baidu.input.ime.cloudinput.CloudLog", listOf("get_pkg_name")),
            ClassContract("com.baidu.input.aicard.impl.uploader.b", listOf("b")),
            ClassContract("com.baidu.input.aicard.impl.uploader.a", listOf("g", "h")),
            ClassContract("pg.b"),
            ClassContract("dj0.l", fields = listOf("I1")),
            ClassContract("ch0.b", listOf("b")),
            ClassContract("ff.s", listOf("b")),
            ClassContract("com.baidu.talos.core.modules.clipboard.ClipboardModule", listOf("getString")),
            ClassContract("xz0.c", listOf("a", "c", "e", "g", "j", "l")),
            ClassContract("com.baidu.sapi2.SapiJsInterpreters\$AddressManageGetPasteboard", listOf("interpret")),
            ClassContract(
                "com.baidu.input.clipboard.ClipboardGlobal\$clipboardListener\$2",
                listOf("onPrimaryClipChanged")
            ),
            ClassContract("wg0.b", listOf("u")),
            ClassContract("bl.c", listOf("K1")),
            ClassContract("com.baidu.input.inspiration_corpus.api.XiaoMiClipData"),
            ClassContract("com.baidu.input.clipboard.manager.a", listOf("j")),
            ClassContract("com.baidu.input.clipboard.datamanager.clipboard.a", listOf("J1")),
            ClassContract("q30.e", listOf("o")),
            ClassContract("q30.d", listOf("onChange")),
            ClassContract("q30.f", listOf("run")),
            ClassContract("t40.b", listOf("a", "d")),
            ClassContract("lp0.c"),
            ClassContract("com.baidu.util.ContactsUtils", listOf("getContactsByContent")),
            ClassContract("oj0.c", listOf("a")),
            ClassContract("v6.i", listOf("j")),
            ClassContract("rj0.b", listOf("b"), listOf("d")),
            ClassContract("com.baidu.input.ime.newcore.ImeEnvCallback", listOf("findApp", "hasInstallApp")),
            ClassContract("com.baidu.iptcore.info.IptAppMsgInfo"),
            ClassContract("l11.b", listOf("f1")),
            ClassContract("zg0.e"),
            ClassContract("com.baidu.talos.core.modules.location.LocationModule"),
            ClassContract("com.baidu.mobstat.v"),
            ClassContract("com.baidu.android.util.connect.NetWorkUtils", listOf("getBSSID")),
            ClassContract("com.baidu.android.util.devices.NetWorkUtils"),
            ClassContract("zd.e", listOf("n")),
            ClassContract("js.a", listOf("h"), listOf("b")),
            ClassContract("fg.w", listOf("b", "c"), listOf("a")),
            ClassContract("com.baidu.input.pl.impl.a", listOf("a")),
            ClassContract("oi0.g"),
            ClassContract("mh.v", listOf("onPageFinished")),
            ClassContract("com.baidu.browser.sailor.BdSailorWebView"),
            ClassContract("com.baidu.speech.core.BDSHttpRequestMaker\$1", listOf("verify")),
            ClassContract("com.baidu.speech.ocr.OcrHttpRequestMaker\$1", listOf("verify")),
            ClassContract("com.baidu.webkit.internal.daemon.HttpDnsCache", listOf("tryToUpdateHttpDnsCache", "tryToUpdateHttpDnsCacheStaticIP")),
            ClassContract("com.baidu.webkit.internal.daemon.HttpDnsCacheForHost", listOf("tryToUpdateHttpDnsCacheHost")),
            ClassContract("com.baidu.webkit.internal.blink.WebSettingsGlobalBlink", listOf("getHttpDnsUpdateEnabled")),
            ClassContract("com.baidu.turbonet.net.TurbonetEngine\$Builder", listOf("enableBaiduDns")),
            ClassContract("com.baidu.turbonet.net.TurbonetConfig", listOf("enableBaiduDns")),
            ClassContract("com.baidu.stats.realtime.PagePVSourceEnum"),
            ClassContract("com.baidu.sapi2.SapiWebView\$Command"),
            ClassContract("com.baidu.talos.core.data.ParamMap"),
            ClassContract("fa.m", listOf("a")),
            ClassContract("ha.b", listOf("b")),
            ClassContract("ga.b", listOf("a")),
            ClassContract("ga.d", listOf("a")),
            ClassContract("com.baidu.sofire.ac.F", listOf("gom", "gzd", "rf")),
            ClassContract("com.baidu.android.common.util.DeviceId", listOf("getDeviceID", "getCUID")),
            ClassContract("com.baidu.zeus.jsr.statistics.LogUploader", listOf("uploadRealTimeData", "nativeSetStatisticsTransmission")),
            ClassContract("com.baidu.iptcore.IptCoreInterface", listOf("userTraceWrite", "userVoiceInputLog", "setUplDataAddress", "sendLocationEvent")),
            ClassContract("com.baidu.speech.utils.analysis.AnalysisUpload", listOf("checkIsUpload", "getUploadPermission", "dataCallBack")),
            ClassContract("com.baidu.speech.utils.analysis.AnalysisInterceptor", listOf("initLocalAsrWpData", "writeLocalFile", "setCallBack")),
            ClassContract("com.baidu.input.ime.newcore.ImeEnvCallback", listOf("findApp", "hasInstallApp", "isAllowCloudAdShow", "requestUrlResource")),
            ClassContract("com.baidu.input.aicard.impl.b", listOf("M1")),
            ClassContract("c2.d", listOf("k")),
            ClassContract("com.baidu.sapi2.SapiJsInterpreters\$ActionGetSmsCheckCodeFromClip", listOf("interpret")),
            ClassContract("com.baidu.input.clipboard.datamanager.clipboard.a", listOf("J1", "Y1")),
            ClassContract("com.baidu.business.Product", listOf("p")),
            ClassContract("wg0.b", listOf("u", "B")),
            ClassContract("com.baidu.android.util.connect.NetWorkUtils", listOf("getBSSID", "getWifiName", "getWifiInfo")),
            ClassContract("com.baidu.android.util.devices.NetWorkUtils", listOf("getWifiName", "getWifiInfo")),
            ClassContract("com.baidu.turbonet.net.NetworkChangeNotifierAutoDetect\$WifiManagerDelegate", listOf("getWifiSSID")),
            ClassContract("com.baidu.turbonet.net.TurbonetEngine\$Builder", listOf("enableBaiduDns", "addBdnsBackupHosts", "applyBaiduConfiguration")),
            ClassContract("com.baidu.speech.ocr.OcrHttpRequestMaker", listOf("makeRequest")),
            ClassContract("com.baidu.speech.core.BDSMessage"),
            ClassContract("com.baidu.speech.core.BDSCoreJniInterface", listOf("postMessage", "Post")),
            ClassContract("com.baidu.speech.core.BDSHttpRequestMaker", listOf("makeRequest", "setupConnection", "sendData")),
            ClassContract("com.baidu.talos.core.data.ParamArray", listOf("pushMap", "putDynamic")),
            ClassContract("com.baidu.talos.core.bridge.TalosBridgeManager", listOf("callJSFunction", "callNative"), listOf("mBridgeImpl")),
            ClassContract("com.baidu.webkit.internal.monitor.SessionMonitorEngine", listOf("uploadRealTimeDataToUBCForJSR", "recordImmediately")),
            ClassContract("com.baidu.simeji.dictionary.session.helper.UploadHelper", listOf("checkIfUpload")),
            ClassContract("com.baidu.cyberplayer.sdk.statistics.UbcSessionUploader", listOf("upload")),
            ClassContract("com.baidu.cyberplayer.sdk.statistics.DpSessionDatasUploader", listOf("upload", "uploadLibInitSession"))
        )
        assertClassContracts(ime, classContracts)

        // Synthetic lambda com.baidu.input.clipboard.b and TLS verifier t6.a are D8-merged into enclosing files
        val clipListenerSrc = readDecompiledClass(ime, "com.baidu.input.clipboard.ClipboardGlobal\$clipboardListener\$2")
        assertTrue(
            "Expected D8 synthetic listener com.baidu.input.clipboard.b inside ClipboardGlobal\$clipboardListener\$2",
            clipListenerSrc.contains("com.baidu.input.clipboard.b")
        )
        val iptStreamSrc = readDecompiledClass(ime, "com.baidu.iptcore.net.IptCloudStream")
        assertTrue(
            "Expected D8 synthetic HostnameVerifier t6.a inside IptCloudStream",
            iptStreamSrc.contains("from class: t6.a") && iptStreamSrc.contains("baidu.com")
        )

        val anchorContracts = listOf(
            "e20.g" to listOf("PrivacyDialogUtils.java"),
            "tn0.e" to listOf("An operation is not implemented: Not yet implemented"),
            "com.baidu.ubc.UBCHelper" to listOf("https://tcbox.baidu.com/ztbox?action=zubc"),
            "dj0.y" to listOf("option_log", "Option is empty, reset default setting"),
            "ph0.e" to listOf("NotiSwitch", "pref_key_ccs", "pref_key_adv_swtich"),
            "wc1.a" to listOf("IdentifierManager", "getOAID", "getUDID"),
            "b51.g" to listOf("/data/local/su", "/data/local/bin/su"),
            "n9.c" to listOf("""\u200bcom.baidu.helios.HeliosManager"""),
            "q9.b" to listOf(".helios.ipc.default", ":helios"),
            "v9.a" to listOf("https://mbd.baidu.com/store"),
            "wi0.a" to listOf("com.baidu.input.privacy.impl.aspectj.PermissionAspect", "android_id"),
            "kq0.c" to listOf("no_device_sdk"),
            "dj0.g0" to listOf("ua_cuid_uc_oaid_oid_ecs", "&is_double_cand="),
            "com.baidu.input.b" to listOf("&cuid=emptycuid"),
            "p4.a" to listOf("sids", "installts", "lstupdatets"),
            "ay.g" to listOf("Unknown RatingType: ", "bdst", "bdrl", "skin.token"),
            "com.baidu.stats.realtime.a" to listOf("ime_stats.stats_impl.realtimedurationreportservice.duration_draft_cache_v1"),
            "sj0.a" to listOf("key_upload_kp_data_time"),
            "ju.a" to listOf("sug_mi_statistic"),
            "com.baidu.ubc.BehaviorUploader" to listOf("CRONET"),
            "com.baidu.mobstat.LogSender" to listOf("__track_send_data_"),
            "rj0.a" to listOf("BaseUpdateCommand: ", "no file exist,no upload"),
            "b7.c" to listOf("ads_log"),
            "rh0.a" to listOf("ADNotificationRunner Time: "),
            "xcrash.b" to listOf("/tombstones"),
            "com.baidu.webkit.sdk.dumper.ZeusCrashHandler" to listOf("ZeusCrashHandler"),
            "c9.f" to listOf("1.2.3-beta456-patch789"),
            "x00.b" to listOf("sug_info", "close -> sugSwitch close "),
            "com.baidu.iptcore.net.IptCloudStream" to listOf("cfile", "log_type=7"),
            "com.baidu.input.ime.voicerecognize.voicetrace.a" to listOf("wufeiyang", "traceBean : "),
            "com.baidu.input.aicard.impl.uploader.a" to listOf("WenxinDataUploader.kt", "wenXinData"),
            "dj0.l" to listOf("isNeedShowFlowDialog = ", ",isPrivacyInputMode"),
            "ch0.b" to listOf("pref_key_clip_operation_token"),
            "ff.s" to listOf("Utils.kt", "create<String> {emitter"),
            "com.baidu.input.clipboard.ClipboardGlobal\$clipboardListener\$2" to listOf("ClipboardGlobal", "privacy not agreed"),
            "bl.c" to listOf("ImeInspirationCorpusDependencyImpl.kt"),
            "com.baidu.input.clipboard.datamanager.clipboard.a" to listOf("ClipboardDataManagerImpl", "safeDelete exception"),
            "wg0.b" to listOf("mms_is_ververification_code"),
            "q30.e" to listOf("android.permission.READ_SMS", "count_contsign"),
            "q30.d" to listOf("content://sms/inbox-insert"),
            "q30.f" to listOf("content://sms/inbox"),
            "t40.b" to listOf("ContactCustomManager", "https://upl.baidu.com/words/add"),
            "com.baidu.util.ContactsUtils" to listOf("ContactsUtils.java", "display_name", "data1"),
            "oj0.c" to listOf("android.permission.READ_CONTACTS"),
            "v6.i" to listOf("display_name", "data1"),
            "rj0.b" to listOf("installinfo", "isinstalled", "shortcutinfo", "guiderinfo"),
            "com.baidu.input.ime.newcore.ImeEnvCallback" to listOf("isAllowAiPadAutoOpen type:"),
            "l11.b" to listOf("wapicon", "install"),
            "com.baidu.mobstat.v" to listOf("ap-list", "meta-data"),
            "zd.e" to listOf("TwilightManager", "Failed to get last known location"),
            "js.a" to listOf("DataCollectorHandler", "config file download success"),
            "fg.w" to listOf("enSysDown word info error", "ensysdown_", "cell_loc", "wordlib_info"),
            "com.baidu.input.pl.impl.a" to listOf("ime_pls_files", ".apk"),
            "mh.v" to listOf("https://ksone.it2sh.com/baidu/test/bdinput_ex.js"),
            "com.baidu.speech.core.BDSHttpRequestMaker\$1" to listOf("audiotest.baidu.com", "verifyUrl : "),
            "com.baidu.speech.ocr.OcrHttpRequestMaker\$1" to listOf("119.75.222.172", "httpsdns.baidu.com"),
            "fa.m" to listOf("msaoaidsec"),
            "ha.b" to listOf("oaid_certificate", "msa/msa.pem"),
            "ga.b" to listOf("HonorOAID", "getHonorOaid Exception: "),
            "ga.d" to listOf("com.uodis.opendevice.OPENIDS_SERVICE", "com.huawei.hwid"),
            "com.baidu.android.common.util.DeviceId" to listOf("libcuid.so", "DeviceId"),
            "com.baidu.zeus.jsr.statistics.LogUploader" to listOf("tryToSetStatisticsTransmissionNative failed: "),
            "com.baidu.iptcore.IptCoreInterface" to listOf("cloud_black_filter.bin"),
            "com.baidu.speech.utils.analysis.AnalysisUpload" to listOf("https://upl.baidu.com/slp/upload_monitor?log_sn="),
            "com.baidu.speech.utils.analysis.AnalysisInterceptor" to listOf("AnalysisInterceptor :"),
            "com.baidu.input.aicard.impl.b" to listOf("AICardImpl.kt", "imeserv.sysConnection"),
            "c2.d" to listOf("SLR", "read from config error: "),
            "com.baidu.business.Product" to listOf("UTF-8"),
            "com.baidu.speech.ocr.OcrHttpRequestMaker" to listOf("OcrHttpRequestMaker", "dueros-voice-open.baidu.com"),
            "com.baidu.speech.core.BDSMessage" to listOf(" messageParamsCount=", " messageParams:{  "),
            "com.baidu.speech.core.BDSCoreJniInterface" to listOf("core event", "Listener is null for instance id "),
            "com.baidu.speech.core.BDSHttpRequestMaker" to listOf("dueros-voice.baidu.com"),
            "com.baidu.webkit.internal.monitor.SessionMonitorEngine" to listOf("uploadRealTimeData mWiseSid = "),
            "com.baidu.simeji.dictionary.session.helper.UploadHelper" to listOf("upload/pickpu/", "report/c/simejiEn/android/lsob"),
            "com.baidu.cyberplayer.sdk.statistics.UbcSessionUploader" to listOf("UbcSessionUploader", "UBC upload by Dependency-Inject, UBCID: "),
            "com.baidu.cyberplayer.sdk.statistics.DpSessionDatasUploader" to listOf("https://browserkernel.baidu.com/kw?r_en=true&type=")
        )
        assertAnchorStrings(ime, anchorContracts)
        assertAllSourceClassLiteralsExist(ime, "targets/BaiduHookPack.kt")

        val baiduPackFile = File(
            codesourceRoot,
            "app/src/main/java/com/scluzlep/miimeprivacy/xposed/targets/BaiduHookPack.kt"
        )
        val baiduCode = stripKotlinComments(baiduPackFile.readText())
        assertFalse(
            "BaiduHookPack.kt must NOT use JADX-renamed com.baidu.res.* in runtime code",
            baiduCode.contains("com.baidu.res.")
        )
        assertTrue(
            "BaiduHookPack.kt must use real DEX package com.baidu.util",
            baiduCode.contains("\"com.baidu.util\"")
        )
        assertTrue(
            "BaiduHookPack.kt must preserve real DEX package t6 for t6.a",
            baiduCode.contains("\"t6\"")
        )
    }

    private data class ClassContract(
        val className: String,
        val methods: List<String> = emptyList(),
        val fields: List<String> = emptyList()
    )

    private fun assertClassContracts(ime: String, contracts: List<ClassContract>) {
        for (contract in contracts) {
            val source = assertClassExists(ime, contract.className)
            for (method in contract.methods) {
                val pattern = Regex("""\b${Regex.escape(method)}\s*\(""")
                assertTrue(
                    "[$ime] Missing method ${contract.className}#$method",
                    pattern.containsMatchIn(source)
                )
            }
            for (field in contract.fields) {
                val pattern = Regex("""\b${Regex.escape(field)}\b""")
                assertTrue(
                    "[$ime] Missing field ${contract.className}#$field",
                    pattern.containsMatchIn(source)
                )
            }
        }
    }

    private fun assertAnchorStrings(ime: String, anchors: List<Pair<String, List<String>>>) {
        for ((className, strings) in anchors) {
            val source = readDecompiledClass(ime, className)
            for (needle in strings) {
                assertTrue(
                    "[$ime] Missing DexKit anchor string '$needle' in $className",
                    source.contains(needle)
                )
            }
        }
    }

    private fun assertAllSourceClassLiteralsExist(ime: String, relativeSourcePath: String) {
        val ktFile = File(
            codesourceRoot,
            "app/src/main/java/com/scluzlep/miimeprivacy/xposed/$relativeSourcePath"
        )
        assertTrue("Missing source file ${ktFile.path}", ktFile.isFile)
        val content = stripKotlinComments(ktFile.readText()).replace("\\$", "$")

        val classLiteralPatterns = listOf(
            Regex("""(?:loadClassOrNull|addInterface)\s*\(\s*"([a-zA-Z0-9_$.]+)""""),
            Regex("""fastPathCandidates\s*=\s*listOf\s*\(\s*"([a-zA-Z0-9_$.]+)""""),
            Regex("""className\s*=\s*"([a-zA-Z0-9_$.]+)""""),
            Regex("""const val (?:CLASS_[A-Z0-9_]+|POPUP_CLASS|INFO_CLASS)\s*=\s*"([a-zA-Z0-9_$.]+)"""")
        )
        val discovered = linkedSetOf<String>()
        for (pattern in classLiteralPatterns) {
            for (match in pattern.findAll(content)) {
                val cls = match.groupValues[1]
                if (
                    !cls.startsWith("java.") &&
                    !cls.startsWith("javax.") &&
                    !cls.startsWith("android.") &&
                    !cls.startsWith("androidx.") &&
                    !cls.startsWith("kotlin.")
                ) {
                    discovered.add(cls)
                }
            }
        }
        assertTrue("Expected non-empty class literal extraction for $ime", discovered.isNotEmpty())
        for (cls in discovered) {
            assertClassExists(ime, cls)
        }
    }

    private fun resolveJadxClassName(ime: String, className: String): String =
        if (ime == "baidu" && className.startsWith("com.baidu.util.")) {
            // JADX decompiler renames DEX package `com.baidu.util` -> `com.baidu.res`
            "com.baidu.res." + className.removePrefix("com.baidu.util.")
        } else {
            className
        }

    private fun assertClassExists(ime: String, className: String): String {
        val jadxClassName = resolveJadxClassName(ime, className)
        val directFile = File(decompiledRoot, "$ime/sources/${jadxClassName.replace('.', '/')}.java")
        if (directFile.isFile) {
            return directFile.readText()
        }
        val outer = jadxClassName.substringBefore('$')
        val candidateFile = File(decompiledRoot, "$ime/sources/${outer.replace('.', '/')}.java")
        if (candidateFile.isFile) {
            val text = candidateFile.readText()
            if (jadxClassName.contains('$')) {
                val innerSimple = jadxClassName.substringAfterLast('$')
                // Anonymous / JADX-hoisted inner classes are referenced or declared inside outer
                assertTrue(
                    "[$ime] Inner class $jadxClassName not found in ${candidateFile.path}",
                    text.contains(innerSimple)
                )
            }
            return text
        }
        // D8 synthetic lambda classes (e.g. com.baidu.input.clipboard.b) are inlined into enclosing JADX file
        val packageDir = File(decompiledRoot, "$ime/sources/${outer.substringBeforeLast('.', "").replace('.', '/')}")
        if (packageDir.isDirectory) {
            val marker = "from class: $jadxClassName"
            val inlined = packageDir.listFiles { f -> f.isFile && f.extension == "java" }
                ?.firstOrNull { it.readText().contains(marker) }
            if (inlined != null) {
                return inlined.readText()
            }
        }
        throw AssertionError("[$ime] Missing decompiled class $jadxClassName (checked ${directFile.path} and ${candidateFile.path})")
    }

    private fun readDecompiledClass(ime: String, className: String): String {
        val jadxClassName = resolveJadxClassName(ime, className)
        val directFile = File(decompiledRoot, "$ime/sources/${jadxClassName.replace('.', '/')}.java")
        if (directFile.isFile) {
            return directFile.readText()
        }
        return assertClassExists(ime, jadxClassName)
    }

    private fun stripKotlinComments(source: String): String {
        val noBlock = source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.replace(Regex("""(?m)^\s*//[^\n]*"""), "")
    }
}
