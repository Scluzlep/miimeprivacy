package com.scluzlep.miimeprivacy.xposed.targets

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.location.Location
import android.net.Uri
import android.os.Bundle
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ImeHookPack
import com.scluzlep.miimeprivacy.xposed.ImePackInstallResult
import com.scluzlep.miimeprivacy.xposed.ResolveResult
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.json.JSONArray
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.io.File
import java.lang.reflect.Array as JvmArray
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import javax.net.ssl.SSLSession

/**
 * Baidu Xiaomi IME (`com.baidu.input_mi`, versionCode 7108+) Xposed privacy hook pack.
 *
 * Implements all 23 non-overlay granular rules in [HookRuleCatalog.rulesFor] for
 * [ImeTarget.BAIDU] (plus [HookRuleCatalog.RULE_BAIDU_OVERLAY_PILL] handled by the overlay hook)
 * using per-rule [ConfigBridge.isPointEnabled] evaluation and FastPath + DexKit + structural
 * symbol resolution.
 */
object BaiduHookPack : ImeHookPack {

    override val target: ImeTarget = ImeTarget.BAIDU

    override fun install(
        context: Context?,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ): ImePackInstallResult {
        registry.createSymbolResolver(classLoader).use { resolver ->
            val privacyAgreedMethod = resolvePrivacyAgreedMethod(resolver)
            val privacyInputModeField = resolvePrivacyInputModeField(resolver)

            installPrivacyGateAndNoticeHooks(resolver, registry, privacyAgreedMethod)
            installIdentifierHooks(resolver, registry)
            installTelemetryHooks(resolver, registry)
            installCloudAndContextHooks(resolver, registry)
            installClipboardHooks(
                resolver = resolver,
                classLoader = classLoader,
                appContextWrapper = context as? ContextWrapper,
                registry = registry,
                privacyAgreedMethod = privacyAgreedMethod,
                privacyInputModeField = privacyInputModeField
            )
            installScanHooks(resolver, registry)
            installNetworkAndHardeningHooks(resolver, registry)
        }

        val nonOverlayRules = HookRuleCatalog.rulesFor(target)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_BAIDU_OVERLAY_PILL }
        return ImePackInstallResult(
            target = target,
            reports = nonOverlayRules.mapNotNull { registry.getReport(it.ruleId) },
            note = "Baidu v2.0 privacy hooks installed (${nonOverlayRules.size} rules)"
        )
    }

    private fun isPointEnabled(ruleId: String): Boolean =
        ConfigBridge.isPointEnabled(target.packageName, ruleId)

    // -------------------------------------------------------------------------
    // Gate rules: RULE_BAIDU_GATE_CONSENT_AND_1839, RULE_BAIDU_GATE_NOTICENTER_LOCK
    // -------------------------------------------------------------------------

    private fun resolvePrivacyAgreedMethod(resolver: SymbolResolver): Method? =
        resolver.resolveMethod(
            cacheKey = "baidu:privacy_agreed_2439",
            fastPath = { cl ->
                SymbolResolver.loadClassOrNull("e20.g", cl)
                    ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "a") }
            },
            dexKitFinder = { bridge ->
                bridge.findMethod {
                    matcher {
                        declaredClass {
                            usingStrings("PrivacyDialogUtils.java")
                        }
                        usingNumbers(2439)
                        returnType("boolean")
                        paramCount(0)
                    }
                }.singleOrNull()?.descriptor
            }
        ).value

    private fun installPrivacyGateAndNoticeHooks(
        resolver: SymbolResolver,
        registry: HookRegistry,
        privacyAgreedMethod: Method?
    ) {
        // 1) baidu.gate.consent_and_1839
        val consentRuleId = HookRuleCatalog.RULE_BAIDU_GATE_CONSENT_AND_1839
        registry.installRuleIfEnabled(consentRuleId) {
            // tn0.e#isPrivacyAgreed() -> stop hardcoded true; delegate to real 2439 dialog consent.
            val tn0AgreeResult = resolver.resolveMethod(
                cacheKey = "baidu:tn0_e_is_privacy_agreed",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("tn0.e", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "isPrivacyAgreed") }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            declaredClass {
                                addInterface("ll.a")
                                usingStrings("An operation is not implemented: Not yet implemented")
                            }
                            name = "isPrivacyAgreed"
                            returnType("boolean")
                            paramCount(0)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            registry.hookExecutable(
                ruleId = consentRuleId,
                hookId = "baidu.privacy.tn0_is_privacy_agreed",
                resolveResult = tn0AgreeResult
            ) { chain ->
                if (!isPointEnabled(consentRuleId)) {
                    return@hookExecutable chain.proceed()
                }
                val agreed = readPrivacyAgreed(privacyAgreedMethod)
                if (!agreed) {
                    registry.incrementBlocked(consentRuleId)
                }
                agreed
            }

            // com.baidu.ubc.UBCHelper#isAgreePrivacy() -> block fail-open UBC telemetry agreement.
            hookSimpleBlock(
                registry = registry,
                ruleId = consentRuleId,
                hookId = "baidu.privacy.ubc_is_agree_privacy",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:ubc_helper_is_agree_privacy",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("com.baidu.ubc.UBCHelper", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "isAgreePrivacy") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.ubc")
                            matcher {
                                declaredClass {
                                    usingStrings("https://tcbox.baidu.com/ztbox?action=zubc")
                                }
                                name = "isAgreePrivacy"
                                returnType("boolean")
                                paramCount(0)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(consentRuleId) },
                blockedResult = { false }
            )

            // dj0.y#h(int):boolean & #A(int, boolean):void -> neutralize bit 1839 default-agree bypass.
            val settingsClass = resolver.resolveClass(
                cacheKey = "baidu:dj0_y_settings",
                fastPathCandidates = listOf("dj0.y"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        matcher {
                            usingStrings("option_log", "Option is empty, reset default setting")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value

            registry.hookExecutable(
                ruleId = consentRuleId,
                hookId = "baidu.privacy.bit_1839_read",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:dj0_y_h",
                    clazz = settingsClass,
                    methodName = "h",
                    Int::class.javaPrimitiveType!!,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("option_log", "Option is empty, reset default setting")
                                }
                                paramTypes("int")
                                returnType("boolean")
                                addInvoke(
                                    MethodMatcher().apply {
                                        paramTypes("int", "boolean")
                                        returnType("boolean")
                                    }
                                )
                            }
                        }.singleOrNull()?.descriptor
                    }
                )
            ) { chain ->
                val prefKey = (chain.getArg(0) as? Int) ?: -1
                if (BaiduJsonSanitizer.shouldBlockBit1839Consent(
                        prefKey = prefKey,
                        blockTelemetry = isPointEnabled(consentRuleId)
                    )
                ) {
                    registry.incrementBlocked(consentRuleId)
                    false
                } else {
                    chain.proceed()
                }
            }

            registry.hookExecutable(
                ruleId = consentRuleId,
                hookId = "baidu.privacy.bit_1839_write",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:dj0_y_A",
                    clazz = settingsClass,
                    methodName = "A",
                    Int::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("option_log", "Option is empty, reset default setting")
                                }
                                paramTypes("int", "boolean")
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                val prefKey = (chain.getArg(0) as? Int) ?: -1
                val value = (chain.getArg(1) as? Boolean) ?: false
                if (value && BaiduJsonSanitizer.shouldBlockBit1839Consent(
                        prefKey = prefKey,
                        blockTelemetry = isPointEnabled(consentRuleId)
                    )
                ) {
                    registry.incrementBlocked(consentRuleId)
                    chain.proceed(arrayOf<Any?>(prefKey, false))
                } else {
                    chain.proceed()
                }
            }
        }

        // 2) baidu.gate.noticenter_lock: ph0.e#h(kh0.j):io.reactivex.internal.operators.single.a
        val notiRuleId = HookRuleCatalog.RULE_BAIDU_GATE_NOTICENTER_LOCK
        registry.installRuleIfEnabled(notiRuleId) {
            val ph0MethodResult = resolver.resolveMethod(
                cacheKey = "baidu:ph0_e_h",
                fastPath = { cl ->
                    val ph0Class = SymbolResolver.loadClassOrNull("ph0.e", cl) ?: return@resolveMethod null
                    val kh0jClass = SymbolResolver.loadClassOrNull("kh0.j", cl) ?: return@resolveMethod null
                    SymbolResolver.findDeclaredMethodOrNull(ph0Class, "h", kh0jClass)
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            declaredClass {
                                usingStrings("NotiSwitch", "pref_key_ccs", "pref_key_adv_swtich")
                            }
                            usingStrings("NotiSwitch", "pref_key_ccs")
                            paramCount(1)
                            returnType("io.reactivex.internal.operators.single.a")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val singleJustMethod = ph0MethodResult.value?.returnType?.let {
                BaiduJsonSanitizer.findRxSingleJustMethod(it)
            }
            if (ph0MethodResult.value != null && singleJustMethod == null) {
                registry.hookExecutable(
                    ruleId = notiRuleId,
                    hookId = "baidu.gate.noticenter_switch_lock",
                    resolveResult = ResolveResult(
                        value = null,
                        source = ResolveSource.MISS,
                        descriptor = ph0MethodResult.descriptor,
                        detail = "ph0.e#h pe1.x.e(Object) missing; keeping unhooked to avoid Rx NPE"
                    ),
                    onFailureStatus = HookMatchStatus.DEGRADED
                ) { it.proceed() }
            } else {
                registry.hookExecutable(
                    ruleId = notiRuleId,
                    hookId = "baidu.gate.noticenter_switch_lock",
                    resolveResult = ph0MethodResult,
                    onFailureStatus = HookMatchStatus.DEGRADED
                ) { chain ->
                    if (!isPointEnabled(notiRuleId)) {
                        return@hookExecutable chain.proceed()
                    }
                    registry.incrementBlocked(notiRuleId)
                    BaiduJsonSanitizer.invokeRxSingleBoolean(singleJustMethod, false)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Identifier rules: RULE_BAIDU_ID_OAID_AND_HELIOS, RULE_BAIDU_ID_CUID_ANDROIDID_SOFIRE
    // -------------------------------------------------------------------------

    private fun installIdentifierHooks(resolver: SymbolResolver, registry: HookRegistry) {
        // 3) baidu.id.oaid_and_helios
        val oaidRuleId = HookRuleCatalog.RULE_BAIDU_ID_OAID_AND_HELIOS
        registry.installRuleIfEnabled(oaidRuleId) {
            val wc1Class = resolver.resolveClass(
                cacheKey = "baidu:wc1_a_class",
                fastPathCandidates = listOf("wc1.a"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        matcher {
                            usingStrings("IdentifierManager", "getOAID", "getUDID")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value

            hookSimpleBlock(
                registry = registry,
                ruleId = oaidRuleId,
                hookId = "baidu.id.wc1_oaid_get",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:wc1_a_oaid",
                    clazz = wc1Class,
                    methodName = "a",
                    Context::class.java,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("IdentifierManager", "getOAID")
                                }
                                paramTypes("android.content.Context")
                                returnType("java.lang.String")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(oaidRuleId) },
                blockedResult = { "" }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = oaidRuleId,
                hookId = "baidu.id.wc1_oaid_supported",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:wc1_a_supported",
                    clazz = wc1Class,
                    methodName = "b",
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("IdentifierManager", "getOAID")
                                }
                                paramCount(0)
                                returnType("boolean")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(oaidRuleId) },
                blockedResult = { false },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = oaidRuleId,
                hookId = "baidu.id.b51_g_oaid",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:b51_g_oaid",
                    fastPath = { cl ->
                        val gCls = SymbolResolver.loadClassOrNull("b51.g", cl) ?: return@resolveMethod null
                        val faCls = SymbolResolver.loadClassOrNull("fa.a", cl) ?: return@resolveMethod null
                        SymbolResolver.findDeclaredMethodOrNull(gCls, "a", Context::class.java, faCls)
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("/data/local/su", "/data/local/bin/su")
                                }
                                paramCount(2)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(oaidRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            // Helios background collectors: n9.c#b(n9.e), q9.b#S(Bundle, String), q9.b#O(n7.a)
            hookSimpleBlock(
                registry = registry,
                ruleId = oaidRuleId,
                hookId = "baidu.id.helios_n9_submit",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:helios_n9_c_b",
                    fastPath = { cl ->
                        val cCls = SymbolResolver.loadClassOrNull("n9.c", cl) ?: return@resolveMethod null
                        val eCls = SymbolResolver.loadClassOrNull("n9.e", cl) ?: return@resolveMethod null
                        SymbolResolver.findDeclaredMethodOrNull(cCls, "b", eCls)
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("\u200bcom.baidu.helios.HeliosManager")
                                }
                                paramCount(1)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(oaidRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            val q9bClass = resolver.resolveClass(
                cacheKey = "baidu:helios_q9_b_class",
                fastPathCandidates = listOf("q9.b"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        matcher {
                            usingStrings(".helios.ipc.default", ":helios")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value

            hookSimpleBlock(
                registry = registry,
                ruleId = oaidRuleId,
                hookId = "baidu.id.helios_q9_ipc",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:helios_q9_b_s",
                    clazz = q9bClass,
                    methodName = "S",
                    Bundle::class.java,
                    String::class.java,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings(".helios.ipc.default", ":helios")
                                }
                                paramTypes("android.os.Bundle", "java.lang.String")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(oaidRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = oaidRuleId,
                hookId = "baidu.id.helios_q9_collect",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:helios_q9_b_o",
                    fastPath = { cl ->
                        val n7aCls = SymbolResolver.loadClassOrNull("n7.a", cl) ?: return@resolveMethod null
                        q9bClass?.let { SymbolResolver.findDeclaredMethodOrNull(it, "O", n7aCls) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings(".helios.ipc.default", ":helios")
                                }
                                usingStrings(":helios")
                                paramCount(1)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(oaidRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }

        // 4) baidu.id.cuid_androidid_sofire
        val idRuleId = HookRuleCatalog.RULE_BAIDU_ID_CUID_ANDROIDID_SOFIRE
        registry.installRuleIfEnabled(idRuleId) {
            hookSimpleBlock(
                registry = registry,
                ruleId = idRuleId,
                hookId = "baidu.id.ime_common_param_upload",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:ime_common_param_upload",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("com.baidu.res.ImeCommonParam", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "uploadIdentifiers", Context::class.java) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.res")
                            matcher {
                                name = "uploadIdentifiers"
                                paramTypes("android.content.Context")
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(idRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = idRuleId,
                hookId = "baidu.id.mbd_store_upload",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:v9_a_run",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("v9.a", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "run") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                name = "run"
                                usingStrings("https://mbd.baidu.com/store")
                                paramCount(0)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(idRuleId) },
                blockedResult = { null }
            )

            val wi0Class = resolver.resolveClass(
                cacheKey = "baidu:wi0_a_class",
                fastPathCandidates = listOf("wi0.a"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        matcher {
                            usingStrings("com.baidu.input.privacy.impl.aspectj.PermissionAspect", "android_id")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value

            hookSimpleBlock(
                registry = registry,
                ruleId = idRuleId,
                hookId = "baidu.id.wi0_android_id",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:wi0_a_android_id",
                    fastPath = { cl ->
                        val tk1Cls = SymbolResolver.loadClassOrNull("tk1.c", cl) ?: return@resolveMethod null
                        wi0Class?.let { SymbolResolver.findDeclaredMethodOrNull(it, "b", tk1Cls) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("com.baidu.input.privacy.impl.aspectj.PermissionAspect")
                                }
                                usingStrings("android_id")
                                paramCount(1)
                                returnType("java.lang.Object")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(idRuleId) },
                blockedResult = { "" }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = idRuleId,
                hookId = "baidu.id.wi0_mac_bssid",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:wi0_a_mac_bssid",
                    fastPath = { cl ->
                        val tk1Cls = SymbolResolver.loadClassOrNull("tk1.c", cl) ?: return@resolveMethod null
                        wi0Class?.let { SymbolResolver.findDeclaredMethodOrNull(it, "d", tk1Cls) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("com.baidu.input.privacy.impl.aspectj.PermissionAspect")
                                }
                                usingStrings("getMacAddress")
                                paramCount(1)
                                returnType("java.lang.Object")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(idRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = idRuleId,
                hookId = "baidu.id.kq0_device_id",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:kq0_c_device_id",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("kq0.c", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "d", Context::class.java, String::class.java) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("no_device_sdk")
                                paramTypes("android.content.Context", "java.lang.String")
                                returnType("java.lang.String")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(idRuleId) },
                blockedResult = { "" },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            val cloudInfoClass = resolveCloudInfoClass(resolver)
            for (idGetter in listOf("get_oid", "get_aid", "get_cuid")) {
                hookSimpleBlock(
                    registry = registry,
                    ruleId = idRuleId,
                    hookId = "baidu.id.cloud_info_$idGetter",
                    resolveResult = resolveMethodOnClass(
                        resolver = resolver,
                        cacheKey = "baidu:cloud_info_$idGetter",
                        clazz = cloudInfoClass,
                        methodName = idGetter,
                        dexKitFinder = { bridge ->
                            bridge.findMethod {
                                searchPackages("com.baidu.input.ime.cloudinput")
                                matcher {
                                    name = idGetter
                                    paramCount(0)
                                    returnType("java.lang.String")
                                }
                            }.singleOrNull()?.descriptor
                        }
                    ),
                    shouldBlock = { isPointEnabled(idRuleId) },
                    blockedResult = { "" },
                    onFailureStatus = HookMatchStatus.DEGRADED
                )
            }

            hookSimpleBlock(
                registry = registry,
                ruleId = idRuleId,
                hookId = "baidu.id.cloud_info_editor_id",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:cloud_info_editor_id",
                    clazz = cloudInfoClass,
                    methodName = "getEditorId",
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.ime.cloudinput")
                            matcher {
                                name = "getEditorId"
                                paramCount(0)
                                returnType("int")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(idRuleId) },
                blockedResult = { 0 },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            // dj0.g0#e(String[]) + dj0.p#f33941v sanitization
            val g0eResult = resolver.resolveMethod(
                cacheKey = "baidu:dj0_g0_e_device_info",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("dj0.g0", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "e", Array<String>::class.java) }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            usingStrings("ua_cuid_uc_oaid_oid_ecs", "&is_double_cand=")
                            paramTypes("java.lang.String[]")
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val deviceInfoField = resolver.resolveField(
                cacheKey = "baidu:dj0_p_device_info_field",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("dj0.p", cl)
                        ?.let { findDeclaredFieldOrNull(it, "f33941v") }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            usingStrings("&cuid=emptycuid")
                            paramCount(0)
                            returnType("java.lang.String")
                        }
                    }.singleOrNull()
                        ?.usingFields
                        ?.map { it.field }
                        ?.firstOrNull { Modifier.isStatic(it.modifiers) && it.type.name == "java.lang.String" }
                        ?.descriptor
                }
            ).value
            registry.hookExecutable(
                ruleId = idRuleId,
                hookId = "baidu.id.dj0_g0_device_info",
                resolveResult = g0eResult,
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                val res = chain.proceed()
                if (isPointEnabled(idRuleId) && deviceInfoField != null) {
                    val raw = runCatching { deviceInfoField.get(null) as? String }.getOrNull()
                    val sanitized = BaiduJsonSanitizer.sanitizeDeviceInfoQuery(raw, true)
                    if (sanitized != raw) {
                        runCatching { deviceInfoField.set(null, sanitized) }
                        registry.incrementBlocked(idRuleId)
                    }
                }
                res
            }

            // com.baidu.input.b#b():String
            val inputBResult = resolver.resolveMethod(
                cacheKey = "baidu:input_b_device_info",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("com.baidu.input.b", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "b") }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.input")
                        matcher {
                            usingStrings("&cuid=emptycuid")
                            paramCount(0)
                            returnType("java.lang.String")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            registry.hookExecutable(
                ruleId = idRuleId,
                hookId = "baidu.id.input_b_cuid_strip",
                resolveResult = inputBResult,
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                val raw = chain.proceed() as? String
                if (!isPointEnabled(idRuleId)) {
                    return@hookExecutable raw
                }
                val sanitized = BaiduJsonSanitizer.sanitizeDeviceInfoQuery(raw, true)
                if (sanitized != raw) {
                    if (deviceInfoField != null) {
                        runCatching { deviceInfoField.set(null, sanitized) }
                    }
                    registry.incrementBlocked(idRuleId)
                }
                sanitized
            }

            // com.baidu.sofire.rp.Report
            val sofireClass = resolver.resolveClass(
                cacheKey = "baidu:sofire_rp_report",
                fastPathCandidates = listOf("com.baidu.sofire.rp.Report"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.sofire.rp")
                        matcher {
                            methodCount(6..20)
                        }
                    }.firstOrNull { it.name.endsWith(".Report") }?.descriptor
                }
            ).value
            for (methodName in listOf("r", "s", "sr", "fr", "n", "w", "i")) {
                registry.hookAllMethods(
                    ruleId = idRuleId,
                    hookIdPrefix = "baidu.id.sofire_$methodName",
                    clazz = sofireClass,
                    methodName = methodName
                ) { chain ->
                    if (isPointEnabled(idRuleId)) {
                        registry.incrementBlocked(idRuleId)
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }
        }

        // p4.a#F(Context):JSONArray (sappinfos) -> shared between baidu.id and baidu.scan.app_list
        val appListRuleId = HookRuleCatalog.RULE_BAIDU_SCAN_APP_LIST
        if (isPointEnabled(idRuleId) || isPointEnabled(appListRuleId)) {
            val primaryRuleId = if (isPointEnabled(idRuleId)) idRuleId else appListRuleId
            registry.hookExecutable(
                ruleId = primaryRuleId,
                hookId = "baidu.id.p4_sappinfos",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:p4_a_sappinfos",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("p4.a", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "F", Context::class.java) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("sids", "installts", "lstupdatets")
                                paramTypes("android.content.Context")
                                returnType("org.json.JSONArray")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                val blockById = isPointEnabled(idRuleId)
                val blockByApp = isPointEnabled(appListRuleId)
                if (blockById || blockByApp) {
                    registry.incrementBlocked(if (blockByApp) appListRuleId else idRuleId)
                    JSONArray()
                } else {
                    chain.proceed()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Telemetry rules: realtime_stats, periodic_ubc_mobstat, voice_and_ads, crash_dump
    // -------------------------------------------------------------------------

    private fun installTelemetryHooks(resolver: SymbolResolver, registry: HookRegistry) {
        // 5) baidu.telemetry.realtime_stats
        val realtimeRuleId = HookRuleCatalog.RULE_BAIDU_TELEMETRY_REALTIME_STATS
        registry.installRuleIfEnabled(realtimeRuleId) {
            val ayGClass = resolver.resolveClass(
                cacheKey = "baidu:ay_g_class",
                fastPathCandidates = listOf("ay.g"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        matcher {
                            usingStrings("Unknown RatingType: ", "bdst", "bdrl")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value

            hookSimpleBlock(
                registry = registry,
                ruleId = realtimeRuleId,
                hookId = "baidu.telemetry.ay_g_e",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:ay_g_e",
                    clazz = ayGClass,
                    methodName = "e",
                    Int::class.javaPrimitiveType!!,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("Unknown RatingType: ", "bdst")
                                }
                                usingStrings("skin.token")
                                paramTypes("int")
                                returnType("boolean")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(realtimeRuleId) },
                blockedResult = { false },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            val ayGAResult = resolver.resolveMethod(
                cacheKey = "baidu:ay_g_a",
                fastPath = { cl ->
                    val bCls = SymbolResolver.loadClassOrNull("gz0.b", cl) ?: return@resolveMethod null
                    ayGClass?.let { SymbolResolver.findDeclaredMethodOrNull(it, "a", ByteArray::class.java, bCls) }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            declaredClass {
                                usingStrings("Unknown RatingType: ", "bdst")
                            }
                            usingStrings("bdst", "bdrl")
                            paramCount(2)
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            registry.hookExecutable(
                ruleId = realtimeRuleId,
                hookId = "baidu.telemetry.ay_g_a",
                resolveResult = ayGAResult,
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                if (!isPointEnabled(realtimeRuleId)) {
                    return@hookExecutable chain.proceed()
                }
                notifyGz0Fail(chain.getArg(1))
                registry.incrementBlocked(realtimeRuleId)
                null
            }

            val statsUploaderResult = resolver.resolveMethod(
                cacheKey = "baidu:stats_uploader_a",
                fastPath = { cl ->
                    val cls = SymbolResolver.loadClassOrNull("com.baidu.stats.impl.StatsUploader", cl)
                        ?: return@resolveMethod null
                    val bCls = SymbolResolver.loadClassOrNull("gz0.b", cl) ?: return@resolveMethod null
                    SymbolResolver.findDeclaredMethodOrNull(cls, "a", ByteArray::class.java, bCls)
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.stats.impl")
                        matcher {
                            paramTypes("byte[]", "gz0.b")
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            registry.hookExecutable(
                ruleId = realtimeRuleId,
                hookId = "baidu.telemetry.stats_uploader_a",
                resolveResult = statsUploaderResult,
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                if (!isPointEnabled(realtimeRuleId)) {
                    return@hookExecutable chain.proceed()
                }
                notifyGz0Fail(chain.getArg(1))
                registry.incrementBlocked(realtimeRuleId)
                null
            }

            val realtimeClass = resolver.resolveClass(
                cacheKey = "baidu:stats_realtime_a",
                fastPathCandidates = listOf("com.baidu.stats.realtime.a"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.stats.realtime")
                        matcher {
                            usingStrings("ime_stats.stats_impl.realtimedurationreportservice.duration_draft_cache_v1")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            for (methodName in listOf("c", "f")) {
                registry.hookAllMethods(
                    ruleId = realtimeRuleId,
                    hookIdPrefix = "baidu.telemetry.stats_realtime_$methodName",
                    clazz = realtimeClass,
                    methodName = methodName
                ) { chain ->
                    if (isPointEnabled(realtimeRuleId)) {
                        registry.incrementBlocked(realtimeRuleId)
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }

            // ju.a#k0():void -> block cleartext http://r.bd.sec.miui.com daily Xiaomi stat upload.
            hookSimpleBlock(
                registry = registry,
                ruleId = realtimeRuleId,
                hookId = "baidu.telemetry.mi_daily_stat",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:ju_a_k0",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("ju.a", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "k0") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("sug_mi_statistic")
                                returnType("void")
                                paramCount(0)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(realtimeRuleId) },
                blockedResult = { null }
            )
        }

        // 6) baidu.telemetry.periodic_ubc_mobstat
        val periodicRuleId = HookRuleCatalog.RULE_BAIDU_TELEMETRY_PERIODIC_UBC_MOBSTAT
        registry.installRuleIfEnabled(periodicRuleId) {
            // sj0.a#b():boolean -> block ONLY selector == 14 (KP upload); preserve case 9 dict sync!
            val sj0MethodResult = resolver.resolveMethod(
                cacheKey = "baidu:sj0_a_b",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("sj0.a", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "b") }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            usingStrings("key_upload_kp_data_time")
                            returnType("boolean")
                            paramCount(0)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookIntSelectorTask(
                resolver = resolver,
                registry = registry,
                ruleId = periodicRuleId,
                hookId = "baidu.telemetry.sj0_case14",
                methodResult = sj0MethodResult,
                fieldCacheKey = "baidu:sj0_a_selector",
                primaryFieldName = "b",
                onMissingFieldDetail = "sj0.a selector field unresolved; keeping unhooked to preserve case 9",
                onFailureStatus = HookMatchStatus.FAILED,
                shouldBlockSelector = { selector ->
                    BaiduJsonSanitizer.shouldBlockSj0Task(selector, isPointEnabled(periodicRuleId))
                },
                blockedResult = { false }
            )

            // com.baidu.ubc.BehaviorUploader#obUploadDataRequest & uploadDataRequest overloads.
            val ubcUploaderResult = resolver.resolveClass(
                cacheKey = "baidu:behavior_uploader",
                fastPathCandidates = listOf("com.baidu.ubc.BehaviorUploader"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.ubc")
                        matcher {
                            usingStrings("CRONET")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val uploaderCls = ubcUploaderResult.value
            for (methodName in listOf("obUploadDataRequest", "uploadDataRequest")) {
                registry.hookAllMethods(
                    ruleId = periodicRuleId,
                    hookIdPrefix = "baidu.telemetry.ubc_$methodName",
                    clazz = uploaderCls,
                    methodName = methodName,
                    source = ubcUploaderResult.source.name
                ) { chain ->
                    if (isPointEnabled(periodicRuleId)) {
                        registry.incrementBlocked(periodicRuleId)
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }

            // com.baidu.mobstat.LogSender#saveLogData & onSend
            val logSenderClass = resolver.resolveClass(
                cacheKey = "baidu:mobstat_log_sender",
                fastPathCandidates = listOf("com.baidu.mobstat.LogSender"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.mobstat")
                        matcher {
                            usingStrings("__track_send_data_")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            hookSimpleBlock(
                registry = registry,
                ruleId = periodicRuleId,
                hookId = "baidu.telemetry.mobstat_save_log",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:mobstat_save_log_data",
                    clazz = logSenderClass,
                    methodName = "saveLogData",
                    Context::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType!!,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.mobstat")
                            matcher {
                                usingStrings("__track_send_data_")
                                paramTypes("android.content.Context", "java.lang.String", "boolean")
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(periodicRuleId) },
                blockedResult = { null }
            )
            val hasOnSend = logSenderClass != null && runCatching {
                logSenderClass.declaredMethods.any { it.name == "onSend" }
            }.getOrDefault(false)
            if (hasOnSend) {
                registry.hookAllMethods(
                    ruleId = periodicRuleId,
                    hookIdPrefix = "baidu.telemetry.mobstat_on_send",
                    clazz = logSenderClass,
                    methodName = "onSend"
                ) { chain ->
                    if (isPointEnabled(periodicRuleId)) {
                        registry.incrementBlocked(periodicRuleId)
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }
        }

        // 7) baidu.telemetry.voice_and_ads (fixes voicetrace.a#b/d StackOverflowError + blocks ads)
        val voiceAdsRuleId = HookRuleCatalog.RULE_BAIDU_TELEMETRY_VOICE_AND_ADS
        registry.installRuleIfEnabled(voiceAdsRuleId) {
            val voiceTraceClass = resolveVoiceTraceClass(resolver)
            val traceBeanField: Field? = if (voiceTraceClass != null) {
                resolver.resolveField(
                    cacheKey = "baidu:voice_trace_bean_field",
                    fastPath = { _ -> findDeclaredFieldOrNull(voiceTraceClass, "f15013d") },
                    structuralFallback = { cl ->
                        val beanCls = SymbolResolver.loadClassOrNull(
                            "com.baidu.input.ime.voicerecognize.voicetrace.TraceBean",
                            cl
                        )
                        if (beanCls == null) null else findStaticFieldByType(voiceTraceClass, beanCls)
                    }
                ).value
            } else {
                null
            }

            // voicetrace.a#d(TraceBean): MUST clear static f15013d = null so b() cannot recurse infinitely!
            val vtUploadResult = resolver.resolveMethod(
                cacheKey = "baidu:voice_trace_upload",
                fastPath = { cl ->
                    val traceBeanCls = SymbolResolver.loadClassOrNull(
                        "com.baidu.input.ime.voicerecognize.voicetrace.TraceBean",
                        cl
                    ) ?: return@resolveMethod null
                    voiceTraceClass?.let { SymbolResolver.findDeclaredMethodOrNull(it, "d", traceBeanCls) }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.input.ime.voicerecognize.voicetrace")
                        matcher {
                            usingStrings("traceBean : ")
                            paramCount(1)
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            registry.hookExecutable(
                ruleId = voiceAdsRuleId,
                hookId = "baidu.telemetry.voice_trace_upload",
                resolveResult = vtUploadResult,
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                val block = isPointEnabled(voiceAdsRuleId) || !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
                if (!block) {
                    return@hookExecutable chain.proceed()
                }
                runCatching { traceBeanField?.set(null, null) }
                registry.incrementBlocked(voiceAdsRuleId)
                null
            }

            // voicetrace.a#b(int, String, CharSequence, CharSequence): short-circuit & clear f15013d.
            val vtBResult = resolveMethodOnClass(
                resolver = resolver,
                cacheKey = "baidu:voice_trace_b",
                clazz = voiceTraceClass,
                methodName = "b",
                Int::class.javaPrimitiveType!!,
                String::class.java,
                CharSequence::class.java,
                CharSequence::class.java,
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.input.ime.voicerecognize.voicetrace")
                        matcher {
                            declaredClass {
                                usingStrings("wufeiyang")
                            }
                            paramTypes("int", "java.lang.String", "java.lang.CharSequence", "java.lang.CharSequence")
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            registry.hookExecutable(
                ruleId = voiceAdsRuleId,
                hookId = "baidu.telemetry.voice_trace_b",
                resolveResult = vtBResult,
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                val block = isPointEnabled(voiceAdsRuleId) || !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
                if (!block) {
                    return@hookExecutable chain.proceed()
                }
                runCatching { traceBeanField?.set(null, null) }
                registry.incrementBlocked(voiceAdsRuleId)
                null
            }

            // rj0.a#b() selector 0 (ads upload) + countDown latch safety
            val rj0AMethodResult = resolver.resolveMethod(
                cacheKey = "baidu:rj0_a_b",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("rj0.a", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "b") }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            usingStrings("BaseUpdateCommand: ", "no file exist,no upload")
                            returnType("void")
                            paramCount(0)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val rj0ASelectorField = rj0AMethodResult.value?.declaringClass?.let { cls ->
                resolver.resolveField(
                    cacheKey = "baidu:rj0_a_selector",
                    fastPath = { _ -> findDeclaredFieldOrNull(cls, "f60199d") },
                    structuralFallback = { _ -> findSingleInstanceIntField(cls) }
                ).value
            }
            val rj0CCountDownMethod = rj0AMethodResult.value?.declaringClass?.superclass?.let { base ->
                SymbolResolver.findDeclaredMethodOrNull(base, "c")
            }
            if (rj0AMethodResult.value != null && rj0ASelectorField != null) {
                registry.hookExecutable(
                    ruleId = voiceAdsRuleId,
                    hookId = "baidu.telemetry.rj0_a_ads",
                    resolveResult = rj0AMethodResult,
                    onFailureStatus = HookMatchStatus.DEGRADED
                ) { chain ->
                    val instance = chain.thisObject
                    val selector = if (instance != null) {
                        runCatching<Int?> { rj0ASelectorField.getInt(instance) }.getOrNull()
                    } else {
                        null
                    }
                    if (selector == 0 && isPointEnabled(voiceAdsRuleId)) {
                        runCatching { rj0CCountDownMethod?.invoke(instance) }
                        registry.incrementBlocked(voiceAdsRuleId)
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }

            // b7.c#w(int, int, int, int, String) & rh0.a#a()
            hookSimpleBlock(
                registry = registry,
                ruleId = voiceAdsRuleId,
                hookId = "baidu.telemetry.ads_b7_w",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:b7_c_w",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("b7.c", cl)?.let {
                            SymbolResolver.findDeclaredMethodOrNull(
                                it,
                                "w",
                                Int::class.javaPrimitiveType!!,
                                Int::class.javaPrimitiveType!!,
                                Int::class.javaPrimitiveType!!,
                                Int::class.javaPrimitiveType!!,
                                String::class.java
                            )
                        }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("ads_log")
                                }
                                paramTypes("int", "int", "int", "int", "java.lang.String")
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(voiceAdsRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = voiceAdsRuleId,
                hookId = "baidu.telemetry.ad_notification_runner",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:rh0_a_ad_notif",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("rh0.a", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "a") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("ADNotificationRunner Time: ")
                                paramCount(0)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(voiceAdsRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }

        // 8) baidu.telemetry.crash_dump (never touches harmless rj0.d or dj0.h)
        val crashRuleId = HookRuleCatalog.RULE_BAIDU_TELEMETRY_CRASH_DUMP
        registry.installRuleIfEnabled(crashRuleId) {
            hookSimpleBlock(
                registry = registry,
                ruleId = crashRuleId,
                hookId = "baidu.crash.xcrash_init",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:xcrash_b_a",
                    fastPath = { cl ->
                        val bCls = SymbolResolver.loadClassOrNull("xcrash.b", cl) ?: return@resolveMethod null
                        val fCls = SymbolResolver.loadClassOrNull("zl1.f", cl) ?: return@resolveMethod null
                        SymbolResolver.findDeclaredMethodOrNull(bCls, "a", Application::class.java, fCls)
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("xcrash")
                            matcher {
                                usingStrings("/tombstones")
                                paramCount(2)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(crashRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            val zeusHandlerClass = resolver.resolveClass(
                cacheKey = "baidu:zeus_crash_handler",
                fastPathCandidates = listOf("com.baidu.webkit.sdk.dumper.ZeusCrashHandler"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.webkit.sdk.dumper")
                        matcher {
                            usingStrings("ZeusCrashHandler")
                        }
                    }.firstOrNull()?.descriptor
                }
            ).value

            hookSimpleBlock(
                registry = registry,
                ruleId = crashRuleId,
                hookId = "baidu.crash.zeus_init",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:zeus_crash_init",
                    clazz = zeusHandlerClass,
                    methodName = "init",
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.webkit.sdk.dumper")
                            matcher {
                                name = "init"
                                paramCount(0)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(crashRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = crashRuleId,
                hookId = "baidu.crash.zeus_log_exception",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:zeus_crash_log_ex",
                    clazz = zeusHandlerClass,
                    methodName = "logException",
                    Throwable::class.java,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.webkit.sdk.dumper")
                            matcher {
                                name = "logException"
                                paramTypes("java.lang.Throwable")
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(crashRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = crashRuleId,
                hookId = "baidu.crash.zeus_upload_log",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:zeus_crash_upload_log",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull(
                            "com.baidu.webkit.sdk.dumper.ZeusCrashHandler\$ZeusCrashHandlerClient",
                            cl
                        )?.let { SymbolResolver.findDeclaredMethodOrNull(it, "onUploadLogFile", File::class.java) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.webkit.sdk.dumper")
                            matcher {
                                name = "onUploadLogFile"
                                paramTypes("java.io.File")
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(crashRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = crashRuleId,
                hookId = "baidu.crash.c9_f_init",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:c9_f_crash_init",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("c9.f", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "a") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("1.2.3-beta456-patch789")
                                paramCount(0)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(crashRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = crashRuleId,
                hookId = "baidu.crash.c9_i_upload",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:c9_i_crash_upload",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("c9.i", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "U", String::class.java, Boolean::class.javaPrimitiveType!!) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("c9")
                            matcher {
                                name = "U"
                                paramTypes("java.lang.String", "boolean")
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(crashRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }
    }

    // -------------------------------------------------------------------------
    // Cloud & Context rules:
    //   RULE_BAIDU_CLOUD_STREAM_GATE, RULE_BAIDU_CLOUD_PAYLOAD_SANITIZE,
    //   RULE_BAIDU_CONTEXT_CURSOR_AND_AI, RULE_BAIDU_CONTEXT_HOST_PACKAGE
    // -------------------------------------------------------------------------

    private fun installCloudAndContextHooks(resolver: SymbolResolver, registry: HookRegistry) {
        val streamGateRuleId = HookRuleCatalog.RULE_BAIDU_CLOUD_STREAM_GATE
        val realtimeRuleId = HookRuleCatalog.RULE_BAIDU_TELEMETRY_REALTIME_STATS
        val payloadRuleId = HookRuleCatalog.RULE_BAIDU_CLOUD_PAYLOAD_SANITIZE
        val clipBgRuleId = HookRuleCatalog.RULE_BAIDU_CLIP_BACKGROUND_AND_DB
        val cursorRuleId = HookRuleCatalog.RULE_BAIDU_CONTEXT_CURSOR_AND_AI
        val hostPkgRuleId = HookRuleCatalog.RULE_BAIDU_CONTEXT_HOST_PACKAGE

        // 9) baidu.cloud.stream_gate: x00.b#c() + IptCloudStream#send
        // Global Cloud Input (`纯本地`) ALWAYS blocks cloud input while Baidu is managed, even if
        // `RULE_BAIDU_CLOUD_STREAM_GATE` is toggled off; the rule toggle controls diagnostic gating.
        val targetManaged = ConfigBridge.isTargetManaged(ImeTarget.BAIDU)
        val streamGateEnabledAtInstall = isPointEnabled(streamGateRuleId)
        if (!targetManaged) {
            registry.reportSkipped(streamGateRuleId, "已暂停接管")
        } else {
            hookSimpleBlock(
                registry = registry,
                ruleId = streamGateRuleId,
                hookId = "baidu.cloud.sug_switch",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:x00_b_c",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("x00.b", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "c") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("sug_info", "close -> sugSwitch close ")
                                returnType("boolean")
                                paramCount(0)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = {
                    ConfigBridge.isTargetManaged(ImeTarget.BAIDU) && !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
                },
                blockedResult = { false }
            )

            // Shared IptCloudStream#send(int, byte[], int):int (types 4/5 -> realtime_stats, else -> global cloud gate)
            val primaryRuleId = if (streamGateEnabledAtInstall || !isPointEnabled(realtimeRuleId)) {
                streamGateRuleId
            } else {
                realtimeRuleId
            }
            val iptSendResult = resolver.resolveMethod(
                cacheKey = "baidu:ipt_cloud_stream_send",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("com.baidu.iptcore.net.IptCloudStream", cl)
                        ?.let {
                            SymbolResolver.findDeclaredMethodOrNull(
                                it,
                                "send",
                                Int::class.javaPrimitiveType!!,
                                ByteArray::class.java,
                                Int::class.javaPrimitiveType!!
                            )
                        }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            usingStrings("cfile", "log_type=7")
                            returnType("int")
                            paramTypes("int", "byte[]", "int")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            registry.hookExecutable(
                ruleId = primaryRuleId,
                hookId = "baidu.cloud.ipt_stream_send",
                resolveResult = iptSendResult
            ) { chain ->
                val type = (chain.getArg(0) as? Int) ?: -1
                val cloudAllowed = if (ConfigBridge.isTargetManaged(ImeTarget.BAIDU)) {
                    ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
                } else {
                    true
                }
                when (
                    BaiduJsonSanitizer.decideIptCloudStreamSend(
                        type = type,
                        blockTelemetry = isPointEnabled(realtimeRuleId),
                        cloudEnabled = cloudAllowed
                    )
                ) {
                    StreamSendDecision.BLOCK_TELEMETRY -> {
                        registry.incrementBlocked(realtimeRuleId)
                        0
                    }
                    StreamSendDecision.BLOCK_CLOUD_DISABLED -> {
                        registry.incrementBlocked(streamGateRuleId)
                        0
                    }
                    StreamSendDecision.PROCEED -> chain.proceed()
                }
            }

            if (!streamGateEnabledAtInstall) {
                registry.reportSkipped(streamGateRuleId, "规则已关闭")
            }
        }

        val cloudInfoClass = resolveCloudInfoClass(resolver)

        // 10) baidu.cloud.payload_sanitize: CloudInfo#get_city & CloudInfo#get_bd_exist
        registry.installRuleIfEnabled(payloadRuleId) {
            hookSimpleBlock(
                registry = registry,
                ruleId = payloadRuleId,
                hookId = "baidu.cloud.info_city",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:cloud_info_city",
                    clazz = cloudInfoClass,
                    methodName = "get_city",
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.ime.cloudinput")
                            matcher {
                                name = "get_city"
                                paramCount(0)
                                returnType("java.lang.String")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(payloadRuleId) },
                blockedResult = { "" }
            )
        }

        // Shared CloudInfo#get_json_buf():byte[] (zeros report.cpn under clip.background_and_db / payload_sanitize)
        if (isPointEnabled(payloadRuleId) || isPointEnabled(clipBgRuleId)) {
            val primaryRuleId = if (isPointEnabled(payloadRuleId)) payloadRuleId else clipBgRuleId
            registry.hookExecutable(
                ruleId = primaryRuleId,
                hookId = "baidu.cloud.info_json_buf",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:cloud_info_json_buf",
                    clazz = cloudInfoClass,
                    methodName = "get_json_buf",
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.ime.cloudinput")
                            matcher {
                                name = "get_json_buf"
                                paramCount(0)
                                returnType("byte[]")
                            }
                        }.singleOrNull()?.descriptor
                    }
                )
            ) { chain ->
                val original = chain.proceed() as? ByteArray ?: return@hookExecutable null
                val zeroCpn = isPointEnabled(clipBgRuleId) || isPointEnabled(payloadRuleId)
                val sanitized = BaiduJsonSanitizer.sanitizeCloudInfoJson(
                    bytes = original,
                    zeroClipboardCount = zeroCpn
                )
                if (sanitized !== original) {
                    registry.incrementBlocked(if (isPointEnabled(clipBgRuleId)) clipBgRuleId else payloadRuleId)
                }
                sanitized
            }
        }

        // 11) baidu.context.cursor_and_ai
        registry.installRuleIfEnabled(cursorRuleId) {
            val imeEnvClass = resolveImeEnvCallbackClass(resolver)
            for (methodName in listOf(
                "getEditBeforeCursor",
                "getEditAfterCursor",
                "getEditSelection",
                "getEditTextDialogue"
            )) {
                hookSimpleBlock(
                    registry = registry,
                    ruleId = cursorRuleId,
                    hookId = "baidu.context.$methodName",
                    resolveResult = resolveMethodOnClass(
                        resolver = resolver,
                        cacheKey = "baidu:ime_env_$methodName",
                        clazz = imeEnvClass,
                        methodName = methodName,
                        Int::class.javaPrimitiveType!!,
                        dexKitFinder = { bridge ->
                            bridge.findMethod {
                                matcher {
                                    declaredClass {
                                        usingStrings("isAllowAiPadAutoOpen type:")
                                    }
                                    name = methodName
                                    paramTypes("int")
                                    returnType("java.lang.String")
                                }
                            }.singleOrNull()?.descriptor
                        }
                    ),
                    shouldBlock = { isPointEnabled(cursorRuleId) },
                    blockedResult = { "" },
                    onFailureStatus = HookMatchStatus.DEGRADED
                )
            }

            // VoiceTrace surrounding cursor context
            val voiceTraceClass = resolveVoiceTraceClass(resolver)
            hookSimpleBlock(
                registry = registry,
                ruleId = cursorRuleId,
                hookId = "baidu.cloud.voice_trace_context",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:voice_trace_context",
                    clazz = voiceTraceClass,
                    methodName = "c",
                    String::class.java,
                    CharSequence::class.java,
                    CharSequence::class.java,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.ime.voicerecognize.voicetrace")
                            matcher {
                                declaredClass {
                                    usingStrings("wufeiyang")
                                }
                                paramTypes("java.lang.String", "java.lang.CharSequence", "java.lang.CharSequence")
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = {
                    isPointEnabled(cursorRuleId) || !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
                },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            // Wenxin 210-char dialogue context reader & pre-stream uploader
            hookSimpleBlock(
                registry = registry,
                ruleId = cursorRuleId,
                hookId = "baidu.cloud.wenxin_context_reader",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:wenxin_context_210",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("com.baidu.input.aicard.impl.uploader.b", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "b", Boolean::class.javaPrimitiveType!!) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.aicard.impl.uploader")
                            matcher {
                                paramTypes("boolean")
                                returnType("java.lang.String")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = {
                    isPointEnabled(cursorRuleId) || !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
                },
                blockedResult = { "" },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            val wenxinUploaderClass = resolver.resolveClass(
                cacheKey = "baidu:wenxin_uploader",
                fastPathCandidates = listOf("com.baidu.input.aicard.impl.uploader.a"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.input.aicard.impl.uploader")
                        matcher {
                            usingStrings("WenxinDataUploader.kt", "wenXinData")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value

            for (methodName in listOf("g", "h")) {
                hookSimpleBlock(
                    registry = registry,
                    ruleId = cursorRuleId,
                    hookId = "baidu.cloud.wenxin_uploader_$methodName",
                    resolveResult = resolver.resolveMethod(
                        cacheKey = "baidu:wenxin_uploader_$methodName",
                        fastPath = { cl ->
                            val paramCls = SymbolResolver.loadClassOrNull("pg.b", cl) ?: return@resolveMethod null
                            wenxinUploaderClass?.let {
                                SymbolResolver.findDeclaredMethodOrNull(it, methodName, paramCls)
                            }
                        },
                        dexKitFinder = { bridge ->
                            bridge.findMethod {
                                searchPackages("com.baidu.input.aicard.impl.uploader")
                                matcher {
                                    declaredClass {
                                        usingStrings("WenxinDataUploader.kt")
                                    }
                                    if (methodName == "h") {
                                        usingStrings("wenXinData", "uploadFailedDatas.iterator()")
                                    } else {
                                        addCaller(
                                            MethodMatcher().apply {
                                                usingStrings("wenXinData", "uploadFailedDatas.iterator()")
                                            }
                                        )
                                    }
                                    paramCount(1)
                                    returnType("void")
                                }
                            }.singleOrNull()?.descriptor
                        }
                    ),
                    shouldBlock = {
                        isPointEnabled(cursorRuleId) || !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
                    },
                    blockedResult = { null },
                    onFailureStatus = HookMatchStatus.DEGRADED
                )
            }
        }

        // 12) baidu.context.host_package
        registry.installRuleIfEnabled(hostPkgRuleId) {
            for (hostGetter in listOf("getSugPackageInfo", "get_app_name")) {
                hookSimpleBlock(
                    registry = registry,
                    ruleId = hostPkgRuleId,
                    hookId = "baidu.cloud.info_$hostGetter",
                    resolveResult = resolveMethodOnClass(
                        resolver = resolver,
                        cacheKey = "baidu:cloud_info_$hostGetter",
                        clazz = cloudInfoClass,
                        methodName = hostGetter,
                        dexKitFinder = { bridge ->
                            bridge.findMethod {
                                searchPackages("com.baidu.input.ime.cloudinput")
                                matcher {
                                    name = hostGetter
                                    paramCount(0)
                                    returnType("java.lang.String")
                                }
                            }.singleOrNull()?.descriptor
                        }
                    ),
                    shouldBlock = { isPointEnabled(hostPkgRuleId) },
                    blockedResult = { "" }
                )
            }

            hookSimpleBlock(
                registry = registry,
                ruleId = hostPkgRuleId,
                hookId = "baidu.context.cloud_log_pkg_name",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:cloud_log_pkg_name",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("com.baidu.input.ime.cloudinput.CloudLog", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "get_pkg_name") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.ime.cloudinput")
                            matcher {
                                name = "get_pkg_name"
                                paramCount(0)
                                returnType("java.lang.String")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(hostPkgRuleId) },
                blockedResult = { "" },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }
    }

    // -------------------------------------------------------------------------
    // Clipboard rules: token_upload_and_js, background_and_db, strict_block_readers
    // -------------------------------------------------------------------------

    private fun resolvePrivacyInputModeField(resolver: SymbolResolver): Field? =
        resolver.resolveField(
            cacheKey = "baidu:dj0_l_privacy_input_mode",
            fastPath = { cl ->
                SymbolResolver.loadClassOrNull("dj0.l", cl)?.let { findDeclaredFieldOrNull(it, "I1") }
            },
            dexKitFinder = { bridge ->
                bridge.findMethod {
                    matcher {
                        usingStrings("isNeedShowFlowDialog = ", ",isPrivacyInputMode")
                        returnType("boolean")
                        paramCount(0)
                    }
                }.singleOrNull()
                    ?.usingFields
                    ?.map { it.field }
                    ?.singleOrNull { Modifier.isStatic(it.modifiers) && it.type.name == "boolean" }
                    ?.descriptor
            }
        ).value

    private fun installClipboardHooks(
        resolver: SymbolResolver,
        classLoader: ClassLoader,
        appContextWrapper: ContextWrapper?,
        registry: HookRegistry,
        privacyAgreedMethod: Method?,
        privacyInputModeField: Field?
    ) {
        val tokenJsRuleId = HookRuleCatalog.RULE_BAIDU_CLIP_TOKEN_UPLOAD_AND_JS
        val bgDbRuleId = HookRuleCatalog.RULE_BAIDU_CLIP_BACKGROUND_AND_DB
        val strictReaderRuleId = HookRuleCatalog.RULE_BAIDU_CLIP_STRICT_BLOCK_READERS

        fun readPrivacyTuple(): Pair<Boolean, Boolean> {
            val agreed = readPrivacyAgreed(privacyAgreedMethod)
            val privacyInputMode = if (privacyInputModeField != null) {
                runCatching { privacyInputModeField.getBoolean(null) }.getOrNull() ?: false
            } else {
                false
            }
            return agreed to privacyInputMode
        }

        // 13) baidu.clip.token_upload_and_js (blocks even in consented mode!)
        registry.installRuleIfEnabled(tokenJsRuleId) {
            hookSimpleBlock(
                registry = registry,
                ruleId = tokenJsRuleId,
                hookId = "baidu.clip.token_upload_ch0",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:ch0_b_token_upload",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("ch0.b", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "b") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("pref_key_clip_operation_token")
                                paramCount(0)
                                returnType("java.lang.String")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = {
                    val (agreed, privMode) = readPrivacyTuple()
                    BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                        clipRuleEnabled = isPointEnabled(tokenJsRuleId),
                        isBackgroundOrTokenOrJs = true,
                        privacyAgreed = agreed,
                        privacyInputMode = privMode
                    )
                },
                blockedResult = { "" }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = tokenJsRuleId,
                hookId = "baidu.clip.ff_s_read",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:ff_s_clipboard_b",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("ff.s", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "b") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("Utils.kt", "create<String> {emitter")
                                }
                                paramCount(0)
                                returnType("java.lang.String")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = {
                    val (agreed, privMode) = readPrivacyTuple()
                    BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                        clipRuleEnabled = isPointEnabled(tokenJsRuleId),
                        isBackgroundOrTokenOrJs = true,
                        privacyAgreed = agreed,
                        privacyInputMode = privMode
                    )
                },
                blockedResult = { "" },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            val talosClipResult = resolver.resolveMethod(
                cacheKey = "baidu:talos_clipboard_get_string",
                fastPath = { cl ->
                    val modCls = SymbolResolver.loadClassOrNull(
                        "com.baidu.talos.core.modules.clipboard.ClipboardModule",
                        cl
                    ) ?: return@resolveMethod null
                    val cbCls = SymbolResolver.loadClassOrNull("xz0.c", cl) ?: return@resolveMethod null
                    SymbolResolver.findDeclaredMethodOrNull(modCls, "getString", cbCls)
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.talos.core.modules.clipboard")
                        matcher {
                            name = "getString"
                            paramCount(1)
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            registry.hookExecutable(
                ruleId = tokenJsRuleId,
                hookId = "baidu.clip.talos_get_string",
                resolveResult = talosClipResult,
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                val (agreed, privMode) = readPrivacyTuple()
                val block = BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                    clipRuleEnabled = isPointEnabled(tokenJsRuleId),
                    isBackgroundOrTokenOrJs = true,
                    privacyAgreed = agreed,
                    privacyInputMode = privMode
                )
                if (!block) {
                    return@hookExecutable chain.proceed()
                }
                val callback = chain.getArg(0)
                if (callback != null) {
                    runCatching {
                        callback.javaClass.getMethod("e", Any::class.java).invoke(callback, "")
                    }
                }
                registry.incrementBlocked(tokenJsRuleId)
                null
            }

            val sapiPasteClass = resolver.resolveClass(
                cacheKey = "baidu:sapi_js_pasteboard",
                fastPathCandidates = listOf("com.baidu.sapi2.SapiJsInterpreters\$AddressManageGetPasteboard"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.sapi2")
                        matcher {
                            className = "com.baidu.sapi2.SapiJsInterpreters\$AddressManageGetPasteboard"
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            registry.hookAllMethods(
                ruleId = tokenJsRuleId,
                hookIdPrefix = "baidu.clip.sapi_js_pasteboard",
                clazz = sapiPasteClass,
                methodName = "interpret"
            ) { chain ->
                val (agreed, privMode) = readPrivacyTuple()
                val block = BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                    clipRuleEnabled = isPointEnabled(tokenJsRuleId),
                    isBackgroundOrTokenOrJs = true,
                    privacyAgreed = agreed,
                    privacyInputMode = privMode
                )
                if (block) {
                    registry.incrementBlocked(tokenJsRuleId)
                    """{"errno":0,"paste":""}"""
                } else {
                    chain.proceed()
                }
            }
        }

        // 14) baidu.clip.background_and_db (blocks silent listeners & DB writes even in consented mode)
        registry.installRuleIfEnabled(bgDbRuleId) {
            val shouldBlockBg: () -> Boolean = {
                val (agreed, privMode) = readPrivacyTuple()
                BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                    clipRuleEnabled = isPointEnabled(bgDbRuleId),
                    isBackgroundOrTokenOrJs = true,
                    privacyAgreed = agreed,
                    privacyInputMode = privMode
                )
            }

            hookSimpleBlock(
                registry = registry,
                ruleId = bgDbRuleId,
                hookId = "baidu.clipboard.global_listener",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:clipboard_global_listener",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("com.baidu.input.clipboard.b", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "onPrimaryClipChanged") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.clipboard")
                            matcher {
                                name = "onPrimaryClipChanged"
                                usingStrings("ClipboardGlobal", "privacy not agreed")
                                returnType("void")
                                paramCount(0)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = shouldBlockBg,
                blockedResult = { null }
            )

            val blClass = resolver.resolveClass(
                cacheKey = "baidu:bl_c_class",
                fastPathCandidates = listOf("bl.c"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        matcher {
                            usingStrings("ImeInspirationCorpusDependencyImpl.kt")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            val blContextField: Field? = blClass?.superclass?.let { parent ->
                findDeclaredFieldOrNull(parent, "a")
                    ?: runCatching {
                        parent.declaredFields.firstOrNull { ContextWrapper::class.java.isAssignableFrom(it.type) }
                            ?.apply { isAccessible = true }
                    }.getOrNull()
            }
            val xiaoMiClipCtor: Constructor<*>? = SymbolResolver.loadClassOrNull(
                "com.baidu.input.inspiration_corpus.api.XiaoMiClipData",
                classLoader
            )?.let { cls ->
                runCatching {
                    cls.getDeclaredConstructor(ContextWrapper::class.java).apply { isAccessible = true }
                }.getOrNull()
            }

            registry.hookExecutable(
                ruleId = bgDbRuleId,
                hookId = "baidu.clipboard.bl_capture_k1",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:bl_c_k1",
                    clazz = blClass,
                    methodName = "K1",
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("ImeInspirationCorpusDependencyImpl.kt")
                                }
                                usingStrings("text/html")
                                paramCount(0)
                                returnType("com.baidu.input.inspiration_corpus.api.XiaoMiClipData")
                            }
                        }.singleOrNull()?.descriptor
                    }
                )
            ) { chain ->
                if (!shouldBlockBg()) {
                    return@hookExecutable chain.proceed()
                }
                registry.incrementBlocked(bgDbRuleId)
                val ctxWrapper = chain.thisObject?.let { self ->
                    runCatching { blContextField?.get(self) as? ContextWrapper }.getOrNull()
                } ?: appContextWrapper
                if (ctxWrapper != null && xiaoMiClipCtor != null) {
                    runCatching<Any?> { xiaoMiClipCtor.newInstance(ctxWrapper) }.getOrNull()
                } else {
                    null
                }
            }

            hookSimpleBlock(
                registry = registry,
                ruleId = bgDbRuleId,
                hookId = "baidu.clipboard.manager_gate_j",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:clipboard_manager_j",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("com.baidu.input.clipboard.manager.a", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "j") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.clipboard.manager")
                            matcher {
                                returnType("boolean")
                                paramCount(0)
                                addInvoke(
                                    MethodMatcher().apply {
                                        name = "isPrivacyAgreed"
                                        returnType("boolean")
                                        paramCount(0)
                                    }
                                )
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = shouldBlockBg,
                blockedResult = { false }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = bgDbRuleId,
                hookId = "baidu.clipboard.db_insert",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:clipboard_db_insert_j1",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("com.baidu.input.clipboard.datamanager.clipboard.a", cl)
                            ?.let {
                                SymbolResolver.findDeclaredMethodOrNull(
                                    it,
                                    "J1",
                                    String::class.java,
                                    Boolean::class.javaPrimitiveType!!
                                )
                            }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.clipboard.datamanager.clipboard")
                            matcher {
                                declaredClass {
                                    usingStrings("ClipboardDataManagerImpl", "safeDelete exception")
                                }
                                paramTypes("java.lang.String", "boolean")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = shouldBlockBg,
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }

        // 15) baidu.clip.strict_block_readers (opt-in, default=false): wg0.b#u(ContextWrapper)
        if (!isPointEnabled(strictReaderRuleId)) {
            registry.reportSkipped(strictReaderRuleId, "规则已关闭")
        }
        if (isPointEnabled(strictReaderRuleId) || isPointEnabled(bgDbRuleId)) {
            val primaryRuleId = if (isPointEnabled(strictReaderRuleId)) strictReaderRuleId else bgDbRuleId
            hookSimpleBlock(
                registry = registry,
                ruleId = primaryRuleId,
                hookId = "baidu.clipboard.reader_u",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:wg0_b_u",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("wg0.b", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "u", ContextWrapper::class.java) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("mms_is_ververification_code")
                                }
                                paramTypes("android.content.ContextWrapper")
                                returnType("java.lang.CharSequence")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = {
                    val (agreed, privMode) = readPrivacyTuple()
                    BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                        clipRuleEnabled = isPointEnabled(bgDbRuleId),
                        isBackgroundOrTokenOrJs = false,
                        privacyAgreed = agreed,
                        privacyInputMode = privMode,
                        strictReadersEnabled = isPointEnabled(strictReaderRuleId)
                    )
                },
                blockedResult = { "" }
            )
        }
    }

    // -------------------------------------------------------------------------
    // Scan rules: contacts, app_list, sms_otp_local, location_wifi_cell
    // -------------------------------------------------------------------------

    private fun installScanHooks(resolver: SymbolResolver, registry: HookRegistry) {
        // 16) baidu.scan.contacts
        val contactsRuleId = HookRuleCatalog.RULE_BAIDU_SCAN_CONTACTS
        registry.installRuleIfEnabled(contactsRuleId) {
            val contactsUploaderClass = resolver.resolveClass(
                cacheKey = "baidu:t40_b_class",
                fastPathCandidates = listOf("t40.b"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        matcher {
                            usingStrings("ContactCustomManager", "https://upl.baidu.com/words/add")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value

            hookSimpleBlock(
                registry = registry,
                ruleId = contactsRuleId,
                hookId = "baidu.contacts_sms.contacts_map",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:t40_b_a",
                    clazz = contactsUploaderClass,
                    methodName = "a",
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("ContactCustomManager", "https://upl.baidu.com/words/add")
                                }
                                usingStrings("ContactCustomManager", "display_name")
                                returnType("java.util.HashMap")
                                paramCount(0)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(contactsRuleId) },
                blockedResult = { HashMap<Any?, Any?>() }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = contactsRuleId,
                hookId = "baidu.contacts_sms.contacts_upload",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:t40_b_d",
                    fastPath = { cl ->
                        val lp0cClass = SymbolResolver.loadClassOrNull("lp0.c", cl) ?: return@resolveMethod null
                        contactsUploaderClass?.let {
                            SymbolResolver.findDeclaredMethodOrNull(
                                it,
                                "d",
                                lp0cClass,
                                Boolean::class.javaPrimitiveType!!
                            )
                        }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("ContactCustomManager", "https://upl.baidu.com/words/add")
                                }
                                usingStrings("https://upl.baidu.com/words/add", "android.permission.READ_CONTACTS")
                                paramCount(2)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(contactsRuleId) },
                blockedResult = { null }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = contactsRuleId,
                hookId = "baidu.contacts_sms.contacts_query",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:contacts_utils_query",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("com.baidu.res.ContactsUtils", cl)
                            ?.let {
                                SymbolResolver.findDeclaredMethodOrNull(
                                    it,
                                    "getContactsByContent",
                                    Int::class.javaPrimitiveType!!,
                                    String::class.java
                                )
                            }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("ContactsUtils.java")
                                }
                                usingStrings("display_name", "data1")
                                paramTypes("int", "java.lang.String")
                                returnType("java.util.List")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(contactsRuleId) },
                blockedResult = { emptyList<Any?>() },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = contactsRuleId,
                hookId = "baidu.scan.oj0_c_contacts",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:oj0_c_contacts",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("oj0.c", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "a") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                name = "a"
                                usingStrings("android.permission.READ_CONTACTS")
                                paramCount(0)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(contactsRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = contactsRuleId,
                hookId = "baidu.scan.v6_i_contacts",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:v6_i_contacts_j",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("v6.i", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "j", Context::class.java) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("display_name", "data1")
                                paramTypes("android.content.Context")
                                returnType("java.util.HashMap")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(contactsRuleId) },
                blockedResult = { HashMap<Any?, Any?>() },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }

        // 17) baidu.scan.app_list: rj0.b#b() selectors 1 & 4 (instl), ImeEnvCallback#findApp/hasInstallApp, l11.b#f1
        val appListRuleId = HookRuleCatalog.RULE_BAIDU_SCAN_APP_LIST
        registry.installRuleIfEnabled(appListRuleId) {
            hookIntSelectorTask(
                resolver = resolver,
                registry = registry,
                ruleId = appListRuleId,
                hookId = "baidu.contacts_sms.rj0_app_scan",
                methodResult = resolver.resolveMethod(
                    cacheKey = "baidu:rj0_b_b",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("rj0.b", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "b") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("installinfo", "isinstalled", "shortcutinfo", "guiderinfo")
                                returnType("void")
                                paramCount(0)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                fieldCacheKey = "baidu:rj0_b_selector",
                primaryFieldName = "d",
                onMissingFieldDetail = "rj0.b selector field unresolved; leaving app scan task unhooked",
                onFailureStatus = HookMatchStatus.DEGRADED,
                shouldBlockSelector = { selector ->
                    BaiduJsonSanitizer.shouldBlockAppListSelector(selector, isPointEnabled(appListRuleId))
                },
                blockedResult = { null }
            )

            val imeEnvClass = resolveImeEnvCallbackClass(resolver)
            hookSimpleBlock(
                registry = registry,
                ruleId = appListRuleId,
                hookId = "baidu.contacts_sms.find_app",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:ime_env_find_app",
                    fastPath = { cl ->
                        val envCls = imeEnvClass ?: return@resolveMethod null
                        val iptAppMsgInfoCls = SymbolResolver.loadClassOrNull("com.baidu.iptcore.info.IptAppMsgInfo", cl)
                            ?: return@resolveMethod null
                        val arrayClass = JvmArray.newInstance(iptAppMsgInfoCls, 0).javaClass
                        SymbolResolver.findDeclaredMethodOrNull(envCls, "findApp", arrayClass)
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("isAllowAiPadAutoOpen type:")
                                }
                                name = "findApp"
                                returnType("int")
                                paramCount(1)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(appListRuleId) },
                blockedResult = { -1 }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = appListRuleId,
                hookId = "baidu.contacts_sms.has_install_app",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:ime_env_has_install_app",
                    clazz = imeEnvClass,
                    methodName = "hasInstallApp",
                    String::class.java,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("isAllowAiPadAutoOpen type:")
                                }
                                usingStrings("hasInstallApp packageName = ")
                                paramTypes("java.lang.String")
                                returnType("boolean")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(appListRuleId) },
                blockedResult = { false }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = appListRuleId,
                hookId = "baidu.contacts_sms.l11_app_upload",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:l11_b_f1",
                    fastPath = { cl ->
                        val l11bClass = SymbolResolver.loadClassOrNull("l11.b", cl) ?: return@resolveMethod null
                        val zg0eClass = SymbolResolver.loadClassOrNull("zg0.e", cl) ?: return@resolveMethod null
                        SymbolResolver.findDeclaredMethodOrNull(l11bClass, "f1", zg0eClass)
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("wapicon")
                                }
                                usingStrings("install")
                                paramCount(1)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(appListRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }

        // 18) baidu.scan.sms_otp_local (opt-in, default=false): q30.e#o(), q30.d#onChange(), q30.f#run()
        val smsRuleId = HookRuleCatalog.RULE_BAIDU_SCAN_SMS_OTP_LOCAL
        registry.installRuleIfEnabled(smsRuleId) {
            hookSimpleBlock(
                registry = registry,
                ruleId = smsRuleId,
                hookId = "baidu.contacts_sms.sms_check",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:q30_e_o",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("q30.e", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "o") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                declaredClass {
                                    usingStrings("android.permission.READ_SMS", "count_contsign")
                                }
                                usingStrings("android.permission.READ_SMS")
                                returnType("boolean")
                                paramCount(0)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(smsRuleId) },
                blockedResult = { false }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = smsRuleId,
                hookId = "baidu.contacts_sms.sms_observer",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:q30_d_on_change",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("q30.d", cl)
                            ?.let {
                                SymbolResolver.findDeclaredMethodOrNull(
                                    it,
                                    "onChange",
                                    Boolean::class.javaPrimitiveType!!,
                                    Uri::class.java
                                )
                            }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                name = "onChange"
                                usingStrings("content://sms/inbox-insert")
                                paramTypes("boolean", "android.net.Uri")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(smsRuleId) },
                blockedResult = { null }
            )

            hookSimpleBlock(
                registry = registry,
                ruleId = smsRuleId,
                hookId = "baidu.contacts_sms.sms_runner",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:q30_f_run",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("q30.f", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "run") }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                name = "run"
                                usingStrings("content://sms/inbox")
                                paramCount(0)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(smsRuleId) },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }

        // 19) baidu.scan.location_wifi_cell
        val locRuleId = HookRuleCatalog.RULE_BAIDU_SCAN_LOCATION_WIFI_CELL
        registry.installRuleIfEnabled(locRuleId) {
            val locModuleClass = resolver.resolveClass(
                cacheKey = "baidu:talos_location_module",
                fastPathCandidates = listOf("com.baidu.talos.core.modules.location.LocationModule"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.talos.core.modules.location")
                        matcher {
                            className = "com.baidu.talos.core.modules.location.LocationModule"
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            for (methodName in listOf("getCurrentPosition", "startObserving")) {
                registry.hookAllMethods(
                    ruleId = locRuleId,
                    hookIdPrefix = "baidu.scan.location_$methodName",
                    clazz = locModuleClass,
                    methodName = methodName
                ) { chain ->
                    if (isPointEnabled(locRuleId)) {
                        registry.incrementBlocked(locRuleId)
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }

            // com.baidu.mobstat.v#h/i/k(Context):String (Wi-Fi AP list / BSSID / cell info)
            val mobstatVClass = resolver.resolveClass(
                cacheKey = "baidu:mobstat_v_class",
                fastPathCandidates = listOf("com.baidu.mobstat.v"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.mobstat")
                        matcher {
                            usingStrings("ap-list", "meta-data")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            for (methodName in listOf("h", "i", "k")) {
                hookSimpleBlock(
                    registry = registry,
                    ruleId = locRuleId,
                    hookId = "baidu.scan.mobstat_v_$methodName",
                    resolveResult = resolveMethodOnClass(
                        resolver = resolver,
                        cacheKey = "baidu:mobstat_v_$methodName",
                        clazz = mobstatVClass,
                        methodName = methodName,
                        Context::class.java,
                        dexKitFinder = { bridge ->
                            bridge.findMethod {
                                searchPackages("com.baidu.mobstat")
                                matcher {
                                    declaredClass {
                                        usingStrings("ap-list", "meta-data")
                                    }
                                    name = methodName
                                    paramTypes("android.content.Context")
                                    returnType("java.lang.String")
                                }
                            }.singleOrNull()?.descriptor
                        }
                    ),
                    shouldBlock = { isPointEnabled(locRuleId) },
                    blockedResult = { "" },
                    onFailureStatus = HookMatchStatus.DEGRADED
                )
            }

            // com.baidu.android.util.connect.NetWorkUtils & devices.NetWorkUtils
            val connectNetUtils = resolver.resolveClass(
                cacheKey = "baidu:connect_net_utils",
                fastPathCandidates = listOf("com.baidu.android.util.connect.NetWorkUtils"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.android.util.connect")
                        matcher {
                            className = "com.baidu.android.util.connect.NetWorkUtils"
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            hookSimpleBlock(
                registry = registry,
                ruleId = locRuleId,
                hookId = "baidu.scan.connect_bssid",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:connect_net_bssid",
                    clazz = connectNetUtils,
                    methodName = "getBSSID",
                    Context::class.java
                ),
                shouldBlock = { isPointEnabled(locRuleId) },
                blockedResult = { "" },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
            for (intMethod in listOf("getIPAddress", "getNetworkId")) {
                hookSimpleBlock(
                    registry = registry,
                    ruleId = locRuleId,
                    hookId = "baidu.scan.connect_$intMethod",
                    resolveResult = resolveMethodOnClass(
                        resolver = resolver,
                        cacheKey = "baidu:connect_net_$intMethod",
                        clazz = connectNetUtils,
                        methodName = intMethod,
                        Context::class.java
                    ),
                    shouldBlock = { isPointEnabled(locRuleId) },
                    blockedResult = { 0 },
                    onFailureStatus = HookMatchStatus.DEGRADED
                )
            }

            val devicesNetUtils = resolver.resolveClass(
                cacheKey = "baidu:devices_net_utils",
                fastPathCandidates = listOf("com.baidu.android.util.devices.NetWorkUtils"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.android.util.devices")
                        matcher {
                            className = "com.baidu.android.util.devices.NetWorkUtils"
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            for (strMethod in listOf("getBSSID", "getMacAddress")) {
                registry.hookAllMethods(
                    ruleId = locRuleId,
                    hookIdPrefix = "baidu.scan.devices_$strMethod",
                    clazz = devicesNetUtils,
                    methodName = strMethod
                ) { chain ->
                    if (isPointEnabled(locRuleId)) {
                        registry.incrementBlocked(locRuleId)
                        ""
                    } else {
                        chain.proceed()
                    }
                }
            }
            for (intMethod in listOf("getIPAddress", "getNetworkId")) {
                registry.hookAllMethods(
                    ruleId = locRuleId,
                    hookIdPrefix = "baidu.scan.devices_$intMethod",
                    clazz = devicesNetUtils,
                    methodName = intMethod
                ) { chain ->
                    if (isPointEnabled(locRuleId)) {
                        registry.incrementBlocked(locRuleId)
                        0
                    } else {
                        chain.proceed()
                    }
                }
            }

            // zd.e#n(String):Location
            hookSimpleBlock(
                registry = registry,
                ruleId = locRuleId,
                hookId = "baidu.scan.zd_e_location",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:zd_e_location",
                    fastPath = { cl ->
                        SymbolResolver.loadClassOrNull("zd.e", cl)
                            ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "n", String::class.java) }
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            matcher {
                                usingStrings("TwilightManager", "Failed to get last known location")
                                paramTypes("java.lang.String")
                                returnType("android.location.Location")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(locRuleId) },
                blockedResult = { null as Location? },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }
    }

    // -------------------------------------------------------------------------
    // Network & hardening rules: salvage_logs, dynamic_dex_and_js, tls_hardening, httpdns
    // -------------------------------------------------------------------------

    private fun installNetworkAndHardeningHooks(resolver: SymbolResolver, registry: HookRegistry) {
        // 20) baidu.net.salvage_logs
        val salvageRuleId = HookRuleCatalog.RULE_BAIDU_NET_SALVAGE_LOGS
        registry.installRuleIfEnabled(salvageRuleId) {
            val collectorResult = resolver.resolveMethod(
                cacheKey = "baidu:js_a_h",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("js.a", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "h") }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            usingStrings("DataCollectorHandler", "config file download success")
                            returnType("void")
                            paramCount(0)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            val collectorMethod = collectorResult.value
            val emitterField: Field? = collectorMethod?.declaringClass?.let { cls ->
                findDeclaredFieldOrNull(cls, "b")
                    ?: runCatching {
                        cls.declaredFields.firstOrNull { field ->
                            !Modifier.isStatic(field.modifiers) &&
                                !field.type.isPrimitive &&
                                runCatching {
                                    field.type.methods.any { m -> m.name == "onComplete" && m.parameterCount == 0 }
                                }.getOrDefault(false)
                        }?.apply { isAccessible = true }
                    }.getOrNull()
            }
            registry.hookExecutable(
                ruleId = salvageRuleId,
                hookId = "baidu.salvage.data_collector_h",
                resolveResult = collectorResult
            ) { chain ->
                if (!isPointEnabled(salvageRuleId)) {
                    return@hookExecutable chain.proceed()
                }
                val emitter = chain.thisObject?.let { self ->
                    runCatching<Any?> { emitterField?.get(self) }.getOrNull()
                }
                if (emitter != null) {
                    runCatching { emitter.javaClass.getMethod("onComplete").invoke(emitter) }
                }
                registry.incrementBlocked(salvageRuleId)
                null
            }

            // fg.w#b(x6.a)->Observable & #c(kh0.j)->pe1.x case 5 (mobile_log_salvage)
            val fgWClass = resolver.resolveClass(
                cacheKey = "baidu:fg_w_class",
                fastPathCandidates = listOf("fg.w"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        matcher {
                            usingStrings("enSysDown word info error", "ensysdown_", "cell_loc")
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            hookRxSalvageVariant(
                resolver = resolver,
                registry = registry,
                ruleId = salvageRuleId,
                fgWClass = fgWClass,
                methodName = "b",
                paramClassName = "x6.a",
                hookId = "baidu.salvage.fg_w_b",
                methodCacheKey = "baidu:fg_w_b",
                missingRxDetail = "fg.w#b Observable.empty() missing; keeping unhooked to prevent RxJava NPE",
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            declaredClass {
                                usingStrings("enSysDown word info error", "ensysdown_")
                            }
                            usingStrings("wordlib_info")
                            paramCount(1)
                            returnType("io.reactivex.Observable")
                        }
                    }.singleOrNull()?.descriptor
                },
                resolveRxMethod = { retType -> BaiduJsonSanitizer.findRxEmptyObservableMethod(retType) },
                invokeRxFallback = { rxMethod -> BaiduJsonSanitizer.invokeRxEmptyObservable(rxMethod) }
            )
            hookRxSalvageVariant(
                resolver = resolver,
                registry = registry,
                ruleId = salvageRuleId,
                fgWClass = fgWClass,
                methodName = "c",
                paramClassName = "kh0.j",
                hookId = "baidu.salvage.fg_w_c",
                methodCacheKey = "baidu:fg_w_c",
                missingRxDetail = "fg.w#c pe1.x.e(Object) missing; keeping unhooked to prevent RxJava NPE",
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            declaredClass {
                                usingStrings("enSysDown word info error", "ensysdown_")
                            }
                            usingStrings("KEY_AI_CARD_TRIGGER_CONDITION", "WL_CMBC_WHITELIST")
                            paramCount(1)
                        }
                    }.singleOrNull()?.descriptor
                },
                resolveRxMethod = { retType -> BaiduJsonSanitizer.findRxSingleJustMethod(retType) },
                invokeRxFallback = { rxMethod -> BaiduJsonSanitizer.invokeRxSingleBoolean(rxMethod, false) }
            )
        }

        // 21) baidu.net.dynamic_dex_and_js
        val dclRuleId = HookRuleCatalog.RULE_BAIDU_NET_DYNAMIC_DEX_AND_JS
        registry.installRuleIfEnabled(dclRuleId) {
            hookSimpleBlock(
                registry = registry,
                ruleId = dclRuleId,
                hookId = "baidu.dcl.pl_dex_loader",
                resolveResult = resolver.resolveMethod(
                    cacheKey = "baidu:pl_impl_a_load",
                    fastPath = { cl ->
                        val plClass = SymbolResolver.loadClassOrNull("com.baidu.input.pl.impl.a", cl)
                            ?: return@resolveMethod null
                        val paramClass = SymbolResolver.loadClassOrNull("oi0.g", cl)
                            ?: return@resolveMethod null
                        SymbolResolver.findDeclaredMethodOrNull(plClass, "a", paramClass)
                    },
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.input.pl.impl")
                            matcher {
                                usingStrings("ime_pls_files", ".apk")
                                returnType("void")
                                paramCount(1)
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = { isPointEnabled(dclRuleId) },
                blockedResult = { null }
            )

            // mh.v#onPageFinished(BdSailorWebView, String) case 0 -> block ksone.it2sh.com JS injection
            val mhvResult = resolver.resolveMethod(
                cacheKey = "baidu:mh_v_js_inject",
                fastPath = { cl ->
                    val mhvCls = SymbolResolver.loadClassOrNull("mh.v", cl) ?: return@resolveMethod null
                    val wvCls = SymbolResolver.loadClassOrNull("com.baidu.browser.sailor.BdSailorWebView", cl)
                        ?: return@resolveMethod null
                    SymbolResolver.findDeclaredMethodOrNull(mhvCls, "onPageFinished", wvCls, String::class.java)
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            name = "onPageFinished"
                            usingStrings("https://ksone.it2sh.com/baidu/test/bdinput_ex.js")
                            paramCount(2)
                            returnType("void")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookIntSelectorTask(
                resolver = resolver,
                registry = registry,
                ruleId = dclRuleId,
                hookId = "baidu.dcl.mh_v_js_inject",
                methodResult = mhvResult,
                fieldCacheKey = "baidu:mh_v_selector",
                primaryFieldName = "f50177a",
                onMissingFieldDetail = "mh.v selector field unresolved; keeping unhooked to preserve FeedbackMetisH5Activity",
                onFailureStatus = HookMatchStatus.DEGRADED,
                shouldBlockSelector = { selector -> selector == 0 && isPointEnabled(dclRuleId) },
                blockedResult = { null }
            )
        }

        // 22) baidu.net.tls_hardening (fix 3 broken HostnameVerifiers)
        val tlsRuleId = HookRuleCatalog.RULE_BAIDU_NET_TLS_HARDENING
        registry.installRuleIfEnabled(tlsRuleId) {
            // t6.a is D8-merged into IptCloudStream.java; resolve via DexKit only without a FastPath class literal.
            val t6VerifyResult = resolver.resolveMethod(
                cacheKey = "baidu:tls_t6_a_verify",
                fastPath = { null },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        matcher {
                            declaredClass {
                                addInterface("javax.net.ssl.HostnameVerifier")
                                usingStrings("baidu.com")
                            }
                            name = "verify"
                            paramTypes("java.lang.String", "javax.net.ssl.SSLSession")
                            returnType("boolean")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookTlsVerifier(registry, tlsRuleId, "baidu.tls.t6_a_verify", t6VerifyResult)

            val bdsVerifyResult = resolver.resolveMethod(
                cacheKey = "baidu:tls_bds_request_maker",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("com.baidu.speech.core.BDSHttpRequestMaker\$1", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "verify", String::class.java, SSLSession::class.java) }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.speech.core")
                        matcher {
                            declaredClass {
                                addInterface("javax.net.ssl.HostnameVerifier")
                            }
                            usingStrings("audiotest.baidu.com", "verifyUrl : ")
                            name = "verify"
                            paramTypes("java.lang.String", "javax.net.ssl.SSLSession")
                            returnType("boolean")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookTlsVerifier(registry, tlsRuleId, "baidu.tls.bds_request_maker", bdsVerifyResult)

            val ocrVerifyResult = resolver.resolveMethod(
                cacheKey = "baidu:tls_ocr_request_maker",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("com.baidu.speech.ocr.OcrHttpRequestMaker\$1", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "verify", String::class.java, SSLSession::class.java) }
                },
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.speech.ocr")
                        matcher {
                            declaredClass {
                                addInterface("javax.net.ssl.HostnameVerifier")
                            }
                            usingStrings("119.75.222.172", "httpsdns.baidu.com")
                            name = "verify"
                            paramTypes("java.lang.String", "javax.net.ssl.SSLSession")
                            returnType("boolean")
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            hookTlsVerifier(registry, tlsRuleId, "baidu.tls.ocr_request_maker", ocrVerifyResult)
        }

        // 23) baidu.net.httpdns (opt-in, default=false)
        val httpDnsRuleId = HookRuleCatalog.RULE_BAIDU_NET_HTTPDNS
        registry.installRuleIfEnabled(httpDnsRuleId) {
            val httpDnsBlocked = { BaiduJsonSanitizer.shouldBlockHttpDns(isPointEnabled(httpDnsRuleId)) }

            val httpDnsCache = resolver.resolveClass(
                cacheKey = "baidu:http_dns_cache",
                fastPathCandidates = listOf("com.baidu.webkit.internal.daemon.HttpDnsCache"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.webkit.internal.daemon")
                        matcher {
                            className = "com.baidu.webkit.internal.daemon.HttpDnsCache"
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            for (methodName in listOf("tryToUpdateHttpDnsCache", "tryToUpdateHttpDnsCacheStaticIP")) {
                hookSimpleBlock(
                    registry = registry,
                    ruleId = httpDnsRuleId,
                    hookId = "baidu.httpdns.$methodName",
                    resolveResult = resolveMethodOnClass(
                        resolver = resolver,
                        cacheKey = "baidu:httpdns_$methodName",
                        clazz = httpDnsCache,
                        methodName = methodName,
                        Context::class.java,
                        dexKitFinder = { bridge ->
                            bridge.findMethod {
                                searchPackages("com.baidu.webkit.internal.daemon")
                                matcher {
                                    name = methodName
                                    paramTypes("android.content.Context")
                                    returnType("void")
                                }
                            }.singleOrNull()?.descriptor
                        }
                    ),
                    shouldBlock = httpDnsBlocked,
                    blockedResult = { null },
                    onFailureStatus = HookMatchStatus.DEGRADED
                )
            }

            hookSimpleBlock(
                registry = registry,
                ruleId = httpDnsRuleId,
                hookId = "baidu.httpdns.restoreLastCacheFromCfg",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:httpdns_restore_last_cache",
                    clazz = httpDnsCache,
                    methodName = "restoreLastCacheFromCfg",
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.webkit.internal.daemon")
                            matcher {
                                name = "restoreLastCacheFromCfg"
                                paramCount(0)
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = httpDnsBlocked,
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            val httpDnsHostCache = resolver.resolveClass(
                cacheKey = "baidu:http_dns_cache_host",
                fastPathCandidates = listOf("com.baidu.webkit.internal.daemon.HttpDnsCacheForHost"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.webkit.internal.daemon")
                        matcher {
                            className = "com.baidu.webkit.internal.daemon.HttpDnsCacheForHost"
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            hookSimpleBlock(
                registry = registry,
                ruleId = httpDnsRuleId,
                hookId = "baidu.httpdns.tryToUpdateHttpDnsCacheHost",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:httpdns_host_update",
                    clazz = httpDnsHostCache,
                    methodName = "tryToUpdateHttpDnsCacheHost",
                    String::class.java,
                    Boolean::class.javaPrimitiveType!!,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.webkit.internal.daemon")
                            matcher {
                                name = "tryToUpdateHttpDnsCacheHost"
                                paramTypes("java.lang.String", "boolean")
                                returnType("void")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = httpDnsBlocked,
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            val webSettingsBlink = resolver.resolveClass(
                cacheKey = "baidu:web_settings_global_blink",
                fastPathCandidates = listOf("com.baidu.webkit.internal.blink.WebSettingsGlobalBlink"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.webkit.internal.blink")
                        matcher {
                            className = "com.baidu.webkit.internal.blink.WebSettingsGlobalBlink"
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            hookSimpleBlock(
                registry = registry,
                ruleId = httpDnsRuleId,
                hookId = "baidu.httpdns.blink_update_enabled",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:httpdns_blink_enabled",
                    clazz = webSettingsBlink,
                    methodName = "getHttpDnsUpdateEnabled",
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.webkit.internal.blink")
                            matcher {
                                name = "getHttpDnsUpdateEnabled"
                                paramCount(0)
                                returnType("boolean")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                shouldBlock = httpDnsBlocked,
                blockedResult = { false },
                onFailureStatus = HookMatchStatus.DEGRADED
            )

            val turbonetBuilderClass = resolver.resolveClass(
                cacheKey = "baidu:turbonet_builder",
                fastPathCandidates = listOf("com.baidu.turbonet.net.TurbonetEngine\$Builder"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.turbonet.net")
                        matcher {
                            className = "com.baidu.turbonet.net.TurbonetEngine\$Builder"
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value

            hookEnableBaiduDnsFalse(
                resolver = resolver,
                registry = registry,
                ruleId = httpDnsRuleId,
                hookId = "baidu.httpdns.turbonet_builder",
                targetClass = turbonetBuilderClass,
                methodCacheKey = "baidu:httpdns_turbonet_builder",
                methodName = "enableBaiduDns",
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.turbonet.net")
                        matcher {
                            declaredClass {
                                className = "com.baidu.turbonet.net.TurbonetEngine\$Builder"
                            }
                            name = "enableBaiduDns"
                            paramTypes("boolean")
                        }
                    }.singleOrNull()?.descriptor
                },
                httpDnsBlocked = httpDnsBlocked
            )

            hookEnableBaiduDnsFalse(
                resolver = resolver,
                registry = registry,
                ruleId = httpDnsRuleId,
                hookId = "baidu.httpdns.turbonet_builder_bce_backup",
                targetClass = turbonetBuilderClass,
                methodCacheKey = "baidu:httpdns_turbonet_builder_bce_backup",
                methodName = "enableBceHttpDnsBackupIP",
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.turbonet.net")
                        matcher {
                            declaredClass {
                                className = "com.baidu.turbonet.net.TurbonetEngine\$Builder"
                            }
                            name = "enableBceHttpDnsBackupIP"
                            paramTypes("boolean")
                        }
                    }.singleOrNull()?.descriptor
                },
                httpDnsBlocked = httpDnsBlocked
            )

            registry.hookExecutable(
                ruleId = httpDnsRuleId,
                hookId = "baidu.httpdns.turbonet_builder_bce",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:httpdns_turbonet_builder_bce",
                    clazz = turbonetBuilderClass,
                    methodName = "enableBceHttpDns",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    dexKitFinder = { bridge ->
                        bridge.findMethod {
                            searchPackages("com.baidu.turbonet.net")
                            matcher {
                                declaredClass {
                                    className = "com.baidu.turbonet.net.TurbonetEngine\$Builder"
                                }
                                name = "enableBceHttpDns"
                                paramTypes("java.lang.String", "java.lang.String", "java.lang.String")
                            }
                        }.singleOrNull()?.descriptor
                    }
                ),
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { chain ->
                if (httpDnsBlocked()) {
                    registry.incrementBlocked(httpDnsRuleId)
                    chain.thisObject
                } else {
                    chain.proceed()
                }
            }

            val turbonetConfigClass = resolver.resolveClass(
                cacheKey = "baidu:turbonet_config",
                fastPathCandidates = listOf("com.baidu.turbonet.net.TurbonetConfig"),
                dexKitFinder = { bridge ->
                    bridge.findClass {
                        searchPackages("com.baidu.turbonet.net")
                        matcher {
                            className = "com.baidu.turbonet.net.TurbonetConfig"
                        }
                    }.singleOrNull()?.descriptor
                }
            ).value
            hookEnableBaiduDnsFalse(
                resolver = resolver,
                registry = registry,
                ruleId = httpDnsRuleId,
                hookId = "baidu.httpdns.turbonet_config",
                targetClass = turbonetConfigClass,
                methodCacheKey = "baidu:httpdns_turbonet_config",
                methodName = "enableBaiduDns",
                dexKitFinder = { bridge ->
                    bridge.findMethod {
                        searchPackages("com.baidu.turbonet.net")
                        matcher {
                            declaredClass {
                                className = "com.baidu.turbonet.net.TurbonetConfig"
                            }
                            name = "enableBaiduDns"
                            paramTypes("boolean")
                        }
                    }.singleOrNull()?.descriptor
                },
                httpDnsBlocked = httpDnsBlocked
            )
        }
    }

    // -------------------------------------------------------------------------
    // Shared class resolvers & hook helpers
    // -------------------------------------------------------------------------

    private fun resolveCloudInfoClass(resolver: SymbolResolver): Class<*>? =
        resolver.resolveClass(
            cacheKey = "baidu:cloud_info",
            fastPathCandidates = listOf("com.baidu.input.ime.cloudinput.CloudInfo")
        ).value

    private fun resolveVoiceTraceClass(resolver: SymbolResolver): Class<*>? =
        resolver.resolveClass(
            cacheKey = "baidu:voice_trace_class",
            fastPathCandidates = listOf("com.baidu.input.ime.voicerecognize.voicetrace.a"),
            dexKitFinder = { bridge ->
                bridge.findClass {
                    searchPackages("com.baidu.input.ime.voicerecognize.voicetrace")
                    matcher {
                        usingStrings("wufeiyang")
                    }
                }.singleOrNull()?.descriptor
            }
        ).value

    private fun resolveImeEnvCallbackClass(resolver: SymbolResolver): Class<*>? =
        resolver.resolveClass(
            cacheKey = "baidu:ime_env_callback",
            fastPathCandidates = listOf("com.baidu.input.ime.newcore.ImeEnvCallback"),
            dexKitFinder = { bridge ->
                bridge.findClass {
                    matcher {
                        usingStrings("isAllowAiPadAutoOpen type:")
                    }
                }.singleOrNull()?.descriptor
            }
        ).value

    private fun notifyGz0Fail(callback: Any?) {
        if (callback == null) return
        runCatching { callback.javaClass.getMethod("onFail").invoke(callback) }
    }

    private fun hookTlsVerifier(
        registry: HookRegistry,
        ruleId: String,
        hookId: String,
        resolveResult: ResolveResult<Method>
    ) {
        registry.hookExecutable(
            ruleId = ruleId,
            hookId = hookId,
            resolveResult = resolveResult,
            onFailureStatus = HookMatchStatus.DEGRADED
        ) { chain ->
            if (!isPointEnabled(ruleId)) {
                return@hookExecutable chain.proceed()
            }
            val hostname = chain.getArg(0) as? String
            val session = chain.getArg(1) as? SSLSession
            val verified = BaiduJsonSanitizer.verifyTlsHostname(hostname, session)
            if (!verified) {
                registry.incrementBlocked(ruleId)
            }
            verified
        }
    }

    private inline fun hookSimpleBlock(
        registry: HookRegistry,
        ruleId: String,
        hookId: String,
        resolveResult: ResolveResult<Method>,
        crossinline shouldBlock: () -> Boolean,
        crossinline blockedResult: () -> Any?,
        onFailureStatus: HookMatchStatus = HookMatchStatus.FAILED
    ) {
        registry.hookExecutable(
            ruleId = ruleId,
            hookId = hookId,
            resolveResult = resolveResult,
            onFailureStatus = onFailureStatus
        ) { chain ->
            if (shouldBlock()) {
                registry.incrementBlocked(ruleId)
                blockedResult()
            } else {
                chain.proceed()
            }
        }
    }

    private inline fun hookIntSelectorTask(
        resolver: SymbolResolver,
        registry: HookRegistry,
        ruleId: String,
        hookId: String,
        methodResult: ResolveResult<Method>,
        fieldCacheKey: String,
        primaryFieldName: String,
        onMissingFieldDetail: String,
        onFailureStatus: HookMatchStatus,
        crossinline shouldBlockSelector: (Int) -> Boolean,
        crossinline blockedResult: () -> Any?
    ) {
        val method = methodResult.value
        val selectorField = if (method != null) {
            resolver.resolveField(
                cacheKey = fieldCacheKey,
                fastPath = { _ -> findDeclaredFieldOrNull(method.declaringClass, primaryFieldName) },
                structuralFallback = { _ -> findSingleInstanceIntField(method.declaringClass) }
            ).value
        } else {
            null
        }
        if (method != null && selectorField == null) {
            registry.hookExecutable(
                ruleId = ruleId,
                hookId = hookId,
                resolveResult = ResolveResult(
                    value = null,
                    source = ResolveSource.MISS,
                    descriptor = methodResult.descriptor,
                    detail = onMissingFieldDetail
                ),
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { it.proceed() }
            return
        }
        registry.hookExecutable(
            ruleId = ruleId,
            hookId = hookId,
            resolveResult = methodResult,
            onFailureStatus = onFailureStatus
        ) { chain ->
            val instance = chain.thisObject
            val selector = if (selectorField != null && instance != null) {
                runCatching<Int?> { selectorField.getInt(instance) }.getOrNull()
            } else {
                null
            }
            if (selector != null && shouldBlockSelector(selector)) {
                registry.incrementBlocked(ruleId)
                blockedResult()
            } else {
                chain.proceed()
            }
        }
    }

    private inline fun hookRxSalvageVariant(
        resolver: SymbolResolver,
        registry: HookRegistry,
        ruleId: String,
        fgWClass: Class<*>?,
        methodName: String,
        paramClassName: String,
        hookId: String,
        methodCacheKey: String,
        missingRxDetail: String,
        noinline dexKitFinder: ((DexKitBridge) -> String?)? = null,
        crossinline resolveRxMethod: (Class<*>) -> Method?,
        crossinline invokeRxFallback: (Method?) -> Any?
    ) {
        val methodResult = resolver.resolveMethod(
            cacheKey = methodCacheKey,
            fastPath = { cl ->
                val paramClass = SymbolResolver.loadClassOrNull(paramClassName, cl) ?: return@resolveMethod null
                fgWClass?.let { SymbolResolver.findDeclaredMethodOrNull(it, methodName, paramClass) }
            },
            dexKitFinder = dexKitFinder
        )
        val rxMethod = methodResult.value?.returnType?.let(resolveRxMethod)
        if (methodResult.value != null && rxMethod == null) {
            registry.hookExecutable(
                ruleId = ruleId,
                hookId = hookId,
                resolveResult = ResolveResult(
                    value = null,
                    source = ResolveSource.MISS,
                    descriptor = methodResult.descriptor,
                    detail = missingRxDetail
                ),
                onFailureStatus = HookMatchStatus.DEGRADED
            ) { it.proceed() }
            return
        }
        hookIntSelectorTask(
            resolver = resolver,
            registry = registry,
            ruleId = ruleId,
            hookId = hookId,
            methodResult = methodResult,
            fieldCacheKey = "baidu:fg_w_selector",
            primaryFieldName = "a",
            onMissingFieldDetail = "fg.w selector field unresolved; keeping unhooked to preserve non-salvage cases",
            onFailureStatus = HookMatchStatus.DEGRADED,
            shouldBlockSelector = { selector ->
                BaiduJsonSanitizer.shouldBlockSalvageTask(
                    selector = selector,
                    blockRemoteLogSalvage = isPointEnabled(ruleId)
                )
            },
            blockedResult = { invokeRxFallback(rxMethod) }
        )
    }

    private inline fun hookEnableBaiduDnsFalse(
        resolver: SymbolResolver,
        registry: HookRegistry,
        ruleId: String,
        hookId: String,
        targetClass: Class<*>?,
        methodCacheKey: String,
        methodName: String,
        noinline dexKitFinder: ((DexKitBridge) -> String?)? = null,
        crossinline httpDnsBlocked: () -> Boolean
    ) {
        registry.hookExecutable(
            ruleId = ruleId,
            hookId = hookId,
            resolveResult = resolveMethodOnClass(
                resolver = resolver,
                cacheKey = methodCacheKey,
                clazz = targetClass,
                methodName = methodName,
                Boolean::class.javaPrimitiveType!!,
                dexKitFinder = dexKitFinder
            ),
            onFailureStatus = HookMatchStatus.DEGRADED
        ) { chain ->
            val enabledArg = (chain.getArg(0) as? Boolean) ?: false
            if (enabledArg && httpDnsBlocked()) {
                registry.incrementBlocked(ruleId)
                chain.proceed(arrayOf<Any?>(false))
            } else {
                chain.proceed()
            }
        }
    }

    private fun readPrivacyAgreed(privacyAgreedMethod: Method?): Boolean =
        runCatching { privacyAgreedMethod?.invoke(null) as? Boolean }.getOrNull() ?: false

    private fun resolveMethodOnClass(
        resolver: SymbolResolver,
        cacheKey: String,
        clazz: Class<*>?,
        methodName: String,
        vararg paramTypes: Class<*>,
        dexKitFinder: ((DexKitBridge) -> String?)? = null
    ): ResolveResult<Method> =
        resolver.resolveMethod(
            cacheKey = cacheKey,
            fastPath = { _ ->
                clazz?.let { SymbolResolver.findDeclaredMethodOrNull(it, methodName, *paramTypes) }
            },
            dexKitFinder = dexKitFinder
        )

    private fun findDeclaredFieldOrNull(clazz: Class<*>, fieldName: String): Field? =
        runCatching { clazz.getDeclaredField(fieldName).apply { isAccessible = true } }.getOrNull()

    private fun findSingleInstanceIntField(clazz: Class<*>): Field? =
        runCatching {
            clazz.declaredFields
                .singleOrNull { !Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
                ?.apply { isAccessible = true }
        }.getOrNull()

    private fun findStaticFieldByType(clazz: Class<*>, fieldType: Class<*>): Field? =
        runCatching {
            clazz.declaredFields
                .singleOrNull { Modifier.isStatic(it.modifiers) && it.type == fieldType }
                ?.apply { isAccessible = true }
        }.getOrNull()
}
