package com.scluzlep.miimeprivacy.model

import com.scluzlep.miimeprivacy.config.PrivacyConfig

enum class RuleCategory {
    PRIVACY_GATE,
    DEVICE_ID,
    TELEMETRY,
    CLOUD_PRIVACY,
    CONTEXT_STRIP,
    CLIPBOARD,
    SCAN_AND_LOCATION,
    DYNAMIC_AND_NET,
    UI_ENHANCE
}

/**
 * Static specification of a privacy protection, UI enhancement, or overlay rule for one target IME.
 * Used by the module UI, ConfigBridge, and runtime diagnostics across all 74 v2.0 canonical rules.
 */
data class HookRuleSpec(
    val ruleId: String,
    val target: ImeTarget,
    val title: String,
    val description: String,
    val category: RuleCategory,
    val defaultEnabled: Boolean = true,
    val isOverlayPill: Boolean = false,
    val critical: Boolean = false,
    val legacySwitchKey: String? = null
) {
    val targetPackage: String
        get() = target.packageName

    val configSwitchKey: String?
        get() = legacySwitchKey
}

object HookRuleCatalog {

    // -------------------------------------------------------------------------
    // Sogou canonical v2.0 rule IDs (30)
    // -------------------------------------------------------------------------
    const val RULE_SOGOU_GATE_ALIVE_HEARTBEAT = "sogou.gate.alive_heartbeat"
    const val RULE_SOGOU_GATE_PRECONSENT_GUARD = "sogou.gate.preconsent_guard"
    const val RULE_SOGOU_ID_OAID_TGPA = "sogou.id.oaid_tgpa"
    const val RULE_SOGOU_ID_HARDWARE_AND_SENSOR = "sogou.id.hardware_and_sensor"
    const val RULE_SOGOU_ID_ANDROID_ID_AND_UUID = "sogou.id.android_id_and_uuid"
    const val RULE_SOGOU_TELEMETRY_PINGBACK = "sogou.telemetry.pingback"
    const val RULE_SOGOU_TELEMETRY_WHITEDOG_LOGDIAG = "sogou.telemetry.whitedog_logdiag"
    const val RULE_SOGOU_TELEMETRY_BEACON_QIMEI = "sogou.telemetry.beacon_qimei"
    const val RULE_SOGOU_TELEMETRY_THIRDPARTY_ADS_QM = "sogou.telemetry.thirdparty_ads_qm"
    const val RULE_SOGOU_TELEMETRY_CRASH_APM = "sogou.telemetry.crash_apm"
    const val RULE_SOGOU_CLOUD_GATEWAY_AND_PIGGYBACK = "sogou.cloud.gateway_and_piggyback"
    const val RULE_SOGOU_CLOUD_PAYLOAD_DEVICE_DIALOGUE = "sogou.cloud.payload_device_dialogue"
    const val RULE_SOGOU_CLOUD_WANGZAI_VPA = "sogou.cloud.wangzai_vpa"
    const val RULE_SOGOU_CLOUD_VOICE_PRIVACY = "sogou.cloud.voice_privacy"
    const val RULE_SOGOU_CONTEXT_CURSOR_TEXT = "sogou.context.cursor_text"
    const val RULE_SOGOU_CONTEXT_HOST_PACKAGE = "sogou.context.host_package"
    const val RULE_SOGOU_CONTEXT_TOUCH_POSITIONS = "sogou.context.touch_positions"
    const val RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA = "sogou.context.mutual_bigdata"
    const val RULE_SOGOU_CLIP_SNIFFER_AND_KOULING = "sogou.clip.sniffer_and_kouling"
    const val RULE_SOGOU_CLIP_AUTO_TRANSLATE_AND_JS = "sogou.clip.auto_translate_and_js"
    const val RULE_SOGOU_CLIP_DB_HISTORY_SAVE = "sogou.clip.db_history_save"
    const val RULE_SOGOU_CLIP_STRICT_BLOCK_LISTENER = "sogou.clip.strict_block_listener"
    const val RULE_SOGOU_SCAN_DEVICEINFO_HARVESTER = "sogou.scan.deviceinfo_harvester"
    const val RULE_SOGOU_SCAN_APP_LIST = "sogou.scan.app_list"
    const val RULE_SOGOU_SCAN_CONTACTS_UPLOAD_AND_DICT = "sogou.scan.contacts_upload_and_dict"
    const val RULE_SOGOU_SCAN_LOCATION_WIFI = "sogou.scan.location_wifi"
    const val RULE_SOGOU_NET_DYNAMIC_DEX_AND_JS = "sogou.net.dynamic_dex_and_js"
    const val RULE_SOGOU_NET_HTTPDNS = "sogou.net.httpdns"
    const val RULE_SOGOU_UI_DUAL_SKIN_EMOJI = "sogou.ui.dual_skin_emoji"
    const val RULE_SOGOU_OVERLAY_PILL = "sogou.overlay_pill"

