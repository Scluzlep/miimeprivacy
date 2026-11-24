package com.scluzlep.miimeprivacy.model

/**
 * Supported Xiaomi ecosystem IME host packages targeted by the Xposed module.
 */
enum class ImeTarget(
    val packageName: String,
    val displayName: String,
    val expectedVersionCode: Long,
    val imeServiceClassName: String
) {
    SOGOU(
        packageName = "com.sohu.inputmethod.sogou.xiaomi",
        displayName = "搜狗输入法小米版",
        expectedVersionCode = 20160L,
        imeServiceClassName = "com.sohu.inputmethod.sogou.SogouIME"
    ),
    BAIDU(
        packageName = "com.baidu.input_mi",
        displayName = "百度输入法小米版",
        expectedVersionCode = 7108L,
        imeServiceClassName = "com.baidu.input_mi.ImeService"
    ),
    XIAOAI(
        packageName = "com.xiaomi.type",
        displayName = "超级小爱输入法",
        expectedVersionCode = 21363L,
        imeServiceClassName = "com.mi.ime.MiInputMethodService"
    );

    companion object {
        fun fromPackage(pkg: String?): ImeTarget? {
            if (pkg.isNullOrBlank()) return null
            return entries.firstOrNull { it.packageName == pkg }
        }
    }
}
