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
            ClassContract(XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY, listOf("onActivityResult", "requestCtaDialog", "onCtaAccepted")),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_SDK, listOf(XiaoAiAnchors.METHOD_ONETRACK_SET_NET) + XiaoAiAnchors.ONETRACK_SDK_TRACK_METHODS),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_HELPER, listOf("b", "e", "f")),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_IMP, listOf("a")),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_SYSTEM_IMP),
            ClassContract(XiaoAiAnchors.CLASS_ONETRACK_SERVICE_CONN),
            ClassContract(XiaoAiAnchors.CLASS_SESSION_COLLECTOR, listOf("a", "b", "c", "d", "e"), listOf(XiaoAiAnchors.FIELD_SESSION_HOLDER)),
            ClassContract(XiaoAiAnchors.CLASS_SESSION_HOLDER, listOf("a"), listOf(XiaoAiAnchors.FIELD_SESSION_PASSWORD)),
            ClassContract(XiaoAiAnchors.CLASS_SESSION_CACHE, listOf("b")),
            ClassContract(XiaoAiAnchors.CLASS_AGENT_CONNECTION, listOf("a")),
            ClassContract(XiaoAiAnchors.CLASS_VOICEASSIST_LAUNCH, listOf("L")),
            ClassContract(XiaoAiAnchors.CLASS_MICLAW_MANAGER, listOf("q")),
            ClassContract(XiaoAiAnchors.CLASS_MICLAW_SESSION, listOf("b")),
            ClassContract(XiaoAiAnchors.CLASS_MICLAW_FAILED),
            ClassContract(XiaoAiAnchors.CLASS_SCREENSHOT_CAPTURE, listOf("c")),
            ClassContract(XiaoAiAnchors.CLASS_TRANSLATE_ENGINE, listOf("f")),
            ClassContract(XiaoAiAnchors.CLASS_TRANSLATE_START, fields = listOf("c")),
            ClassContract(XiaoAiAnchors.CLASS_TRANSLATE_IDLE, fields = listOf("a")),
            ClassContract(XiaoAiAnchors.CLASS_TRANSLATE_ERROR),
            ClassContract(XiaoAiAnchors.CLASS_SPEECH_ASR_MANAGER, listOf("b")),
            ClassContract(XiaoAiAnchors.CLASS_TAIYI_SERVICE, listOf("d")),
            ClassContract(XiaoAiAnchors.CLASS_TAIYI_MANAGER, listOf("e")),
            ClassContract(XiaoAiAnchors.CLASS_TAIYI_REQUEST),
            ClassContract(XiaoAiAnchors.CLASS_TAIYI_RESPONSE, listOf("create")),
            ClassContract(XiaoAiAnchors.CLASS_CLIPBOARD_MONITOR, listOf("c")),
            ClassContract(XiaoAiAnchors.CLASS_HOTWORD_CLIENT, listOf("a", "b")),
            ClassContract(XiaoAiAnchors.CLASS_HOTWORD_EDIT_TRACKER, listOf("a", "f", "h"))
        )
        assertClassContracts(ime, classContracts)

        val anchorContracts = listOf(
            XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY to (
                XiaoAiAnchors.CTA_ACTIVITY_STRINGS +
                    XiaoAiAnchors.CTA_DIALOG_STRINGS +
                    XiaoAiAnchors.CTA_CONSENTED_STRINGS +
                    XiaoAiAnchors.CTA_RESULT_STRINGS
                ),
            XiaoAiAnchors.CLASS_ONETRACK_HELPER to (XiaoAiAnchors.ONETRACK_HELPER_STRINGS + XiaoAiAnchors.ONETRACK_HELPER_ERROR_STRINGS),
            XiaoAiAnchors.CLASS_ONETRACK_IMP to XiaoAiAnchors.ONETRACK_IMP_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_SYSTEM_IMP to XiaoAiAnchors.ONETRACK_SYSTEM_IMP_STRINGS,
            XiaoAiAnchors.CLASS_ONETRACK_SERVICE_CONN to XiaoAiAnchors.ONETRACK_SERVICE_CONN_STRINGS,
            XiaoAiAnchors.CLASS_SESSION_COLLECTOR to (
                XiaoAiAnchors.SESSION_COLLECTOR_STRINGS +
                    XiaoAiAnchors.SESSION_ACTION_BACK_STRINGS +
                    XiaoAiAnchors.SESSION_ACTION_DEL_STRINGS
                ),
            XiaoAiAnchors.CLASS_AGENT_CONNECTION to XiaoAiAnchors.AGENT_CONNECTION_STRINGS,
            XiaoAiAnchors.CLASS_VOICEASSIST_LAUNCH to XiaoAiAnchors.VOICEASSIST_ASSIST_STRINGS,
            XiaoAiAnchors.CLASS_MICLAW_MANAGER to XiaoAiAnchors.MICLAW_MANAGER_STRINGS,
            XiaoAiAnchors.CLASS_SCREENSHOT_CAPTURE to XiaoAiAnchors.SCREENSHOT_CAPTURE_STRINGS,
            XiaoAiAnchors.CLASS_TRANSLATE_ENGINE to XiaoAiAnchors.TRANSLATE_ENGINE_STRINGS,
            XiaoAiAnchors.CLASS_SPEECH_ASR_MANAGER to XiaoAiAnchors.SPEECH_ASR_STRINGS,
            XiaoAiAnchors.CLASS_TAIYI_SERVICE to (XiaoAiAnchors.TAIYI_SERVICE_STRINGS + XiaoAiAnchors.TAIYI_CONNECT_STRINGS),
            XiaoAiAnchors.CLASS_TAIYI_MANAGER to XiaoAiAnchors.TAIYI_MANAGER_STRINGS,
            XiaoAiAnchors.CLASS_CLIPBOARD_MONITOR to XiaoAiAnchors.CLIPBOARD_MONITOR_STRINGS,
            XiaoAiAnchors.CLASS_HOTWORD_CLIENT to XiaoAiAnchors.HOTWORD_CLIENT_STRINGS,
            XiaoAiAnchors.CLASS_HOTWORD_EDIT_TRACKER to XiaoAiAnchors.HOTWORD_TRACKER_STRINGS
        )
        assertAnchorStrings(ime, anchorContracts)
        assertAllSourceClassLiteralsExist(ime, "targets/XiaoAiHookPack.kt")
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
            ClassContract("com.sohu.inputmethod.settings.internet.alive.e", listOf("a", "b")),
            ClassContract("com.sohu.inputmethod.sogou.privacy.a", listOf("a")),
            ClassContract("com.sohu.inputmethod.sogou.launch.d\$d", listOf("b", "c")),
            ClassContract("com.sogou.networking.reporter.okhttp.a"),
            ClassContract("com.sogou.networking.reporter.a", listOf("j")),
            ClassContract("com.sogou.networking.f"),
            ClassContract("com.sogou.networking.d"),
            ClassContract("okhttp3.z"),
            ClassContract("com.sohu.inputmethod.cloud.CloudSettings", listOf("isCloudSinkOn", "isCloudSinkAndSendWhenDown")),
            ClassContract(
                "com.sogou.inputmethod.cloud.api.ctrl.CloudMgr",
                listOf("O0", "m", "P0", "k", "p", "o", "h1", "V0", "v"),
                listOf("z", "m", "x")
            ),
            ClassContract("com.sohu.inputmethod.sogou.j2", fields = listOf("C0")),
            ClassContract("com.sohu.inputmethod.sogou.MainImeServiceDel", listOf("getInstance", "isInputViewShown")),
            ClassContract("com.sohu.inputmethod.input.CloudWebSocketSender", listOf("g")),
            ClassContract("com.sohu.inputmethod.input.InputRequestsSender", listOf("b")),
            ClassContract("com.sogou.inputmethod.sogou.cloud.data.BaseInputRequestInfo", listOf("getRequestType", "onDownloadFail")),
            ClassContract("com.sohu.inputmethod.sogou.cloud.b", listOf("h")),
            ClassContract("com.sohu.inputmethod.sogou.cloud.a", listOf("i")),
            ClassContract("com.sohu.inputmethod.cloud.CloudRequestInfo"),
            ClassContract(
                "com.sohu.inputmethod.sogou.cloud.nano.CloudAssocData",
                fields = listOf(
                    "dialogue", "env", "keypositions", "usePosAdjust", "stdata", "stdataBack",
                    "device", "geo", "imei", "imsi", "h", "input", "data", "syllablefilterinfo", "clientcands"
                )
            ),
            ClassContract("sogou.pingback.f", listOf("d", "e", "f", "i", "j")),
            ClassContract("com.sohu.inputmethod.sogou.x2", listOf("d", "f", "g", "i", "j", "k", "l", "m")),
            ClassContract("com.sogou.lib.whitedog.step.f", listOf("r", "g", "x")),
            ClassContract("com.sogou.lib.whitedog.step.InputCollectorCommons\$InputCollectorPrepareStep"),
            ClassContract("com.sohu.inputmethod.settings.internet.k", listOf("onWork")),
            ClassContract("com.sogou.imskit.feature.lib.log.sync.LogDiagSyncLifecycle", listOf("onStartInput", "onStartInputView")),
            ClassContract("com.sogou.imskit.feature.lib.log.f", listOf("c", "g")),
            ClassContract("com.sogou.imskit.feature.lib.log.initservice.LogInitServiceImpl\$a", listOf("a")),
            ClassContract("com.sogou.scrashly.a", listOf("d", "f", "g")),
            ClassContract("com.sohu.inputmethod.beaconbridge.c", listOf("e", "f")),
            ClassContract("com.sohu.inputmethod.sogou.bigdata.internet.a", listOf("g", "i", "j", "k")),
            ClassContract("com.sohu.inputmethod.settings.status.a", listOf("h", "i")),
            ClassContract("com.sogou.bu.basic.settings.SettingManager", listOf("t4")),
            ClassContract("com.sohu.inputmethod.sogou.mutualdata.MutualDataManager", listOf("q", "m", "w", "C", "D", "z", "j", "F", "G", "A", "B")),
            ClassContract("com.sohu.inputmethod.sogou.bigdata.c", listOf("D", "R")),
            ClassContract("com.sohu.inputmethod.clipboard.x", listOf("onPrimaryClipChanged")),
            ClassContract("com.sohu.inputmethod.clipboard.p", listOf("T")),
            ClassContract("com.sohu.inputmethod.asyncpenetrate.c", listOf("m", "n")),
            ClassContract("com.sohu.inputmethod.clipboard.BusinessQuickPortal.BusinessQuickPortalManager", listOf("x")),
            ClassContract("com.sohu.inputmethod.clipboard.KouLingClipBoardManager", listOf("n", "o", "p")),
            ClassContract("com.sohu.inputmethod.clipboard.ClipboardLogHelper", listOf("r", "q")),
            ClassContract("com.sohu.inputmethod.clipboard.vpaclipboard.VpaClipboardManager", listOf("showVpaClipboard")),
            ClassContract("com.sohu.inputmethod.settings.b0", listOf("h")),
            ClassContract("com.sohu.util.u", listOf("a", "b")),
            ClassContract("com.sohu.inputmethod.settings.internet.v", listOf("onWork")),
            ClassContract("com.sogou.hj.RequestWork", listOf("o")),
            ClassContract("com.sogou.http.okhttp.q"),
            ClassContract("com.sogou.hj.task.TaskExecutor\$a", listOf("run")),
            ClassContract("com.sogou.hj.task.a", listOf("a", "b")),
            ClassContract("com.tencent.msdk.dns.MSDKDnsResolver", listOf("init", "WGSetDnsOpenId"))
        )
        assertClassContracts(ime, classContracts)

        val anchorContracts = listOf(
            "com.sogou.alive.strategy.a" to listOf("special_alive"),
            "com.sohu.inputmethod.settings.internet.alive.e" to listOf("/v1/alive/install"),
            "com.sohu.inputmethod.sogou.cloud.b" to listOf("#%#", "&nbsp", "UTF-16LE"),
            "com.sohu.inputmethod.sogou.cloud.a" to listOf("cloud_assoc_before_context", "#%#", "UTF-16LE"),
            "sogou.pingback.f" to listOf("http://kpi2.android.shouji.sogou.com/kpi2.gif")
        )
        assertAnchorStrings(ime, anchorContracts)
        assertAllSourceClassLiteralsExist(ime, "targets/SogouHookPack.kt")
    }

    @Test
    fun baiduFastPathSymbolsAndDexKitAnchorsExistInDecompiledV7108() {
        val ime = "baidu"
        val classContracts = listOf(
            ClassContract("e20.g", listOf("a")),
            ClassContract("tn0.e", listOf("isPrivacyAgreed")),
            ClassContract("com.baidu.ubc.UBCHelper", listOf("isAgreePrivacy")),
            ClassContract("dj0.y", listOf("h", "A")),
            ClassContract("sj0.a", listOf("b"), listOf("b")),
            ClassContract("ju.a", listOf("k0")),
            ClassContract("com.baidu.ubc.BehaviorUploader", listOf("obUploadDataRequest", "uploadDataRequest")),
            ClassContract("com.baidu.mobstat.LogSender", listOf("saveLogData")),
            ClassContract("x00.b", listOf("c")),
            ClassContract("com.baidu.iptcore.net.IptCloudStream", listOf("send")),
            ClassContract("com.baidu.input.ime.cloudinput.CloudInfo", listOf("getSugPackageInfo", "get_app_name", "get_city", "get_json_buf")),
            ClassContract("com.baidu.input.ime.voicerecognize.voicetrace.a", listOf("c", "d")),
            ClassContract("com.baidu.input.ime.voicerecognize.voicetrace.TraceBean"),
            ClassContract("com.baidu.input.aicard.impl.uploader.b", listOf("b")),
            ClassContract("com.baidu.input.aicard.impl.uploader.a", listOf("g", "h")),
            ClassContract("dj0.l", fields = listOf("I1")),
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
            ClassContract("com.baidu.res.ContactsUtils", listOf("getContactsByContent")),
            ClassContract("rj0.b", listOf("b"), listOf("d")),
            ClassContract("com.baidu.input.ime.newcore.ImeEnvCallback", listOf("findApp", "hasInstallApp")),
            ClassContract("l11.b", listOf("f1")),
            ClassContract("js.a", listOf("h"), listOf("b")),
            ClassContract("fg.w", listOf("b", "c"), listOf("a")),
            ClassContract("com.baidu.input.pl.impl.a", listOf("a")),
            ClassContract("com.baidu.webkit.internal.daemon.HttpDnsCache", listOf("tryToUpdateHttpDnsCache", "tryToUpdateHttpDnsCacheStaticIP")),
            ClassContract("com.baidu.webkit.internal.daemon.HttpDnsCacheForHost", listOf("tryToUpdateHttpDnsCacheHost")),
            ClassContract("com.baidu.webkit.internal.blink.WebSettingsGlobalBlink", listOf("getHttpDnsUpdateEnabled")),
            ClassContract("com.baidu.turbonet.net.TurbonetEngine\$Builder", listOf("enableBaiduDns")),
            ClassContract("com.baidu.turbonet.net.TurbonetConfig", listOf("enableBaiduDns"))
        )
        assertClassContracts(ime, classContracts)

        // Synthetic lambda com.baidu.input.clipboard.b is inlined inside ClipboardGlobal$clipboardListener$2.java
        val clipListenerSrc = readDecompiledClass(ime, "com.baidu.input.clipboard.ClipboardGlobal\$clipboardListener\$2")
        assertTrue(
            "Expected D8 synthetic listener com.baidu.input.clipboard.b inside ClipboardGlobal\$clipboardListener\$2",
            clipListenerSrc.contains("com.baidu.input.clipboard.b")
        )

        val anchorContracts = listOf(
            "e20.g" to listOf("PrivacyDialogUtils.java"),
            "dj0.y" to listOf("option_log", "Option is empty, reset default setting"),
            "sj0.a" to listOf("key_upload_kp_data_time"),
            "ju.a" to listOf("sug_mi_statistic"),
            "x00.b" to listOf("sug_info", "close -> sugSwitch close "),
            "com.baidu.iptcore.net.IptCloudStream" to listOf("cfile", "log_type=7"),
            "com.baidu.input.ime.voicerecognize.voicetrace.a" to listOf("wufeiyang", "traceBean : "),
            "com.baidu.input.aicard.impl.uploader.a" to listOf("wenXinData"),
            "dj0.l" to listOf("isNeedShowFlowDialog = ", ",isPrivacyInputMode"),
            "com.baidu.input.clipboard.ClipboardGlobal\$clipboardListener\$2" to listOf("ClipboardGlobal", "privacy not agreed"),
            "q30.e" to listOf("android.permission.READ_SMS", "count_contsign"),
            "q30.d" to listOf("content://sms/inbox-insert"),
            "q30.f" to listOf("content://sms/inbox"),
            "t40.b" to listOf("ContactCustomManager", "https://upl.baidu.com/words/add"),
            "js.a" to listOf("DataCollectorHandler", "config file download success"),
            "com.baidu.input.pl.impl.a" to listOf("ime_pls_files", "PlManager")
        )
        assertAnchorStrings(ime, anchorContracts)
        assertAllSourceClassLiteralsExist(ime, "targets/BaiduHookPack.kt")
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
            Regex("""const val CLASS_[A-Z0-9_]+\s*=\s*"([a-zA-Z0-9_$.]+)"""")
        )
        val discovered = linkedSetOf<String>()
        for (pattern in classLiteralPatterns) {
            for (match in pattern.findAll(content)) {
                val cls = match.groupValues[1]
                if (!cls.startsWith("java.") && !cls.startsWith("android.")) {
                    discovered.add(cls)
                }
            }
        }
        assertTrue("Expected non-empty class literal extraction for $ime", discovered.isNotEmpty())
        for (cls in discovered) {
            assertClassExists(ime, cls)
        }
    }

    private fun assertClassExists(ime: String, className: String): String {
        val directFile = File(decompiledRoot, "$ime/sources/${className.replace('.', '/')}.java")
        if (directFile.isFile) {
            return directFile.readText()
        }
        val outer = className.substringBefore('$')
        val candidateFile = File(decompiledRoot, "$ime/sources/${outer.replace('.', '/')}.java")
        if (candidateFile.isFile) {
            val text = candidateFile.readText()
            if (className.contains('$')) {
                val innerSimple = className.substringAfterLast('$')
                // Anonymous / JADX-hoisted inner classes are referenced or declared inside outer
                assertTrue(
                    "[$ime] Inner class $className not found in ${candidateFile.path}",
                    text.contains(innerSimple)
                )
            }
            return text
        }
        // D8 synthetic lambda classes (e.g. com.baidu.input.clipboard.b) are inlined into enclosing JADX file
        val packageDir = File(decompiledRoot, "$ime/sources/${outer.substringBeforeLast('.', "").replace('.', '/')}")
        if (packageDir.isDirectory) {
            val marker = "from class: $className"
            val inlined = packageDir.listFiles { f -> f.isFile && f.extension == "java" }
                ?.firstOrNull { it.readText().contains(marker) }
            if (inlined != null) {
                return inlined.readText()
            }
        }
        throw AssertionError("[$ime] Missing decompiled class $className (checked ${directFile.path} and ${candidateFile.path})")
    }

    private fun readDecompiledClass(ime: String, className: String): String {
        val directFile = File(decompiledRoot, "$ime/sources/${className.replace('.', '/')}.java")
        if (directFile.isFile) {
            return directFile.readText()
        }
        return assertClassExists(ime, className)
    }

    private fun stripKotlinComments(source: String): String {
        val noBlock = source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        return noBlock.replace(Regex("""//[^\n]*"""), "")
    }
}
