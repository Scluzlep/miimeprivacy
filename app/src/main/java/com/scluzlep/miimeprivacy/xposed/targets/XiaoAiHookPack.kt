package com.scluzlep.miimeprivacy.xposed.targets

import android.content.Context
import android.content.pm.ApplicationInfo
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ImeHookPack
import com.scluzlep.miimeprivacy.xposed.ImePackInstallResult

/**
 * Phase A baseline stub for XiaoAi IME (`com.xiaomi.type`).
 * Owned and implemented in Phase B on branch `feat/hooks-xiaoai`.
 */
object XiaoAiHookPack : ImeHookPack {

    override val target: ImeTarget = ImeTarget.XIAOAI

    override fun install(
        context: Context?,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ): ImePackInstallResult {
        val rules = HookRuleCatalog.rulesFor(target)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL }
        for (rule in rules) {
            registry.reportRule(
                ruleId = rule.ruleId,
                status = HookMatchStatus.SKIPPED,
                detail = "Phase A baseline stub"
            )
        }
        return ImePackInstallResult(
            target = target,
            reports = rules.mapNotNull { registry.getReport(it.ruleId) },
            note = "XiaoAi baseline stub"
        )
    }
}
