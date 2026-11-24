package com.scluzlep.miimeprivacy.xposed

import android.content.Context
import android.content.pm.ApplicationInfo
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.ImeTarget

data class ImePackInstallResult(
    val target: ImeTarget,
    val reports: List<HookRuleReport> = emptyList(),
    val note: String = ""
)

/**
 * Contract implemented by each target-specific IME privacy hook pack.
 */
interface ImeHookPack {
    val target: ImeTarget

    val imeServiceClassName: String
        get() = target.imeServiceClassName

    fun install(
        context: Context?,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ): ImePackInstallResult
}
