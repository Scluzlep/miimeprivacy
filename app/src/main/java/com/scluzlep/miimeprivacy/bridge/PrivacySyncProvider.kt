package com.scluzlep.miimeprivacy.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget

/**
 * Secondary fast-path IPC provider allowing hooked IME processes to push live config updates
 * or runtime diagnostics back to the module app when caller visibility permits.
 */
class PrivacySyncProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { XposedServiceManager.init(it) }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return errorBundle("no_context")
        XposedServiceManager.init(ctx)

        val callerPackage = verifyCallerPackage(ctx)
            ?: return errorBundle("unauthorized_caller")

        return when (method) {
            ConfigContract.METHOD_PUSH_CONFIG -> {
                val raw = extras?.getString(ConfigContract.EXTRA_CONFIG_JSON)
                    ?: return errorBundle("missing_config_json")
                val parsed = PrivacyConfig.fromJson(raw)
                if (parsed.updatedAtMs <= 0L) {
                    return errorBundle("invalid_timestamp")
                }
                val merged = XposedServiceManager.onConfigPushedFromIme(parsed)
                Bundle().apply {
                    putBoolean("ok", true)
                    putString(ConfigContract.EXTRA_CONFIG_JSON, merged.toJson())
                }
            }

            ConfigContract.METHOD_PUSH_DIAGNOSTICS -> {
                val raw = extras?.getString(ConfigContract.EXTRA_DIAGNOSTICS_JSON)
                    ?: return errorBundle("missing_diagnostics_json")
                val diag = ImeRuntimeDiagnostics.fromJson(raw)
                    ?: return errorBundle("invalid_diagnostics_json")
                if (callerPackage != ConfigContract.MODULE_PACKAGE &&
                    diag.target.packageName != callerPackage
                ) {
                    return errorBundle("target_package_mismatch")
                }
                XposedServiceManager.onDiagnosticsPushedFromIme(diag)
                Bundle().apply { putBoolean("ok", true) }
            }

            ConfigContract.METHOD_PULL_CONFIG -> {
                Bundle().apply {
                    putBoolean("ok", true)
                    putString(ConfigContract.EXTRA_CONFIG_JSON, XposedServiceManager.config.value.toJson())
                }
            }

            else -> errorBundle("unknown_method")
        }
    }

    private fun verifyCallerPackage(ctx: android.content.Context): String? {
        val callingPkg = callingPackage
        if (callingPkg == ConfigContract.MODULE_PACKAGE || ImeTarget.fromPackage(callingPkg) != null) {
            return callingPkg
        }
        val uidPackages = runCatching {
            ctx.packageManager.getPackagesForUid(Binder.getCallingUid())
        }.getOrNull().orEmpty()
        return uidPackages.firstOrNull { pkg ->
            pkg == ConfigContract.MODULE_PACKAGE || ImeTarget.fromPackage(pkg) != null
        }
    }

    private fun errorBundle(reason: String): Bundle = Bundle().apply {
        putBoolean("ok", false)
        putString("error", reason)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
