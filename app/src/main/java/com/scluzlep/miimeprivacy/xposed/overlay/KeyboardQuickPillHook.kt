package com.scluzlep.miimeprivacy.xposed.overlay

import android.content.Context
import android.content.pm.ApplicationInfo
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.HookRegistry

/**
 * Phase A baseline stub for the in-keyboard quick cloud-toggle pill overlay.
 * Owned and implemented in Phase B on branch `feat/keyboard-overlay`.
 *
 * NOTE for Phase B: prefix all HookRegistry hookIds with `"overlay:"` so live target
 * unhook (`keepPrefixes = setOf("lifecycle:", "overlay:")`) preserves the pill overlay
 * when an IME's privacy pack is toggled off.
 */
object KeyboardQuickPillHook {

    fun install(
        target: ImeTarget,
        context: Context?,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ) {
        registry.reportRule(
            ruleId = HookRuleCatalog.overlayRuleIdFor(target),
            status = HookMatchStatus.SKIPPED,
            detail = "Phase A baseline stub"
        )
    }

    fun onConfigChanged(oldConfig: PrivacyConfig, newConfig: PrivacyConfig) {
        // Implemented in Phase B
    }

    fun clearOnHotReload() {
        // Implemented in Phase B
    }
}
