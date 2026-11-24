package com.scluzlep.miimeprivacy.model

import com.scluzlep.miimeprivacy.config.PrivacyConfig

enum class RuleCategory {
    TELEMETRY,
    CLOUD_PRIVACY,
    CONTEXT_STRIP,
    CLIPBOARD,
    SCAN_AND_DCL,
    STRICT,
    OVERLAY
}

/**
 * Static specification of a privacy protection or overlay rule for one target IME.
 * Used by the module UI to display expected protections before an IME process has started.
 */
data class HookRuleSpec(
    val ruleId: String,
    val target: ImeTarget,
    val title: String,
    val description: String,
    val category: RuleCategory,
    val critical: Boolean = false,
    val configSwitchKey: String? = null
)

object HookRuleCatalog {

    const val RULE_SOGOU_PRIVACY_GATE = "sogou.privacy_gate"
    const val RULE_SOGOU_TELEMETRY = "sogou.telemetry_sinks"
    const val RULE_SOGOU_CLOUD_SANITIZE = "sogou.cloud_sanitize"
    const val RULE_SOGOU_MUTUAL_DATA = "sogou.mutual_data"
    const val RULE_SOGOU_CLIPBOARD = "sogou.clipboard"
    const val RULE_SOGOU_CONTACTS_APP_SCAN = "sogou.contacts_app_scan"
    const val RULE_SOGOU_DYNAMIC_DEX = "sogou.dynamic_dex"
    const val RULE_SOGOU_HTTPDNS = "sogou.httpdns"
    const val RULE_SOGOU_OVERLAY_PILL = "sogou.overlay_pill"

    const val RULE_BAIDU_PRIVACY_GATE = "baidu.privacy_gate"
    const val RULE_BAIDU_TELEMETRY = "baidu.telemetry_sinks"
    const val RULE_BAIDU_CLOUD_AND_AI = "baidu.cloud_and_ai_sanitize"
    const val RULE_BAIDU_CLIPBOARD = "baidu.clipboard"
    const val RULE_BAIDU_CONTACTS_SMS_SCAN = "baidu.contacts_sms_scan"
    const val RULE_BAIDU_SALVAGE_DCL = "baidu.remote_salvage_dcl"
    const val RULE_BAIDU_HTTPDNS = "baidu.httpdns"
    const val RULE_BAIDU_OVERLAY_PILL = "baidu.overlay_pill"

    const val RULE_XIAOAI_CTA_GATE = "xiaoai.cta_fail_open"
    const val RULE_XIAOAI_ONETRACK = "xiaoai.onetrack_and_password"
    const val RULE_XIAOAI_AI_CLOUD_IPC = "xiaoai.ai_cloud_and_ipc"
    const val RULE_XIAOAI_CLIPBOARD = "xiaoai.clipboard"
    const val RULE_XIAOAI_HOTWORD_TRACKER = "xiaoai.hotword_tracker"
    const val RULE_XIAOAI_OVERLAY_PILL = "xiaoai.overlay_pill"

