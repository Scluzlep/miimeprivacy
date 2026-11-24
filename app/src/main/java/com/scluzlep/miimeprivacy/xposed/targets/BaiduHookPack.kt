package com.scluzlep.miimeprivacy.xposed.targets

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ImeHookPack
import com.scluzlep.miimeprivacy.xposed.ImePackInstallResult
import com.scluzlep.miimeprivacy.xposed.ResolveResult
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Array as JvmArray
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Baidu Xiaomi IME (`com.baidu.input_mi`, versionCode 7108+) Xposed privacy hook pack.
 *
 * Implements all 7 non-overlay rules in [HookRuleCatalog.rulesFor] for [ImeTarget.BAIDU]
 * using runtime [ConfigBridge] evaluation and FastPath + DexKit + structural resolution.
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

            installPrivacyGateHooks(resolver, registry, privacyAgreedMethod)
            installTelemetryHooks(resolver, registry)
            installCloudAndAiHooks(resolver, registry)
            installClipboardHooks(
                resolver = resolver,
                classLoader = classLoader,
                appContextWrapper = context as? ContextWrapper,
                registry = registry,
                privacyAgreedMethod = privacyAgreedMethod,
                privacyInputModeField = privacyInputModeField
            )
            installContactsSmsAppScanHooks(resolver, registry)
            installSalvageAndDclHooks(resolver, registry)
            installHttpDnsHooks(resolver, registry)
        }

        val nonOverlayRules = HookRuleCatalog.rulesFor(target)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_BAIDU_OVERLAY_PILL }
        return ImePackInstallResult(
            target = target,
            reports = nonOverlayRules.mapNotNull { registry.getReport(it.ruleId) },
            note = "Baidu privacy hooks installed (${nonOverlayRules.size} rules)"
        )
    }

    private inline fun isRuleFlagEnabled(crossinline predicate: (PrivacyConfig) -> Boolean): Boolean =
        ConfigBridge.isRuleEnabled(ImeTarget.BAIDU) { cfg -> predicate(cfg) }

    // -------------------------------------------------------------------------
    // Rule 1: RULE_BAIDU_PRIVACY_GATE
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

    private fun installPrivacyGateHooks(
        resolver: SymbolResolver,
        registry: HookRegistry,
        privacyAgreedMethod: Method?
    ) {
        val ruleId = HookRuleCatalog.RULE_BAIDU_PRIVACY_GATE

        // 1) tn0.e#isPrivacyAgreed() -> stop hardcoded true; delegate to real 2439 dialog consent.
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
            ruleId = ruleId,
            hookId = "baidu.privacy.tn0_is_privacy_agreed",
            resolveResult = tn0AgreeResult
        ) { chain ->
            if (!isRuleFlagEnabled { it.blockTelemetry }) {
                return@hookExecutable chain.proceed()
            }
            val agreed = readPrivacyAgreed(privacyAgreedMethod)
            if (!agreed) {
                registry.incrementBlocked(ruleId)
            }
            agreed
        }

        // 2) com.baidu.ubc.UBCHelper#isAgreePrivacy() -> block fail-open UBC telemetry agreement.
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = { isRuleFlagEnabled { it.blockTelemetry } },
            blockedResult = { false }
        )

        // 3) dj0.y#h(int):boolean & #A(int, boolean):void -> neutralize bit 1839 default-agree bypass.
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
            ruleId = ruleId,
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
                    blockTelemetry = isRuleFlagEnabled { it.blockTelemetry }
                )
            ) {
                registry.incrementBlocked(ruleId)
                false
            } else {
                chain.proceed()
            }
        }

        registry.hookExecutable(
            ruleId = ruleId,
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
                    blockTelemetry = isRuleFlagEnabled { it.blockTelemetry }
                )
            ) {
                registry.incrementBlocked(ruleId)
                chain.proceed(arrayOf<Any?>(prefKey, false))
            } else {
                chain.proceed()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rule 2: RULE_BAIDU_TELEMETRY
    // -------------------------------------------------------------------------

    private fun installTelemetryHooks(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_BAIDU_TELEMETRY

        // 1) sj0.a#b():boolean -> block ONLY selector == 14 (KP upload); preserve case 9 dict sync!
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
            ruleId = ruleId,
            hookId = "baidu.telemetry.sj0_case14",
            methodResult = sj0MethodResult,
            fieldCacheKey = "baidu:sj0_a_selector",
            primaryFieldName = "b",
            onMissingFieldDetail = "sj0.a selector field unresolved; keeping unhooked to preserve case 9",
            onFailureStatus = HookMatchStatus.FAILED,
            shouldBlockSelector = { selector ->
                BaiduJsonSanitizer.shouldBlockSj0Task(selector, isRuleFlagEnabled { it.blockTelemetry })
            },
            blockedResult = { false }
        )

        // 2) ju.a#k0():void -> block cleartext http://r.bd.sec.miui.com daily Xiaomi stat upload.
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = { isRuleFlagEnabled { it.blockTelemetry } },
            blockedResult = { null }
        )

        // 3) com.baidu.ubc.BehaviorUploader#obUploadDataRequest & uploadDataRequest overloads.
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
                ruleId = ruleId,
                hookIdPrefix = "baidu.telemetry.ubc_$methodName",
                clazz = uploaderCls,
                methodName = methodName,
                source = ubcUploaderResult.source.name
            ) { chain ->
                if (isRuleFlagEnabled { it.blockTelemetry }) {
                    registry.incrementBlocked(ruleId)
                    null
                } else {
                    chain.proceed()
                }
            }
        }

        // 4) com.baidu.mobstat.LogSender#saveLogData(Context, String, boolean)
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
            ruleId = ruleId,
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
            shouldBlock = { isRuleFlagEnabled { it.blockTelemetry } },
            blockedResult = { null }
        )
        val hasOnSend = logSenderClass != null && runCatching {
            logSenderClass.declaredMethods.any { it.name == "onSend" }
        }.getOrDefault(false)
        if (hasOnSend) {
            registry.hookAllMethods(
                ruleId = ruleId,
                hookIdPrefix = "baidu.telemetry.mobstat_on_send",
                clazz = logSenderClass,
                methodName = "onSend"
            ) { chain ->
                if (isRuleFlagEnabled { it.blockTelemetry }) {
                    registry.incrementBlocked(ruleId)
                    null
                } else {
                    chain.proceed()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rule 3: RULE_BAIDU_CLOUD_AND_AI
    // -------------------------------------------------------------------------

    private fun installCloudAndAiHooks(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_BAIDU_CLOUD_AND_AI

        // 1) Cloud master switch: x00.b#c():boolean
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = { !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU) },
            blockedResult = { false }
        )

        // 2) Verified IptCloudStream#send(int, byte[], int):int
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
            ruleId = ruleId,
            hookId = "baidu.cloud.ipt_stream_send",
            resolveResult = iptSendResult
        ) { chain ->
            val type = (chain.getArg(0) as? Int) ?: -1
            when (
                BaiduJsonSanitizer.decideIptCloudStreamSend(
                    type = type,
                    blockTelemetry = isRuleFlagEnabled { it.blockTelemetry },
                    cloudEnabled = ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
                )
            ) {
                StreamSendDecision.BLOCK_TELEMETRY,
                StreamSendDecision.BLOCK_CLOUD_DISABLED -> {
                    registry.incrementBlocked(ruleId)
                    0
                }
                StreamSendDecision.PROCEED -> chain.proceed()
            }
        }

        // 3) CloudInfo field masking & JSON report.cpn sanitization
        val cloudInfoClass = resolver.resolveClass(
            cacheKey = "baidu:cloud_info",
            fastPathCandidates = listOf("com.baidu.input.ime.cloudinput.CloudInfo")
        ).value

        for (hostGetter in listOf("getSugPackageInfo", "get_app_name")) {
            hookSimpleBlock(
                registry = registry,
                ruleId = ruleId,
                hookId = "baidu.cloud.info_$hostGetter",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:cloud_info_$hostGetter",
                    clazz = cloudInfoClass,
                    methodName = hostGetter
                ),
                shouldBlock = { isRuleFlagEnabled { it.maskHostPackage } },
                blockedResult = { "" }
            )
        }

        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
            hookId = "baidu.cloud.info_city",
            resolveResult = resolveMethodOnClass(
                resolver = resolver,
                cacheKey = "baidu:cloud_info_city",
                clazz = cloudInfoClass,
                methodName = "get_city"
            ),
            shouldBlock = { isRuleFlagEnabled { it.sanitizeCloudPayload } },
            blockedResult = { "" }
        )

        registry.hookExecutable(
            ruleId = ruleId,
            hookId = "baidu.cloud.info_json_buf",
            resolveResult = resolveMethodOnClass(
                resolver = resolver,
                cacheKey = "baidu:cloud_info_json_buf",
                clazz = cloudInfoClass,
                methodName = "get_json_buf"
            )
        ) { chain ->
            val original = chain.proceed() as? ByteArray ?: return@hookExecutable null
            val sanitized = BaiduJsonSanitizer.sanitizeCloudInfoJson(
                bytes = original,
                zeroClipboardCount = isRuleFlagEnabled { it.clipboardPrivacy }
            )
            if (sanitized !== original) {
                registry.incrementBlocked(ruleId)
            }
            sanitized
        }

        // 4) VoiceTrace surrounding cursor context & /v5/fb/vt upload
        val voiceTraceClass = resolver.resolveClass(
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

        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
                isRuleFlagEnabled { it.stripCursorContext } || !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
            },
            blockedResult = { null },
            onFailureStatus = HookMatchStatus.DEGRADED
        )

        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
            hookId = "baidu.cloud.voice_trace_upload",
            resolveResult = resolver.resolveMethod(
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
            ),
            shouldBlock = {
                isRuleFlagEnabled { it.blockTelemetry } || !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
            },
            blockedResult = { null },
            onFailureStatus = HookMatchStatus.DEGRADED
        )

        // 5) Wenxin 210-char dialogue context reader & pre-stream uploader
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
                isRuleFlagEnabled { it.stripCursorContext } || !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
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
                ruleId = ruleId,
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
                    isRuleFlagEnabled { it.stripCursorContext } || !ConfigBridge.isCloudEnabled(ImeTarget.BAIDU)
                },
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }
    }

    // -------------------------------------------------------------------------
    // Rule 4: RULE_BAIDU_CLIPBOARD
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
        val ruleId = HookRuleCatalog.RULE_BAIDU_CLIPBOARD

        fun isBlockedPrivacyClipboard(): Boolean {
            val agreed = readPrivacyAgreed(privacyAgreedMethod)
            val privacyInputMode = if (privacyInputModeField != null) {
                runCatching { privacyInputModeField.getBoolean(null) }.getOrNull() ?: false
            } else {
                false
            }
            return BaiduJsonSanitizer.shouldBlockPrivacyClipboard(
                clipboardPrivacy = isRuleFlagEnabled { it.clipboardPrivacy },
                privacyAgreed = agreed,
                privacyInputMode = privacyInputMode
            )
        }

        // 1) ClipboardGlobal$clipboardListener$2 listener (com.baidu.input.clipboard.b#onPrimaryClipChanged)
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = ::isBlockedPrivacyClipboard,
            blockedResult = { null }
        )

        // 2) wg0.b#u(ContextWrapper):CharSequence (ClipboardManager reader)
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = ::isBlockedPrivacyClipboard,
            blockedResult = { "" }
        )

        // 3) bl.c#K1():XiaoMiClipData (returns empty XiaoMiClipData cleanly without touching clipboard)
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
            ruleId = ruleId,
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
            if (!isBlockedPrivacyClipboard()) {
                return@hookExecutable chain.proceed()
            }
            registry.incrementBlocked(ruleId)
            val ctxWrapper = chain.thisObject?.let { self ->
                runCatching { blContextField?.get(self) as? ContextWrapper }.getOrNull()
            } ?: appContextWrapper
            if (ctxWrapper != null && xiaoMiClipCtor != null) {
                runCatching<Any?> { xiaoMiClipCtor.newInstance(ctxWrapper) }.getOrNull()
            } else {
                null
            }
        }

        // 4) com.baidu.input.clipboard.manager.a#j():boolean (inverted e20.g.a() && l.I1 gate)
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = ::isBlockedPrivacyClipboard,
            blockedResult = { false }
        )

        // 5) com.baidu.input.clipboard.datamanager.clipboard.a#J1(String, boolean):pj.a (silent DB insert)
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = ::isBlockedPrivacyClipboard,
            blockedResult = { null },
            onFailureStatus = HookMatchStatus.DEGRADED
        )
    }

    // -------------------------------------------------------------------------
    // Rule 5: RULE_BAIDU_CONTACTS_SMS_SCAN
    // -------------------------------------------------------------------------

    private fun installContactsSmsAppScanHooks(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_BAIDU_CONTACTS_SMS_SCAN
        val enabledCheck = { isRuleFlagEnabled { it.blockContactsAndAppScan } }

        // 1) SMS inbox & OTP ContentObserver: q30.e#o(), q30.d#onChange(), q30.f#run()
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = enabledCheck,
            blockedResult = { false }
        )

        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = enabledCheck,
            blockedResult = { null }
        )

        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = enabledCheck,
            blockedResult = { null },
            onFailureStatus = HookMatchStatus.DEGRADED
        )

        // 2) Contacts custom words & ContactsUtils
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
            ruleId = ruleId,
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
            shouldBlock = enabledCheck,
            blockedResult = { HashMap<Any?, Any?>() }
        )

        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = enabledCheck,
            blockedResult = { null }
        )

        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = enabledCheck,
            blockedResult = { emptyList<Any?>() },
            onFailureStatus = HookMatchStatus.DEGRADED
        )

        // 3) Installed app scan: rj0.b#b() selector == 1, ImeEnvCallback#findApp / hasInstallApp, l11.b#f1
        hookIntSelectorTask(
            resolver = resolver,
            registry = registry,
            ruleId = ruleId,
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
                BaiduJsonSanitizer.shouldBlockAppScanTask(selector, enabledCheck())
            },
            blockedResult = { null }
        )

        val imeEnvClass = resolver.resolveClass(
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
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = enabledCheck,
            blockedResult = { -1 }
        )
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = enabledCheck,
            blockedResult = { false }
        )

        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = enabledCheck,
            blockedResult = { null },
            onFailureStatus = HookMatchStatus.DEGRADED
        )
    }

    // -------------------------------------------------------------------------
    // Rule 6: RULE_BAIDU_SALVAGE_DCL
    // -------------------------------------------------------------------------

    private fun installSalvageAndDclHooks(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_BAIDU_SALVAGE_DCL

        // 1) js.a#h():void (DataCollectorHandler silent clog/flog pull)
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
            ruleId = ruleId,
            hookId = "baidu.salvage.data_collector_h",
            resolveResult = collectorResult
        ) { chain ->
            if (!isRuleFlagEnabled { it.blockRemoteLogSalvage }) {
                return@hookExecutable chain.proceed()
            }
            // Complete RxJava emitter cleanly so upstream observers do not hang.
            val emitter = chain.thisObject?.let { self ->
                runCatching<Any?> { emitterField?.get(self) }.getOrNull()
            }
            if (emitter != null) {
                runCatching { emitter.javaClass.getMethod("onComplete").invoke(emitter) }
            }
            registry.incrementBlocked(ruleId)
            null
        }

        // 2) fg.w#b(x6.a)->Observable & #c(kh0.j)->pe1.x case 5 (mobile_log_salvage).
        // Must return Observable.empty() / pe1.x.e(false) rather than null to prevent RxJava NPE.
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
            ruleId = ruleId,
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
            ruleId = ruleId,
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

        // 3) Remote unsigned DEX plugin loader: com.baidu.input.pl.impl.a#a(oi0.g):void
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
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
            shouldBlock = { isRuleFlagEnabled { it.blockUnsignedDynamicDex } },
            blockedResult = { null }
        )
    }

    // -------------------------------------------------------------------------
    // Rule 7: RULE_BAIDU_HTTPDNS (optional strict, default blockHttpDns == false)
    // -------------------------------------------------------------------------

    private fun installHttpDnsHooks(resolver: SymbolResolver, registry: HookRegistry) {
        val ruleId = HookRuleCatalog.RULE_BAIDU_HTTPDNS
        val httpDnsBlocked = { BaiduJsonSanitizer.shouldBlockHttpDns(isRuleFlagEnabled { it.blockHttpDns }) }

        val httpDnsCache = resolver.resolveClass(
            cacheKey = "baidu:http_dns_cache",
            fastPathCandidates = listOf("com.baidu.webkit.internal.daemon.HttpDnsCache")
        ).value
        for (methodName in listOf("tryToUpdateHttpDnsCache", "tryToUpdateHttpDnsCacheStaticIP")) {
            hookSimpleBlock(
                registry = registry,
                ruleId = ruleId,
                hookId = "baidu.httpdns.$methodName",
                resolveResult = resolveMethodOnClass(
                    resolver = resolver,
                    cacheKey = "baidu:httpdns_$methodName",
                    clazz = httpDnsCache,
                    methodName = methodName,
                    Context::class.java
                ),
                shouldBlock = httpDnsBlocked,
                blockedResult = { null },
                onFailureStatus = HookMatchStatus.DEGRADED
            )
        }

        val httpDnsHostCache = resolver.resolveClass(
            cacheKey = "baidu:http_dns_cache_host",
            fastPathCandidates = listOf("com.baidu.webkit.internal.daemon.HttpDnsCacheForHost")
        ).value
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
            hookId = "baidu.httpdns.tryToUpdateHttpDnsCacheHost",
            resolveResult = resolveMethodOnClass(
                resolver = resolver,
                cacheKey = "baidu:httpdns_host_update",
                clazz = httpDnsHostCache,
                methodName = "tryToUpdateHttpDnsCacheHost",
                String::class.java,
                Boolean::class.javaPrimitiveType!!
            ),
            shouldBlock = httpDnsBlocked,
            blockedResult = { null },
            onFailureStatus = HookMatchStatus.DEGRADED
        )

        val webSettingsBlink = resolver.resolveClass(
            cacheKey = "baidu:web_settings_global_blink",
            fastPathCandidates = listOf("com.baidu.webkit.internal.blink.WebSettingsGlobalBlink")
        ).value
        hookSimpleBlock(
            registry = registry,
            ruleId = ruleId,
            hookId = "baidu.httpdns.blink_update_enabled",
            resolveResult = resolveMethodOnClass(
                resolver = resolver,
                cacheKey = "baidu:httpdns_blink_enabled",
                clazz = webSettingsBlink,
                methodName = "getHttpDnsUpdateEnabled"
            ),
            shouldBlock = httpDnsBlocked,
            blockedResult = { false },
            onFailureStatus = HookMatchStatus.DEGRADED
        )

        hookEnableBaiduDnsFalse(
            resolver = resolver,
            registry = registry,
            ruleId = ruleId,
            hookId = "baidu.httpdns.turbonet_builder",
            classCacheKey = "baidu:turbonet_builder",
            methodCacheKey = "baidu:httpdns_turbonet_builder",
            className = "com.baidu.turbonet.net.TurbonetEngine\$Builder",
            httpDnsBlocked = httpDnsBlocked
        )
        hookEnableBaiduDnsFalse(
            resolver = resolver,
            registry = registry,
            ruleId = ruleId,
            hookId = "baidu.httpdns.turbonet_config",
            classCacheKey = "baidu:turbonet_config",
            methodCacheKey = "baidu:httpdns_turbonet_config",
            className = "com.baidu.turbonet.net.TurbonetConfig",
            httpDnsBlocked = httpDnsBlocked
        )
    }

    // -------------------------------------------------------------------------
    // Shared hook & reflection helpers
    // -------------------------------------------------------------------------

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
                    source = com.scluzlep.miimeprivacy.xposed.ResolveSource.MISS,
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
                    source = com.scluzlep.miimeprivacy.xposed.ResolveSource.MISS,
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
                    blockRemoteLogSalvage = isRuleFlagEnabled { it.blockRemoteLogSalvage }
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
        classCacheKey: String,
        methodCacheKey: String,
        className: String,
        crossinline httpDnsBlocked: () -> Boolean
    ) {
        val targetClass = resolver.resolveClass(
            cacheKey = classCacheKey,
            fastPathCandidates = listOf(className)
        ).value
        registry.hookExecutable(
            ruleId = ruleId,
            hookId = hookId,
            resolveResult = resolveMethodOnClass(
                resolver = resolver,
                cacheKey = methodCacheKey,
                clazz = targetClass,
                methodName = "enableBaiduDns",
                Boolean::class.javaPrimitiveType!!
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
}
