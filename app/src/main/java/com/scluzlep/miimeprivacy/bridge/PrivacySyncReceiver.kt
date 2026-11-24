package com.scluzlep.miimeprivacy.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget

/**
 * Primary cross-process return receiver for IME -> Module App config & diagnostics sync.
 * Explicit component broadcasts bypass Android 11+ `<queries>` restrictions from target IMEs.
 */
class PrivacySyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != ConfigContract.ACTION_SYNC_FROM_IME) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val sender = sentFromPackage
            if (sender != null && ImeTarget.fromPackage(sender) == null && sender != ConfigContract.MODULE_PACKAGE) {
                return
            }
        }

        XposedServiceManager.init(context)

        val sourcePkg = intent.getStringExtra(ConfigContract.EXTRA_SOURCE_PACKAGE)
        val sourceTarget = ImeTarget.fromPackage(sourcePkg) ?: return
        if (!isPackageInstalledForTarget(context, sourceTarget.packageName)) return

        when (intent.getStringExtra(ConfigContract.EXTRA_SYNC_TYPE)) {
            ConfigContract.SYNC_TYPE_CONFIG -> {
                val raw = intent.getStringExtra(ConfigContract.EXTRA_CONFIG_JSON) ?: return
                val parsed = PrivacyConfig.fromJson(raw)
                if (parsed.updatedAtMs <= 0L) return
                XposedServiceManager.onConfigPushedFromIme(parsed)
            }

            ConfigContract.SYNC_TYPE_DIAGNOSTICS -> {
                val raw = intent.getStringExtra(ConfigContract.EXTRA_DIAGNOSTICS_JSON) ?: return
                val diag = ImeRuntimeDiagnostics.fromJson(raw) ?: return
                if (diag.target != sourceTarget) return
                XposedServiceManager.onDiagnosticsPushedFromIme(diag)
            }
        }
    }

    private fun isPackageInstalledForTarget(context: Context, packageName: String): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }
}
