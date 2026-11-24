package com.scluzlep.miimeprivacy.config

/**
 * Shared IPC, SharedPreferences, and intent contract constants across the module app
 * and hooked IME host processes.
 */
object ConfigContract {
    const val MODULE_PACKAGE = "com.scluzlep.miimeprivacy"

    const val REMOTE_PREFS_NAME = "miimeprivacy_prefs"
    const val MIRROR_PREFS_NAME = "miimeprivacy_mirror"
    const val DIAGNOSTICS_PREFS_NAME = "miimeprivacy_diagnostics"
    const val LOCAL_OVERRIDE_PREFS_NAME = "miimeprivacy_local_override"
    const val SYMBOL_CACHE_PREFS_NAME = "miimeprivacy_symbol_cache"

    const val SYNC_AUTHORITY = "com.scluzlep.miimeprivacy.sync"
    const val SYNC_CONTENT_URI = "content://$SYNC_AUTHORITY"

    const val ACTION_SYNC_FROM_IME = "com.scluzlep.miimeprivacy.ACTION_SYNC_FROM_IME"
    const val ACTION_OPEN_SETTINGS = "com.scluzlep.miimeprivacy.ACTION_OPEN_SETTINGS"

    const val RECEIVER_CLASS_NAME = "com.scluzlep.miimeprivacy.bridge.PrivacySyncReceiver"
    const val MAIN_ACTIVITY_CLASS_NAME = "com.scluzlep.miimeprivacy.ui.MainActivity"

    const val METHOD_PUSH_CONFIG = "push_config"
    const val METHOD_PUSH_DIAGNOSTICS = "push_diagnostics"
    const val METHOD_PULL_CONFIG = "pull_config"

    const val EXTRA_SYNC_TYPE = "sync_type"
    const val SYNC_TYPE_CONFIG = "config"
    const val SYNC_TYPE_DIAGNOSTICS = "diagnostics"

    const val EXTRA_CONFIG_JSON = "config_json"
    const val EXTRA_DIAGNOSTICS_JSON = "diagnostics_json"
    const val EXTRA_SOURCE_PACKAGE = "source_package"

    const val KEY_CONFIG_JSON = "config_json"
    const val KEY_UPDATED_AT_MS = "updated_at_ms"
    const val KEY_DIAGNOSTICS_PREFIX = "diag_"
}
