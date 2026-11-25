package com.scluzlep.miimeprivacy.xposed

import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.targets.BaiduHookPack
import com.scluzlep.miimeprivacy.xposed.targets.SogouHookPack
import com.scluzlep.miimeprivacy.xposed.targets.XiaoAiAnchors
import com.scluzlep.miimeprivacy.xposed.targets.XiaoAiHookPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class RealApkDexSimulationHarnessTest {

    @Before
    fun requireRealApks() {
        for (target in ImeTarget.entries) {
            val apk = RealApkDexSimulationHarness.apkFileFor(target)
            Assume.assumeTrue(
                "Skipping real-APK simulation test: missing ${apk.absolutePath}",
                apk.isFile
            )
        }
    }

    @Test
    fun loadsAllThreeRealApksAndResolvesClassesMethodsConstructorsAndFields() {
        for (target in ImeTarget.entries) {
            val session = RealApkDexSimulationHarness.openSimulation(
                target = target,
                resolveMode = SymbolResolveMode.DYNAMIC
            )
            assertTrue("${target.name} should parse classes", session.database.classes.size > 1000)
            assertTrue("${target.name} should parse methods", session.database.allMethods.size > 5000)
            assertTrue("${target.name} should parse fields", session.database.allFields.size > 1000)

            // Verify DexBackedClassLoader loads the IME service class
            val imeCls = Class.forName(target.imeServiceClassName, false, session.classLoader)
            assertEquals(target.imeServiceClassName, imeCls.name)
            assertTrue(DexClass(imeCls).toString().contains(target.imeServiceClassName.replace('.', '/')))

            // Verify a real constructor from the APK resolves via DexMethod + reflection
            val sampleCtor = session.database.allMethods.firstOrNull { parsed ->
                parsed.name == "<init>" &&
                    parsed.paramTypeDescriptors.isNotEmpty() &&
                    parsed.paramTypeDescriptors.all { it.startsWith("Ljava/lang/") || it.length == 1 }
            }
            assertNotNull("${target.name} should have a constructor with standard params", sampleCtor)
            val ctorInstance = DexMethod.deserialize(sampleCtor!!.descriptor)
                .getConstructorInstance(session.classLoader)
            assertNotNull(ctorInstance)
            val ctorDeclCls = Class.forName(sampleCtor.declaredClassName, false, session.classLoader)
            assertNotNull(ctorDeclCls.getDeclaredConstructor(*ctorInstance.parameterTypes))

            // Verify a real field from the APK resolves via FindField + DexField + reflection
            val sampleField = session.database.allFields.first { it.name.isNotBlank() }
            val foundFieldDescs = session.dexKitExecutor.findField(
                FindField.create().matcher(
                    FieldMatcher.create()
                        .declaredClass(sampleField.declaredClassName, StringMatchType.Equals)
                        .name(sampleField.name, StringMatchType.Equals)
                        .type(DexTypeNames.descriptorToClassName(sampleField.typeDescriptor), StringMatchType.Equals)
                )
            )
            assertTrue(
                "${target.name} FindField should match ${sampleField.descriptor}",
                foundFieldDescs.any { it.descriptor == sampleField.descriptor }
            )
            val fieldInstance = DexField.deserialize(sampleField.descriptor)
                .getFieldInstance(session.classLoader)
            assertEquals(sampleField.name, fieldInstance.name)
        }
    }

    @Test
    fun xiaoAiClassesAndJava8InterfacesLoadDeclaredMembersWithoutClassFormatError() {
        val session = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.DYNAMIC
        )
        val targetClasses = listOf(
            "com.mi.ime.MiInputMethodService",
            "i.o0",
            "xc.i",
            "kb.e",
            "m8.h",
            "rh.b",
            "rb.c",
            "pd.a"
        )
        val java8Interfaces = listOf(
            "p0.z0",
            "w.i1",
            "b1.l",
            "miuix.view.k"
        )

        for (className in targetClasses + java8Interfaces) {
            val dexClassDef = session.database.classesByName[className]
            assertNotNull("Expected $className in XiaoAi APK DEX", dexClassDef)

            val loaded = Class.forName(className, false, session.classLoader)
            assertEquals(className, loaded.name)

            val declaredMethods = loaded.declaredMethods
            val declaredCtors = loaded.declaredConstructors
            val declaredFields = loaded.declaredFields
            assertNotNull(declaredFields)

            val expectedMethods = dexClassDef!!.methods.filter { it.name != "<init>" && it.name != "<clinit>" }
            val actualMethodKeys = declaredMethods.map { m ->
                Triple(
                    m.name,
                    m.parameterTypes.map { it.typeName } to m.returnType.typeName,
                    Modifier.isStatic(m.modifiers)
                )
            }.toSet()
            for (expected in expectedMethods) {
                val expectedKey = Triple(
                    expected.name,
                    expected.paramTypeDescriptors.map { DexTypeNames.descriptorToClassName(it) } to
                        DexTypeNames.descriptorToClassName(expected.returnTypeDescriptor),
                    Modifier.isStatic(expected.modifiers)
                )
                assertTrue(
                    "Missing or mismatched method signature for $className#${expected.name}: $expectedKey",
                    expectedKey in actualMethodKeys
                )
            }

            val expectedCtors = dexClassDef.methods.filter { it.name == "<init>" }
            val actualCtorParams = declaredCtors.map { c ->
                c.parameterTypes.map { it.typeName }
            }.toSet()
            for (expectedCtor in expectedCtors) {
                val expectedParams = expectedCtor.paramTypeDescriptors.map { DexTypeNames.descriptorToClassName(it) }
                assertTrue(
                    "Missing constructor for $className with params $expectedParams",
                    expectedParams in actualCtorParams
                )
            }
        }
    }

    @Test
    fun apkBytecodeDexKitExecutorMatchesRealPackQueriesAcrossFastPathAndDynamicMode() {
        // 1. Baidu: usingStrings + usingNumbers(2439) + returnType("boolean") + paramCount(0)
        val baiduSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.DYNAMIC
        )
        baiduSession.registry.createSymbolResolver(baiduSession.classLoader).use { resolver ->
            val privacyRes = resolver.resolveMethod(
                cacheKey = "sim:baidu:privacy_2439",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("e20.g", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "a") }
                },
                dexKitFinder = { dk ->
                    dk.findMethod {
                        matcher {
                            declaredClass { usingStrings("PrivacyDialogUtils.java") }
                            usingNumbers(2439)
                            returnType("boolean")
                            paramCount(0)
                        }
                    }.singleOrNull()?.descriptor
                }
            )
            assertEquals(ResolveSource.DEXKIT, privacyRes.source)
            assertNotNull(privacyRes.value)
            val resolvedMethod: Method = privacyRes.value!!
            assertEquals("e20.g", resolvedMethod.declaringClass.name)
            assertEquals("a", resolvedMethod.name)

            // Second lookup hits CACHE
            val cachedRes = resolver.resolveMethod(
                cacheKey = "sim:baidu:privacy_2439",
                dexKitFinder = { error("Should not re-query DexKit on cache hit") }
            )
            assertEquals(ResolveSource.CACHE, cachedRes.source)
            assertEquals(privacyRes.value, cachedRes.value)
        }

        // AUTO mode resolves the same class/method via FAST_PATH
        val baiduAuto = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.AUTO
        )
        baiduAuto.registry.createSymbolResolver(baiduAuto.classLoader).use { resolver ->
            val fastRes = resolver.resolveMethod(
                cacheKey = "sim:baidu:privacy_2439",
                fastPath = { cl ->
                    SymbolResolver.loadClassOrNull("e20.g", cl)
                        ?.let { SymbolResolver.findDeclaredMethodOrNull(it, "a") }
                }
            )
            assertEquals(ResolveSource.FAST_PATH, fastRes.source)
            assertNotNull(fastRes.value)
        }

        // 2. Sogou: searchPackages + ClassMatcher usingStrings
        val sogouSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.SOGOU,
            resolveMode = SymbolResolveMode.DYNAMIC
        )
        sogouSession.registry.createSymbolResolver(sogouSession.classLoader).use { resolver ->
            val oaidRes = resolver.resolveClass(
                cacheKey = "sim:sogou:oaid",
                fastPathCandidates = listOf("com.sogou.lib.device.oaid.c"),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create()
                            .searchPackages("com.sohu", "com.sogou", "sogou")
                            .matcher(
                                ClassMatcher.create().usingStrings(
                                    listOf("tencent_oaid", "200100"),
                                    StringMatchType.Contains
                                )
                            )
                    ).firstOrNull()?.descriptor
                }
            )
            assertEquals(ResolveSource.DEXKIT, oaidRes.source)
            assertEquals("com.sogou.lib.device.oaid.c", oaidRes.value?.name)
        }

        // 3. XiaoAi: CTA activity class + method via ApkBytecodeDexKitExecutor
        val xiaoAiSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.DYNAMIC
        )
        xiaoAiSession.registry.createSymbolResolver(xiaoAiSession.classLoader).use { resolver ->
            val ctaRes = resolver.resolveClass(
                cacheKey = "sim:xiaoai:cta",
                fastPathCandidates = listOf(XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY),
                dexKitFinder = { dk ->
                    dk.findClass(
                        FindClass.create().matcher(
                            ClassMatcher.create().usingStrings(XiaoAiAnchors.CTA_ACTIVITY_STRINGS)
                        )
                    ).firstOrNull()?.descriptor
                }
            )
            assertEquals(ResolveSource.DEXKIT, ctaRes.source)
            assertEquals(XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY, ctaRes.value?.name)

            val oneTrackImpRes = resolver.resolveMethod(
                cacheKey = "sim:xiaoai:onetrack_imp",
                dexKitFinder = { dk ->
                    dk.findMethod(
                        FindMethod.create().matcher(
                            MethodMatcher.create()
                                .declaredClass(XiaoAiAnchors.CLASS_ONETRACK_IMP)
                                .usingStrings(XiaoAiAnchors.ONETRACK_IMP_STRINGS)
                        )
                    ).firstOrNull()?.descriptor
                }
            )
            assertEquals(ResolveSource.DEXKIT, oneTrackImpRes.source)
            assertNotNull(oneTrackImpRes.value)
        }
    }

    @Test
    fun hookPacksInstallEndToEndOnRealApksInBothAutoAndDynamicModes() {
        val packs = listOf(
            ImeTarget.SOGOU to SogouHookPack,
            ImeTarget.BAIDU to BaiduHookPack,
            ImeTarget.XIAOAI to XiaoAiHookPack
        )

        for ((target, pack) in packs) {
            val expectedNonOverlay = HookRuleCatalog.rulesFor(target)
                .count { !it.isOverlayPill }

            for (mode in listOf(SymbolResolveMode.AUTO, SymbolResolveMode.DYNAMIC)) {
                val session = RealApkDexSimulationHarness.openSimulation(
                    target = target,
                    resolveMode = mode
                )
                val result = session.installPack(pack)
                assertEquals(target, result.target)
                assertTrue(
                    "${target.name} [$mode] should emit rule reports",
                    result.reports.isNotEmpty()
                )
                val nonOverlayDiagnostics = session.registry.snapshotDiagnostics().rules
                    .filterNot { it.ruleId == HookRuleCatalog.overlayRuleIdFor(target) }
                assertEquals(
                    "${target.name} [$mode] should cover all $expectedNonOverlay non-overlay rules",
                    expectedNonOverlay,
                    nonOverlayDiagnostics.size
                )
                assertTrue(
                    "${target.name} [$mode] should install hooks via RecordingXposedInterface",
                    session.xposed.recordedHooks.isNotEmpty()
                )
                val activeCount = result.reports.count {
                    it.status == HookMatchStatus.ACTIVE || it.status == HookMatchStatus.FALLBACK
                }
                assertTrue(
                    "${target.name} [$mode] expected active/fallback rules > 0, got $activeCount",
                    activeCount > 0
                )
                if (mode == SymbolResolveMode.DYNAMIC) {
                    assertTrue(
                        "${target.name} [DYNAMIC] should resolve rules via DEXKIT",
                        result.reports.any { it.source.contains("DEXKIT") }
                    )
                    assertTrue(
                        "${target.name} [DYNAMIC] should record resolutionTraces without FAST_PATH",
                        session.resolutionTraces.isNotEmpty() &&
                            session.resolutionTraces.none { it.source == ResolveSource.FAST_PATH }
                    )
                }
            }
        }
    }
}