    // -------------------------------------------------------------------------
    // Baidu canonical v2.0 rule IDs (24)
    // -------------------------------------------------------------------------
    const val RULE_BAIDU_GATE_CONSENT_AND_1839 = "baidu.gate.consent_and_1839"
    const val RULE_BAIDU_GATE_NOTICENTER_LOCK = "baidu.gate.noticenter_lock"
    const val RULE_BAIDU_ID_OAID_AND_HELIOS = "baidu.id.oaid_and_helios"
    const val RULE_BAIDU_ID_CUID_ANDROIDID_SOFIRE = "baidu.id.cuid_androidid_sofire"
    const val RULE_BAIDU_TELEMETRY_REALTIME_STATS = "baidu.telemetry.realtime_stats"
    const val RULE_BAIDU_TELEMETRY_PERIODIC_UBC_MOBSTAT = "baidu.telemetry.periodic_ubc_mobstat"
    const val RULE_BAIDU_TELEMETRY_VOICE_AND_ADS = "baidu.telemetry.voice_and_ads"
    const val RULE_BAIDU_TELEMETRY_CRASH_DUMP = "baidu.telemetry.crash_dump"
    const val RULE_BAIDU_CLOUD_STREAM_GATE = "baidu.cloud.stream_gate"
    const val RULE_BAIDU_CLOUD_PAYLOAD_SANITIZE = "baidu.cloud.payload_sanitize"
    const val RULE_BAIDU_CONTEXT_CURSOR_AND_AI = "baidu.context.cursor_and_ai"
    const val RULE_BAIDU_CONTEXT_HOST_PACKAGE = "baidu.context.host_package"
    const val RULE_BAIDU_CLIP_TOKEN_UPLOAD_AND_JS = "baidu.clip.token_upload_and_js"
    const val RULE_BAIDU_CLIP_BACKGROUND_AND_DB = "baidu.clip.background_and_db"
    const val RULE_BAIDU_CLIP_STRICT_BLOCK_READERS = "baidu.clip.strict_block_readers"
    const val RULE_BAIDU_SCAN_CONTACTS = "baidu.scan.contacts"
    const val RULE_BAIDU_SCAN_APP_LIST = "baidu.scan.app_list"
    const val RULE_BAIDU_SCAN_SMS_OTP_LOCAL = "baidu.scan.sms_otp_local"
    const val RULE_BAIDU_SCAN_LOCATION_WIFI_CELL = "baidu.scan.location_wifi_cell"
    const val RULE_BAIDU_NET_SALVAGE_LOGS = "baidu.net.salvage_logs"
    const val RULE_BAIDU_NET_DYNAMIC_DEX_AND_JS = "baidu.net.dynamic_dex_and_js"
    const val RULE_BAIDU_NET_TLS_HARDENING = "baidu.net.tls_hardening"
    const val RULE_BAIDU_NET_HTTPDNS = "baidu.net.httpdns"
    const val RULE_BAIDU_OVERLAY_PILL = "baidu.overlay_pill"

