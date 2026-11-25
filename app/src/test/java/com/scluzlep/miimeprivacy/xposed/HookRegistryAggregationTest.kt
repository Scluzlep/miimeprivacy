package com.scluzlep.miimeprivacy.xposed

import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class HookRegistryAggregationTest {

    @Test
    fun nineActiveOneDegraded_countsNineOfTenAndIdentifiesDegradedHook() {
        val outcomes = listOf(
            HookRegistry.SubHookOutcome("baidu.contacts_sms.sms_check", HookMatchStatus.ACTIVE, "q30.e#o", "FAST_PATH", "Lq30/e;->o()Z"),
            HookRegistry.SubHookOutcome("baidu.contacts_sms.sms_observer", HookMatchStatus.ACTIVE, "q30.d#onChange", "FAST_PATH", "Lq30/d;->onChange(ZLandroid/net/Uri;)V"),
            HookRegistry.SubHookOutcome("baidu.contacts_sms.sms_runner", HookMatchStatus.ACTIVE, "q30.f#run", "FAST_PATH", "Lq30/f;->run()V"),
            HookRegistry.SubHookOutcome("baidu.contacts_sms.contacts_map", HookMatchStatus.ACTIVE, "t40.b#a", "FAST_PATH", "Lt40/b;->a()Ljava/util/HashMap;"),
            HookRegistry.SubHookOutcome("baidu.contacts_sms.contacts_upload", HookMatchStatus.ACTIVE, "t40.b#d", "FAST_PATH", "Lt40/b;->d(Llp0/c;Z)V"),
            HookRegistry.SubHookOutcome("baidu.contacts_sms.contacts_utils", HookMatchStatus.ACTIVE, "ContactsUtils#getContactsByContent", "FAST_PATH"),
            HookRegistry.SubHookOutcome("baidu.contacts_sms.rj0_app_scan", HookMatchStatus.ACTIVE, "rj0.b#b", "FAST_PATH", "Lrj0/b;->b()V"),
            HookRegistry.SubHookOutcome("baidu.contacts_sms.find_app", HookMatchStatus.ACTIVE, "ImeEnvCallback#findApp", "FAST_PATH"),
            HookRegistry.SubHookOutcome("baidu.contacts_sms.has_install_app", HookMatchStatus.ACTIVE, "ImeEnvCallback#hasInstallApp", "FAST_PATH"),
            HookRegistry.SubHookOutcome(
                hookId = "baidu.contacts_sms.l11_app_upload",
                status = HookMatchStatus.DEGRADED,
                matchedMethod = "l11.b#f1",
                source = "MISS",
                detail = "symbol missing"
            )
        )

        val report = HookRegistry.aggregateSubHookOutcomes(
            ruleId = HookRuleCatalog.RULE_BAIDU_SCAN_CONTACTS,
            outcomes = outcomes,
            blockedCount = 0L
        )

        assertEquals(HookMatchStatus.DEGRADED, report.status)
        assertTrue("Expected 9/10 prefix, got: ${report.matchedMethod}", report.matchedMethod.startsWith("9/10 ("))
        assertTrue(report.detail.contains("baidu.contacts_sms.l11_app_upload: symbol missing"))
        assertFalse(
            "Should not fall back to Hook #1 descriptor in detail: ${report.detail}",
            report.detail.contains("Lq30/e;->o()Z")
        )
    }

    @Test
    fun degradedBlankDetail_formatsDefaultMissingText() {
        val report = HookRegistry.aggregateSubHookOutcomes(
            ruleId = "test.rule",
            outcomes = listOf(
                HookRegistry.SubHookOutcome("sub.ok", HookMatchStatus.ACTIVE, "A#a", "FAST_PATH", "LA;->a()V"),
                HookRegistry.SubHookOutcome("sub.degraded", HookMatchStatus.DEGRADED, "B#b", "MISS", "")
            ),
            blockedCount = 0L
        )

        assertEquals(HookMatchStatus.DEGRADED, report.status)
        assertTrue(report.matchedMethod.startsWith("1/2 ("))
        assertEquals("sub.degraded: 未命中", report.detail)
    }

    @Test
    fun allDegraded_countsZeroOfTotalAndReportsDegradedStatus() {
        val report = HookRegistry.aggregateSubHookOutcomes(
            ruleId = "test.rule",
            outcomes = listOf(
                HookRegistry.SubHookOutcome("sub.d1", HookMatchStatus.DEGRADED, "A#a", "MISS", "d1 missing"),
                HookRegistry.SubHookOutcome("sub.d2", HookMatchStatus.DEGRADED, "B#b", "MISS", "")
            ),
            blockedCount = 0L
        )

        assertEquals(HookMatchStatus.DEGRADED, report.status)
        assertTrue(report.matchedMethod.startsWith("0/2 ("))
        assertEquals("sub.d1: d1 missing; sub.d2: 未命中", report.detail)
    }

    @Test
    fun zeroSucceededWithDegradedAndFailed_reportsFailedAndListsAllIssues() {
        val report = HookRegistry.aggregateSubHookOutcomes(
            ruleId = "test.rule",
            outcomes = listOf(
                HookRegistry.SubHookOutcome("sub.d1", HookMatchStatus.DEGRADED, "A#a", "MISS", "degraded miss"),
                HookRegistry.SubHookOutcome("sub.f1", HookMatchStatus.FAILED, "B#b", "MISS", "failed miss")
            ),
            blockedCount = 0L
        )

        assertEquals(HookMatchStatus.FAILED, report.status)
        assertTrue(report.matchedMethod.startsWith("0/2 ("))
        assertEquals("sub.f1: failed miss; sub.d1: degraded miss", report.detail)
    }

    @Test
    fun collectOverloadsForHook_recordsFailedOutcomeWithoutCrashingOnLinkageError() {
        val brokenClass = defineGodClassWithMissingSiblingParam()

        val lookup = HookRegistry.collectOverloadsForHook(
            clazz = brokenClass,
            methodName = "f1",
            hookIdPrefix = "baidu.test.broken_god_class"
        )

        assertTrue(lookup.targets.isEmpty())
        val failure = lookup.failureOutcome
        assertNotNull(failure)
        assertEquals(HookMatchStatus.FAILED, failure?.status)
        assertEquals("${brokenClass.name}#f1", failure?.matchedMethod)
        assertTrue(
            "Expected declaredMethods failure detail, got: ${failure?.detail}",
            failure?.detail?.contains("declaredMethods reflection failed") == true
        )
        assertTrue(lookup.error is NoClassDefFoundError)

        // Missing method on a valid class reports "no overloads matched" cleanly.
        val missingMethodLookup = HookRegistry.collectOverloadsForHook(
            clazz = String::class.java,
            methodName = "nonExistentMethodName",
            hookIdPrefix = "baidu.test.missing"
        )
        assertTrue(missingMethodLookup.targets.isEmpty())
        assertEquals(HookMatchStatus.FAILED, missingMethodLookup.failureOutcome?.status)
        assertEquals("no overloads matched", missingMethodLookup.failureOutcome?.detail)
    }

    private fun defineGodClassWithMissingSiblingParam(): Class<*> {
        val classBytes = buildAbstractClassBytes(
            internalName = "com/scluzlep/test/SyntheticGodClass",
            methods = listOf(
                "f1" to "(Ljava/lang/String;)V",
                "brokenSibling" to "(Lcom/scluzlep/nonexistent/MissingDep;)V"
            )
        )
        val loader = object : ClassLoader(HookRegistryAggregationTest::class.java.classLoader) {
            fun loadSynthetic(name: String, bytes: ByteArray): Class<*> =
                defineClass(name, bytes, 0, bytes.size)
        }
        return loader.loadSynthetic("com.scluzlep.test.SyntheticGodClass", classBytes)
    }

    private fun buildAbstractClassBytes(
        internalName: String,
        methods: List<Pair<String, String>>
    ): ByteArray {
        val cp = mutableListOf<ByteArray>()
        fun addUtf8(value: String): Int {
            val b = ByteArrayOutputStream()
            val out = DataOutputStream(b)
            out.writeByte(1)
            out.writeUTF(value)
            cp.add(b.toByteArray())
            return cp.size
        }
        fun addClass(nameIndex: Int): Int {
            val b = ByteArrayOutputStream()
            val out = DataOutputStream(b)
            out.writeByte(7)
            out.writeShort(nameIndex)
            cp.add(b.toByteArray())
            return cp.size
        }

        val thisNameIdx = addUtf8(internalName)
        val thisClassIdx = addClass(thisNameIdx)
        val superNameIdx = addUtf8("java/lang/Object")
        val superClassIdx = addClass(superNameIdx)
        val methodEntries = methods.map { (name, desc) -> addUtf8(name) to addUtf8(desc) }

        val outBytes = ByteArrayOutputStream()
        val out = DataOutputStream(outBytes)
        out.writeInt(0xCAFEBABE.toInt())
        out.writeShort(0) // minor
        out.writeShort(52) // Java 8
        out.writeShort(cp.size + 1)
        cp.forEach { out.write(it) }
        out.writeShort(0x0401) // ACC_PUBLIC | ACC_ABSTRACT
        out.writeShort(thisClassIdx)
        out.writeShort(superClassIdx)
        out.writeShort(0) // interfaces
        out.writeShort(0) // fields
        out.writeShort(methodEntries.size)
        for ((nameIdx, descIdx) in methodEntries) {
            out.writeShort(0x0401) // ACC_PUBLIC | ACC_ABSTRACT
            out.writeShort(nameIdx)
            out.writeShort(descIdx)
            out.writeShort(0) // attributes
        }
        out.writeShort(0) // class attributes
        return outBytes.toByteArray()
    }
}