    private val catalog: List<HookRuleSpec> = listOf(
        // Sogou
        HookRuleSpec(
            ruleId = RULE_SOGOU_PRIVACY_GATE,
            target = ImeTarget.SOGOU,
            title = "隐私门控与 Alive 绕过拦截",
            description = "拦截未同意隐私放行保活、special_alive/enforce_send 及 C0532d 越权 Cookie 注入",
            category = RuleCategory.TELEMETRY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_TELEMETRY,
            target = ImeTarget.SOGOU,
            title = "遥测打点与日志上报净化",
            description = "阻断 kpi2/instantpb/BigData/WhiteDog/Bugly/RMonitor 与 TDDiag 日志打包",
            category = RuleCategory.TELEMETRY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLOUD_SANITIZE,
            target = ImeTarget.SOGOU,
            title = "云输入 WSS 脱敏与纯本地切换",
            description = "剥离前后 100 字上下文、QQ/微信对话缓存、触控坐标与宿主包名，支持纯本地直断",
            category = RuleCategory.CLOUD_PRIVACY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_MUTUAL_DATA,
            target = ImeTarget.SOGOU,
            title = "MutualData 行为入库拦截",
            description = "关闭小米版默认开启的搜索词、应用切换与地理坐标 bigdata.db 持久化",
            category = RuleCategory.CONTEXT_STRIP,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_MUTUAL_DATA
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLIPBOARD,
            target = ImeTarget.SOGOU,
            title = "剪贴板后台监听隔离",
            description = "隔离非用户主动触发的剪贴板读取、口令识别与反馈附带行为",
            category = RuleCategory.CLIPBOARD,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CONTACTS_APP_SCAN,
            target = ImeTarget.SOGOU,
            title = "通讯录上传与应用列表扫描阻断",
            description = "拦截隐藏开关控制的 15 天通讯录上传与 getInstalledPackages 全量扫包",
            category = RuleCategory.SCAN_AND_DCL,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_DYNAMIC_DEX,
            target = ImeTarget.SOGOU,
            title = "未签名动态 DEX 与任务加载阻断",
            description = "拦截仅 MD5 校验的 hj 远端任务 DEX 与动态插件加载",
            category = RuleCategory.SCAN_AND_DCL,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_UNSIGNED_DYNAMIC_DEX
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_HTTPDNS,
            target = ImeTarget.SOGOU,
            title = "HTTPDNS 直连旁路阻断 (严格)",
            description = "阻断 MSDKDnsResolver (119.29.29.98) 绕过系统 DNS 的解析初始化",
            category = RuleCategory.STRICT,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_HTTP_DNS
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_OVERLAY_PILL,
            target = ImeTarget.SOGOU,
            title = "键盘顶栏云输入快捷胶囊",
            description = "在键盘视图顶部悬浮微透明胶囊，支持一键切换纯本地/云输入模式",
            category = RuleCategory.OVERLAY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_SHOW_KEYBOARD_PILL
        ),

        // Baidu
        HookRuleSpec(
            ruleId = RULE_BAIDU_PRIVACY_GATE,
            target = ImeTarget.BAIDU,
            title = "隐私标志反转修复与门控加固",
            description = "修复 dj0.l.I1 标志位反转及 tn0.e/UBCHelper/1839 默认同意绕过",
            category = RuleCategory.TELEMETRY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_TELEMETRY,
            target = ImeTarget.BAIDU,
            title = "周期上报与明文统计阻断",
            description = "拦截 sj0/a case 14 周期回传、http://r.bd.sec.miui.com 明文上报及断网补报",
            category = RuleCategory.TELEMETRY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CLOUD_AND_AI,
            target = ImeTarget.BAIDU,
            title = "云输入 / VoiceTrace / AI 上下文脱敏",
            description = "脱敏 CloudInfo/libiptcore、VoiceTrace 前后文及文心 210 字对话上传，支持纯本地直断",
            category = RuleCategory.CLOUD_PRIVACY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CLIPBOARD,
            target = ImeTarget.BAIDU,
            title = "隐私模式剪贴板落盘隔离",
            description = "修正 MIUI 隐私输入态反向采集缺陷，阻断 clipborad_records.db 静默落盘",
            category = RuleCategory.CLIPBOARD,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CONTACTS_SMS_SCAN,
            target = ImeTarget.BAIDU,
            title = "通讯录/短信读取与桌面应用扫描阻断",
            description = "拦截短信收件箱查询、通讯录词汇上传与全量应用包名扫描",
            category = RuleCategory.SCAN_AND_DCL,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_SALVAGE_DCL,
            target = ImeTarget.BAIDU,
            title = "远端日志回捞与动态脚本/DEX 阻断",
            description = "阻断 mobile_log_salvage 静默日志回捞与远端插件/QuickJS 动态执行",
            category = RuleCategory.SCAN_AND_DCL,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_REMOTE_LOG_SALVAGE
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_HTTPDNS,
            target = ImeTarget.BAIDU,
            title = "百度 HTTPDNS 旁路阻断 (严格)",
            description = "阻断 httpsdns.baidu.com 与 180.76.76.112 直连域名解析",
            category = RuleCategory.STRICT,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_HTTP_DNS
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_OVERLAY_PILL,
            target = ImeTarget.BAIDU,
            title = "键盘顶栏云输入快捷胶囊",
            description = "在键盘视图顶部悬浮微透明胶囊，支持一键切换纯本地/云输入模式",
            category = RuleCategory.OVERLAY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_SHOW_KEYBOARD_PILL
        ),

        // XiaoAi
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CTA_GATE,
            target = ImeTarget.XIAOAI,
            title = "CTA Fail-Open 与降级门控修复",
            description = "阻止 CtaConsentActivity 异常或取消时自动置为已同意并开启词库同步",
            category = RuleCategory.TELEMETRY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_ONETRACK,
            target = ImeTarget.XIAOAI,
            title = "OneTrack 绕过与密码框零计数净化",
            description = "修复 v7.f 忽略改进计划开关、密码框先计数后拦截及系统分析 IPC 旁路",
            category = RuleCategory.TELEMETRY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_PASSWORD_STATS
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_AI_CLOUD_IPC,
            target = ImeTarget.XIAOAI,
            title = "AI 表达/截屏 IPC 与云端辅助脱敏",
            description = "管控 com.miui.voiceassist 截屏转发与 com.xiaomi.aiservice/speech 云端请求",
            category = RuleCategory.CLOUD_PRIVACY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CLIPBOARD,
            target = ImeTarget.XIAOAI,
            title = "后台特权剪贴板静默读取门控",
            description = "管控 READ_CLIPBOARD_IN_BACKGROUND 与无感通知抑制下的后台读取",
            category = RuleCategory.CLIPBOARD,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_HOTWORD_TRACKER,
            target = ImeTarget.XIAOAI,
            title = "跨应用热词纠错文本追踪拦截 (严格)",
            description = "阻断 HotwordRemoteClient 向 com.xiaomi.aicr 发送 before_text/final_text",
            category = RuleCategory.STRICT,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_XIAOAI_HOTWORD_TRACKER
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_OVERLAY_PILL,
            target = ImeTarget.XIAOAI,
            title = "键盘顶栏云输入快捷胶囊",
            description = "在键盘视图顶部悬浮微透明胶囊，支持一键切换纯本地/云输入模式",
            category = RuleCategory.OVERLAY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_SHOW_KEYBOARD_PILL
        )
    )

    fun allRules(): List<HookRuleSpec> = catalog

    fun rulesFor(target: ImeTarget): List<HookRuleSpec> = catalog.filter { it.target == target }

    fun find(ruleId: String): HookRuleSpec? = catalog.firstOrNull { it.ruleId == ruleId }

    fun overlayRuleIdFor(target: ImeTarget): String = when (target) {
        ImeTarget.SOGOU -> RULE_SOGOU_OVERLAY_PILL
        ImeTarget.BAIDU -> RULE_BAIDU_OVERLAY_PILL
        ImeTarget.XIAOAI -> RULE_XIAOAI_OVERLAY_PILL
    }
}