    // -------------------------------------------------------------------------
    // XiaoAi canonical v2.0 rule IDs (20)
    // -------------------------------------------------------------------------
    const val RULE_XIAOAI_GATE_CTA_FAIL_OPEN = "xiaoai.gate.cta_fail_open"
    const val RULE_XIAOAI_GATE_UNLOCK_DEGRADE_BYPASS = "xiaoai.gate.unlock_degrade_bypass"
    const val RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT = "xiaoai.id.antihook_and_sec_report"
    const val RULE_XIAOAI_ID_OAID_INSID_PHONENUM = "xiaoai.id.oaid_insid_phonenum"
    const val RULE_XIAOAI_TELEMETRY_ONETRACK = "xiaoai.telemetry.onetrack"
    const val RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION = "xiaoai.telemetry.password_and_session"
    const val RULE_XIAOAI_TELEMETRY_TAIYI_AND_JANK = "xiaoai.telemetry.taiyi_and_jank"
    const val RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH = "xiaoai.telemetry.bugreport_and_xcrash"
    const val RULE_XIAOAI_CLOUD_MICLAW_LLM = "xiaoai.cloud.miclaw_llm"
    const val RULE_XIAOAI_CLOUD_SCREEN_CAPTURE = "xiaoai.cloud.screen_capture"
    const val RULE_XIAOAI_CLOUD_SPEECH_ASR = "xiaoai.cloud.speech_asr"
    const val RULE_XIAOAI_CLOUD_TRANSLATE_AND_TAIYI = "xiaoai.cloud.translate_and_taiyi"
    const val RULE_XIAOAI_CLOUD_DICTPACK_SYNC = "xiaoai.cloud.dictpack_sync"
    const val RULE_XIAOAI_CLOUD_VOICEASSIST_LAUNCH = "xiaoai.cloud.voiceassist_launch"
    const val RULE_XIAOAI_CONTEXT_HOTWORD_AICR = "xiaoai.context.hotword_aicr"
    const val RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG = "xiaoai.context.top_activity_and_pkg"
    const val RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP = "xiaoai.context.cursor_prompt_strip"
    const val RULE_XIAOAI_CLIP_BACKGROUND_GUARD = "xiaoai.clip.background_guard"
    const val RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL = "xiaoai.net.system_cloud_control"
    const val RULE_XIAOAI_OVERLAY_PILL = "xiaoai.overlay_pill"

    // -------------------------------------------------------------------------
    // Legacy v1.x rule ID aliases (preserved so Stage 2 packs compile cleanly until migrated)
    // -------------------------------------------------------------------------
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_SOGOU_PRIVACY_GATE = "sogou.privacy_gate"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_SOGOU_TELEMETRY = "sogou.telemetry_sinks"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_SOGOU_CLOUD_SANITIZE = "sogou.cloud_sanitize"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_SOGOU_MUTUAL_DATA = "sogou.mutual_data"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_SOGOU_CLIPBOARD = "sogou.clipboard"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_SOGOU_CONTACTS_APP_SCAN = "sogou.contacts_app_scan"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_SOGOU_DYNAMIC_DEX = "sogou.dynamic_dex"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_SOGOU_HTTPDNS = RULE_SOGOU_NET_HTTPDNS

    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_BAIDU_PRIVACY_GATE = "baidu.privacy_gate"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_BAIDU_TELEMETRY = "baidu.telemetry_sinks"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_BAIDU_CLOUD_AND_AI = "baidu.cloud_and_ai_sanitize"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_BAIDU_CLIPBOARD = "baidu.clipboard"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_BAIDU_CONTACTS_SMS_SCAN = "baidu.contacts_sms_scan"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_BAIDU_SALVAGE_DCL = "baidu.remote_salvage_dcl"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_BAIDU_HTTPDNS = RULE_BAIDU_NET_HTTPDNS

    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_XIAOAI_CTA_GATE = RULE_XIAOAI_GATE_CTA_FAIL_OPEN
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_XIAOAI_ONETRACK = "xiaoai.onetrack_and_password"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_XIAOAI_AI_CLOUD_IPC = "xiaoai.ai_cloud_and_ipc"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_XIAOAI_CLIPBOARD = "xiaoai.clipboard"
    @Deprecated("Legacy v1.x rule ID; migrate to granular v2.0 rule IDs in Stage 2")
    const val RULE_XIAOAI_HOTWORD_TRACKER = "xiaoai.hotword_tracker"

