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
            title = "隐私同意门控",
            description = "未同意隐私政策前禁止保活与后台发包",
            category = RuleCategory.TELEMETRY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_TELEMETRY,
            target = ImeTarget.SOGOU,
            title = "遥测与日志拦截",
            description = "阻断行为打点、崩溃日志与诊断上报",
            category = RuleCategory.TELEMETRY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLOUD_SANITIZE,
            target = ImeTarget.SOGOU,
            title = "云输入请求脱敏",
            description = "清除上下文、对话缓存、坐标与宿主包名",
            category = RuleCategory.CLOUD_PRIVACY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_MUTUAL_DATA,
            target = ImeTarget.SOGOU,
            title = "行为数据入库拦截",
            description = "禁止记录搜索词、应用切换与定位信息",
            category = RuleCategory.CONTEXT_STRIP,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_MUTUAL_DATA
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CLIPBOARD,
            target = ImeTarget.SOGOU,
            title = "剪贴板读取拦截",
            description = "拦截非手动粘贴时的后台剪贴板读取",
            category = RuleCategory.CLIPBOARD,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_CONTACTS_APP_SCAN,
            target = ImeTarget.SOGOU,
            title = "通讯录与扫包拦截",
            description = "禁止上传通讯录并拦截读取已安装应用",
            category = RuleCategory.SCAN_AND_DCL,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_DYNAMIC_DEX,
            target = ImeTarget.SOGOU,
            title = "未签名动态加载拦截",
            description = "阻止下载并加载远端未签名动态代码",
            category = RuleCategory.SCAN_AND_DCL,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_UNSIGNED_DYNAMIC_DEX
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_HTTPDNS,
            target = ImeTarget.SOGOU,
            title = "HTTPDNS 拦截",
            description = "禁止绕过系统 DNS 进行直连域名解析",
            category = RuleCategory.STRICT,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_HTTP_DNS
        ),
        HookRuleSpec(
            ruleId = RULE_SOGOU_OVERLAY_PILL,
            target = ImeTarget.SOGOU,
            title = "键盘悬浮胶囊",
            description = "在键盘角落显示状态胶囊并支持快捷切换",
            category = RuleCategory.OVERLAY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_SHOW_KEYBOARD_PILL
        ),

        // Baidu
        HookRuleSpec(
            ruleId = RULE_BAIDU_PRIVACY_GATE,
            target = ImeTarget.BAIDU,
            title = "隐私门控修复",
            description = "修复隐私标志反转并阻断默认同意绕过",
            category = RuleCategory.TELEMETRY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_TELEMETRY,
            target = ImeTarget.BAIDU,
            title = "周期统计上报拦截",
            description = "阻断周期行为回传、明文统计与断网补报",
            category = RuleCategory.TELEMETRY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CLOUD_AND_AI,
            target = ImeTarget.BAIDU,
            title = "云输入与 AI 脱敏",
            description = "清除云联想、语音与 AI 请求的上下文",
            category = RuleCategory.CLOUD_PRIVACY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CLIPBOARD,
            target = ImeTarget.BAIDU,
            title = "剪贴板记录拦截",
            description = "修复隐私模式异常并禁止静默写入数据库",
            category = RuleCategory.CLIPBOARD,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_CONTACTS_SMS_SCAN,
            target = ImeTarget.BAIDU,
            title = "通讯录短信扫包拦截",
            description = "禁止读取通讯录、短信及已安装应用列表",
            category = RuleCategory.SCAN_AND_DCL,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_SALVAGE_DCL,
            target = ImeTarget.BAIDU,
            title = "日志回捞与脚本拦截",
            description = "阻断静默日志提取与远端插件脚本执行",
            category = RuleCategory.SCAN_AND_DCL,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_REMOTE_LOG_SALVAGE
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_HTTPDNS,
            target = ImeTarget.BAIDU,
            title = "HTTPDNS 拦截",
            description = "禁止绕过系统 DNS 直连百度解析服务",
            category = RuleCategory.STRICT,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_HTTP_DNS
        ),
        HookRuleSpec(
            ruleId = RULE_BAIDU_OVERLAY_PILL,
            target = ImeTarget.BAIDU,
            title = "键盘悬浮胶囊",
            description = "在键盘角落显示状态胶囊并支持快捷切换",
            category = RuleCategory.OVERLAY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_SHOW_KEYBOARD_PILL
        ),

        // XiaoAi
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CTA_GATE,
            target = ImeTarget.XIAOAI,
            title = "隐私授权异常修复",
            description = "阻止授权页异常或取消时自动开启同步",
            category = RuleCategory.TELEMETRY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_TELEMETRY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_ONETRACK,
            target = ImeTarget.XIAOAI,
            title = "统计与密码框拦截",
            description = "关闭行为统计并禁止密码输入框先计后拦",
            category = RuleCategory.TELEMETRY,
            critical = true,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_PASSWORD_STATS
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_AI_CLOUD_IPC,
            target = ImeTarget.XIAOAI,
            title = "AI 与云端请求脱敏",
            description = "管控截屏转发并清理语音与 AI 云端请求",
            category = RuleCategory.CLOUD_PRIVACY,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_CLIPBOARD,
            target = ImeTarget.XIAOAI,
            title = "后台剪贴板拦截",
            description = "拦截后台静默读取剪贴板内容",
            category = RuleCategory.CLIPBOARD,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_CLIPBOARD_PRIVACY
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_HOTWORD_TRACKER,
            target = ImeTarget.XIAOAI,
            title = "热词纠错追踪拦截",
            description = "禁止向系统服务上报纠错前后的输入文本",
            category = RuleCategory.STRICT,
            critical = false,
            configSwitchKey = PrivacyConfig.KEY_BLOCK_XIAOAI_HOTWORD_TRACKER
        ),
        HookRuleSpec(
            ruleId = RULE_XIAOAI_OVERLAY_PILL,
            target = ImeTarget.XIAOAI,
            title = "键盘悬浮胶囊",
            description = "在键盘角落显示状态胶囊并支持快捷切换",
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