    private val catalog: List<HookRuleSpec> = listOf(
        // =====================================================================
        // Sogou (30 rules)
        // =====================================================================
        HookRuleSpec(
            ruleId = RULE_SOGOU_GATE_ALIVE_HEARTBEAT,
            target = ImeTarget.SOGOU,
            title = "保活心跳门控",
            description = "未同意隐私政策前禁止后台保活与心跳发包",
            category = RuleCategory.PRIVACY_GATE,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_GATE_PRECONSENT_GUARD,
            target = ImeTarget.SOGOU,
            title = "预同意启动拦截",
            description = "未授权前阻断高危组件初始化与静默联网",
            category = RuleCategory.PRIVACY_GATE,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_ID_OAID_TGPA,
            target = ImeTarget.SOGOU,
            title = "OAID 标识屏蔽",
            description = "阻断读取匿名设备标识与游戏加速标识",
            category = RuleCategory.DEVICE_ID,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_ID_HARDWARE_AND_SENSOR,
            target = ImeTarget.SOGOU,
            title = "硬件传感器屏蔽",
            description = "拦截设备硬件序列号与传感器指纹采集",
            category = RuleCategory.DEVICE_ID,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_ID_ANDROID_ID_AND_UUID,
            target = ImeTarget.SOGOU,
            title = "设备标识匿名化",
            description = "屏蔽 AndroidID 与本地随机标识追踪",
            category = RuleCategory.DEVICE_ID,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_TELEMETRY_PINGBACK,
            target = ImeTarget.SOGOU,
            title = "行为打点拦截",
            description = "拦截 Pingback 行为统计与事件上报",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_TELEMETRY_WHITEDOG_LOGDIAG,
            target = ImeTarget.SOGOU,
            title = "诊断日志上报拦截",
            description = "阻断 Whitedog 诊断日志收集与回传",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_TELEMETRY_BEACON_QIMEI,
            target = ImeTarget.SOGOU,
            title = "灯塔统计拦截",
            description = "拦截腾讯灯塔 QIMEI 生成与数据上报",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_TELEMETRY_THIRDPARTY_ADS_QM,
            target = ImeTarget.SOGOU,
            title = "三方广告 SDK 拦截",
            description = "阻断第三方广告监测与归因统计发包",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_TELEMETRY_CRASH_APM,
            target = ImeTarget.SOGOU,
            title = "崩溃性能上报拦截",
            description = "禁止后台自动上传崩溃堆栈与性能监控",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLOUD_GATEWAY_AND_PIGGYBACK,
            target = ImeTarget.SOGOU,
            title = "云网关夹带拦截",
            description = "剥离云网关夹带遥测载荷与静默回传",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLOUD_PAYLOAD_DEVICE_DIALOGUE,
            target = ImeTarget.SOGOU,
            title = "云输入载荷脱敏",
            description = "清除云请求中的设备参数与历史对话缓存",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLOUD_WANGZAI_VPA,
            target = ImeTarget.SOGOU,
            title = "旺仔助手脱敏",
            description = "剥离旺仔智能助手请求的隐私上下文字段",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLOUD_VOICE_PRIVACY,
            target = ImeTarget.SOGOU,
            title = "语音输入脱敏",
            description = "清除语音识别上传中的设备与环境标识",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CONTEXT_CURSOR_TEXT,
            target = ImeTarget.SOGOU,
            title = "隐藏光标前后文字",
            description = "云请求仅传当前拼音，不上传编辑框上下文",
            category = RuleCategory.CONTEXT_STRIP,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_STRIP_CURSOR_CONTEXT
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CONTEXT_HOST_PACKAGE,
            target = ImeTarget.SOGOU,
            title = "隐藏前台应用包名",
            description = "不向搜狗云端接口传递当前宿主应用包名",
            category = RuleCategory.CONTEXT_STRIP,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_MASK_HOST_PACKAGE
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CONTEXT_TOUCH_POSITIONS,
            target = ImeTarget.SOGOU,
            title = "隐藏按键坐标轨迹",
            description = "清除击键坐标与滑动轨迹等生物特征字段",
            category = RuleCategory.CONTEXT_STRIP,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_STRIP_TOUCH_POSITIONS
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA,
            target = ImeTarget.SOGOU,
            title = "互助大数据拦截",
            description = "禁止将搜索词、切应用与交互数据写入本地库",
            category = RuleCategory.CONTEXT_STRIP,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_MUTUAL_DATA
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLIP_SNIFFER_AND_KOULING,
            target = ImeTarget.SOGOU,
            title = "剪贴板口令嗅探拦截",
            description = "拦截非粘贴场景下的口令解析与后台读取",
            category = RuleCategory.CLIPBOARD,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLIP_AUTO_TRANSLATE_AND_JS,
            target = ImeTarget.SOGOU,
            title = "自动翻译脚本拦截",
            description = "阻止剪贴板自动触发云翻译与脚本读取",
            category = RuleCategory.CLIPBOARD,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLIP_DB_HISTORY_SAVE,
            target = ImeTarget.SOGOU,
            title = "剪贴板静默落库拦截",
            description = "禁止后台静默将剪贴板内容写入本地历史库",
            category = RuleCategory.CLIPBOARD,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLIP_STRICT_BLOCK_LISTENER,
            target = ImeTarget.SOGOU,
            title = "剪贴板严格监听拦截",
            description = "默认关闭，彻底禁用系统剪贴板变化监听",
            category = RuleCategory.CLIPBOARD,
            defaultEnabled = false,
            legacySwitchKey = null
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_SCAN_DEVICEINFO_HARVESTER,
            target = ImeTarget.SOGOU,
            title = "设备信息采集拦截",
            description = "阻止后台集中采集系统属性与设备档案",
            category = RuleCategory.SCAN_AND_LOCATION,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_SCAN_APP_LIST,
            target = ImeTarget.SOGOU,
            title = "应用列表扫描拦截",
            description = "禁止枚举设备已安装应用并上报安装状态",
            category = RuleCategory.SCAN_AND_LOCATION,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_SCAN_CONTACTS_UPLOAD_AND_DICT,
            target = ImeTarget.SOGOU,
            title = "通讯录与词库上传拦截",
            description = "阻止读取联系人并拦截通讯录词库同步上传",
            category = RuleCategory.SCAN_AND_LOCATION,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_SCAN_LOCATION_WIFI,
            target = ImeTarget.SOGOU,
            title = "定位与WiFi扫描拦截",
            description = "屏蔽定位与 WiFi BSSID 采集",
            category = RuleCategory.SCAN_AND_LOCATION,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_NET_DYNAMIC_DEX_AND_JS,
            target = ImeTarget.SOGOU,
            title = "动态加载与脚本拦截",
            description = "阻止加载远端未签名 DEX 插件与下发脚本",
            category = RuleCategory.DYNAMIC_AND_NET,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_UNSIGNED_DYNAMIC_DEX
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_NET_HTTPDNS,
            target = ImeTarget.SOGOU,
            title = "HTTPDNS 直连拦截",
            description = "默认关闭，禁止绕过系统 DNS 直连解析",
            category = RuleCategory.DYNAMIC_AND_NET,
            defaultEnabled = false,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_HTTP_DNS
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_UI_DUAL_SKIN_EMOJI,
            target = ImeTarget.SOGOU,
            title = "Emoji 双肤色弹窗",
            description = "增强 Emoji 面板支持多肤色组合长按选择",
            category = RuleCategory.UI_ENHANCE,
            defaultEnabled = true,
            legacySwitchKey = null
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_OVERLAY_PILL,
            target = ImeTarget.SOGOU,
            title = "键盘悬浮胶囊",
            description = "在键盘角落显示状态胶囊并支持快捷切换",
            category = RuleCategory.UI_ENHANCE,
            defaultEnabled = true,
            isOverlayPill = true,
            legacySwitchKey = PrivacyConfig.KEY_SHOW_KEYBOARD_PILL
        ),

        // =====================================================================
        // Baidu (24 rules)
        // =====================================================================
        HookRuleSpec(
            ruleId = RULE_BAIDU_GATE_CONSENT_AND_1839,
            target = ImeTarget.BAIDU,
            title = "隐私门控与标志修复",
            description = "修复隐私标志反转并阻断默认同意绕过",
            category = RuleCategory.PRIVACY_GATE,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_GATE_NOTICENTER_LOCK,
            target = ImeTarget.BAIDU,
            title = "通知中心推拉锁定",
            description = "阻断静默拉取配置弹窗与后台唤醒通道",
            category = RuleCategory.PRIVACY_GATE,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_ID_OAID_AND_HELIOS,
            target = ImeTarget.BAIDU,
            title = "OAID 与指纹屏蔽",
            description = "屏蔽 OAID 与 Helios 设备指纹",
            category = RuleCategory.DEVICE_ID,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_ID_CUID_ANDROIDID_SOFIRE,
            target = ImeTarget.BAIDU,
            title = "CUID 与风控标识屏蔽",
            description = "匿名化 CUID 与风控 SDK 设备标识",
            category = RuleCategory.DEVICE_ID,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_TELEMETRY_REALTIME_STATS,
            target = ImeTarget.BAIDU,
            title = "实时统计上报拦截",
            description = "阻断实时交互事件打点与明文统计发送",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_TELEMETRY_PERIODIC_UBC_MOBSTAT,
            target = ImeTarget.BAIDU,
            title = "周期统计与移动分析拦截",
            description = "拦截 UBC 周期回传与百度移动统计上报",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_TELEMETRY_VOICE_AND_ADS,
            target = ImeTarget.BAIDU,
            title = "语音与广告打点拦截",
            description = "阻断语音识别附带打点与商业广告埋点",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_TELEMETRY_CRASH_DUMP,
            target = ImeTarget.BAIDU,
            title = "崩溃转储上传拦截",
            description = "禁止静默收集并上传崩溃日志与内存转储",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CLOUD_STREAM_GATE,
            target = ImeTarget.BAIDU,
            title = "云输入流隐私加固",
            description = "加固云输入流隐私状态，纯本地由总览控制",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CLOUD_PAYLOAD_SANITIZE,
            target = ImeTarget.BAIDU,
            title = "云输入与 AI 脱敏",
            description = "清除云联想、语音与 AI 请求中的隐私字段",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CONTEXT_CURSOR_AND_AI,
            target = ImeTarget.BAIDU,
            title = "隐藏光标与 AI 上下文",
            description = "剥离编辑框光标前后文本与 AI 辅助上下文",
            category = RuleCategory.CONTEXT_STRIP,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_STRIP_CURSOR_CONTEXT
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CONTEXT_HOST_PACKAGE,
            target = ImeTarget.BAIDU,
            title = "隐藏前台应用包名",
            description = "禁止向百度云端接口传递当前宿主应用包名",
            category = RuleCategory.CONTEXT_STRIP,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_MASK_HOST_PACKAGE
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CLIP_TOKEN_UPLOAD_AND_JS,
            target = ImeTarget.BAIDU,
            title = "剪贴板口令上传拦截",
            description = "阻断口令识别上传与网页脚本读取剪贴板",
            category = RuleCategory.CLIPBOARD,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CLIP_BACKGROUND_AND_DB,
            target = ImeTarget.BAIDU,
            title = "剪贴板静默落库拦截",
            description = "修复隐私模式异常并禁止后台写入剪贴板库",
            category = RuleCategory.CLIPBOARD,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CLIP_STRICT_BLOCK_READERS,
            target = ImeTarget.BAIDU,
            title = "剪贴板严格读取拦截",
            description = "默认关闭，禁止输入法内部读取剪贴板内容",
            category = RuleCategory.CLIPBOARD,
            defaultEnabled = false,
            legacySwitchKey = null
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_SCAN_CONTACTS,
            target = ImeTarget.BAIDU,
            title = "通讯录读取拦截",
            description = "禁止读取系统联系人与上传通讯录词库",
            category = RuleCategory.SCAN_AND_LOCATION,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_SCAN_APP_LIST,
            target = ImeTarget.BAIDU,
            title = "应用列表扫描拦截",
            description = "禁止探测已安装应用并上报应用安装列表",
            category = RuleCategory.SCAN_AND_LOCATION,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_SCAN_SMS_OTP_LOCAL,
            target = ImeTarget.BAIDU,
            title = "短信验证码监听拦截",
            description = "默认关闭，拦截输入法后台读取短信验证码",
            category = RuleCategory.SCAN_AND_LOCATION,
            defaultEnabled = false,
            legacySwitchKey = null
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_SCAN_LOCATION_WIFI_CELL,
            target = ImeTarget.BAIDU,
            title = "定位与基站扫描拦截",
            description = "屏蔽位置、WiFi 热点与移动基站信息采集",
            category = RuleCategory.SCAN_AND_LOCATION,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_NET_SALVAGE_LOGS,
            target = ImeTarget.BAIDU,
            title = "日志回捞指令拦截",
            description = "阻断远端下发的静默日志提取与上传任务",
            category = RuleCategory.DYNAMIC_AND_NET,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_REMOTE_LOG_SALVAGE
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_NET_DYNAMIC_DEX_AND_JS,
            target = ImeTarget.BAIDU,
            title = "动态插件与脚本拦截",
            description = "阻止加载未签名动态 DEX 与执行远端脚本",
            category = RuleCategory.DYNAMIC_AND_NET,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_UNSIGNED_DYNAMIC_DEX
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_NET_TLS_HARDENING,
            target = ImeTarget.BAIDU,
            title = "弱 TLS 与降级拦截",
            description = "阻止关闭证书校验或降级不安全网络连接",
            category = RuleCategory.DYNAMIC_AND_NET,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_UNSIGNED_DYNAMIC_DEX
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_NET_HTTPDNS,
            target = ImeTarget.BAIDU,
            title = "HTTPDNS 直连拦截",
            description = "默认关闭，禁止绕过系统 DNS 直连百度解析",
            category = RuleCategory.DYNAMIC_AND_NET,
            defaultEnabled = false,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_HTTP_DNS
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_OVERLAY_PILL,
            target = ImeTarget.BAIDU,
            title = "键盘悬浮胶囊",
            description = "在键盘角落显示状态胶囊并支持快捷切换",
            category = RuleCategory.UI_ENHANCE,
            defaultEnabled = true,
            isOverlayPill = true,
            legacySwitchKey = PrivacyConfig.KEY_SHOW_KEYBOARD_PILL
        ),

        // =====================================================================
        // XiaoAi (20 rules)
        // =====================================================================
        HookRuleSpec(
            ruleId = RULE_XIAOAI_GATE_CTA_FAIL_OPEN,
            target = ImeTarget.XIAOAI,
            title = "CTA 授权异常修复",
            description = "阻止授权页异常或取消时自动开启联网同步",
            category = RuleCategory.PRIVACY_GATE,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_GATE_UNLOCK_DEGRADE_BYPASS,
            target = ImeTarget.XIAOAI,
            title = "锁屏降级绕过拦截",
            description = "默认关闭，阻止未解锁时跳过授权强制降级",
            category = RuleCategory.PRIVACY_GATE,
            defaultEnabled = false,
            legacySwitchKey = null
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT,
            target = ImeTarget.XIAOAI,
            title = "反检测与安全上报拦截",
            description = "阻断环境检测探测结果与安全指纹上报",
            category = RuleCategory.DEVICE_ID,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_ID_OAID_INSID_PHONENUM,
            target = ImeTarget.XIAOAI,
            title = "设备与号码标识屏蔽",
            description = "屏蔽 OAID、实例标识及本机手机号读取",
            category = RuleCategory.DEVICE_ID,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_TELEMETRY_ONETRACK,
            target = ImeTarget.XIAOAI,
            title = "OneTrack 拦截",
            description = "阻断小米 OneTrack 行为埋点与事件上报",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION,
            target = ImeTarget.XIAOAI,
            title = "密码框与会话统计拦截",
            description = "禁止密码输入框先计后拦及输入会话打点",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_PASSWORD_STATS
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_TELEMETRY_TAIYI_AND_JANK,
            target = ImeTarget.XIAOAI,
            title = "太乙与卡顿上报拦截",
            description = "阻断太乙引擎遥测与界面卡顿性能回传",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH,
            target = ImeTarget.XIAOAI,
            title = "崩溃与错误报告拦截",
            description = "禁止 xCrash 原生崩溃与自动诊断报告上传",
            category = RuleCategory.TELEMETRY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CLOUD_MICLAW_LLM,
            target = ImeTarget.XIAOAI,
            title = "大模型请求脱敏",
            description = "管控 MiClaw 大模型调用并清除敏感字段",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CLOUD_SCREEN_CAPTURE,
            target = ImeTarget.XIAOAI,
            title = "截屏识别转发拦截",
            description = "阻断小爱输入法后台截屏与屏幕内容转发",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CLOUD_SPEECH_ASR,
            target = ImeTarget.XIAOAI,
            title = "语音识别请求脱敏",
            description = "清理云端语音识别请求中的身份与设备字段",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CLOUD_TRANSLATE_AND_TAIYI,
            target = ImeTarget.XIAOAI,
            title = "翻译与太乙云脱敏",
            description = "剥离翻译与太乙云端服务附带的隐私信息",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CLOUD_DICTPACK_SYNC,
            target = ImeTarget.XIAOAI,
            title = "词库包同步脱敏",
            description = "脱敏词库包同步参数，纯本地由总览控制",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CLOUD_VOICEASSIST_LAUNCH,
            target = ImeTarget.XIAOAI,
            title = "禁止拉起语音助手",
            description = "默认关闭，阻止输入法跨进程拉起小爱语音",
            category = RuleCategory.CLOUD_PRIVACY,
            defaultEnabled = false,
            legacySwitchKey = null
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CONTEXT_HOTWORD_AICR,
            target = ImeTarget.XIAOAI,
            title = "热词纠错追踪拦截",
            description = "禁止向系统 AI 服务上报纠错前后的输入文本",
            category = RuleCategory.CONTEXT_STRIP,
            defaultEnabled = true,
            critical = true,
            legacySwitchKey = null
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG,
            target = ImeTarget.XIAOAI,
            title = "隐藏前台窗口与包名",
            description = "不向云端与系统通道传递前台应用与界面名",
            category = RuleCategory.CONTEXT_STRIP,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_MASK_HOST_PACKAGE
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP,
            target = ImeTarget.XIAOAI,
            title = "隐藏光标上下文提示",
            description = "剥离请求中附带的光标前后文本与提示词",
            category = RuleCategory.CONTEXT_STRIP,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_STRIP_CURSOR_CONTEXT
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CLIP_BACKGROUND_GUARD,
            target = ImeTarget.XIAOAI,
            title = "后台剪贴板拦截",
            description = "拦截后台静默读取与统计剪贴板内容",
            category = RuleCategory.CLIPBOARD,
            defaultEnabled = true,
            legacySwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL,
            target = ImeTarget.XIAOAI,
            title = "系统云控开关拦截",
            description = "默认关闭，阻止拉取远端系统级云控配置",
            category = RuleCategory.DYNAMIC_AND_NET,
            defaultEnabled = false,
            legacySwitchKey = null
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_OVERLAY_PILL,
            target = ImeTarget.XIAOAI,
            title = "键盘悬浮胶囊",
            description = "在键盘角落显示状态胶囊并支持快捷切换",
            category = RuleCategory.UI_ENHANCE,
            defaultEnabled = true,
            isOverlayPill = true,
            legacySwitchKey = PrivacyConfig.KEY_SHOW_KEYBOARD_PILL
        )
    )

    private val byId: Map<String, HookRuleSpec> = catalog.associateBy { it.ruleId }
    private val byTarget: Map<ImeTarget, List<HookRuleSpec>> = catalog.groupBy { it.target }
    private val byPackage: Map<String, List<HookRuleSpec>> = catalog.groupBy { it.targetPackage }

    fun allRules(): List<HookRuleSpec> = catalog

    fun rulesFor(target: ImeTarget): List<HookRuleSpec> = byTarget[target].orEmpty()

    fun rulesForPackage(targetPackage: String): List<HookRuleSpec> = byPackage[targetPackage].orEmpty()

    fun privacyRuleIdsForPackage(targetPackage: String): List<String> =
        rulesForPackage(targetPackage)
            .filterNot { it.isOverlayPill }
            .map { it.ruleId }

    fun findById(ruleId: String): HookRuleSpec? = byId[ruleId]

    fun find(ruleId: String): HookRuleSpec? = findById(ruleId)

    fun overlayRuleIdFor(target: ImeTarget): String = when (target) {
        ImeTarget.SOGOU -> RULE_SOGOU_OVERLAY_PILL
        ImeTarget.BAIDU -> RULE_BAIDU_OVERLAY_PILL
        ImeTarget.XIAOAI -> RULE_XIAOAI_OVERLAY_PILL
    }
}
