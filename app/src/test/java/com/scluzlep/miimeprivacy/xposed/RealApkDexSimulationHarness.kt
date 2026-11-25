package com.scluzlep.miimeprivacy.xposed

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.ParcelFileDescriptor
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.model.ImeTarget
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.MatchType
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.enums.UsingType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.EncodeValueByte
import org.luckypray.dexkit.query.matchers.EncodeValueDouble
import org.luckypray.dexkit.query.matchers.EncodeValueFloat
import org.luckypray.dexkit.query.matchers.EncodeValueInt
import org.luckypray.dexkit.query.matchers.EncodeValueLong
import org.luckypray.dexkit.query.matchers.EncodeValueShort
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.FieldsMatcher
import org.luckypray.dexkit.query.matchers.InterfacesMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.MethodsMatcher
import org.luckypray.dexkit.query.matchers.ParametersMatcher
import org.luckypray.dexkit.query.matchers.UsingFieldMatcher
import org.luckypray.dexkit.query.matchers.base.AccessFlagsMatcher
import org.luckypray.dexkit.query.matchers.base.IntRange as DexIntRange
import org.luckypray.dexkit.query.matchers.base.NumberEncodeValueMatcher
import org.luckypray.dexkit.query.matchers.base.OpCodesMatcher
import org.luckypray.dexkit.query.matchers.base.StringMatcher
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Pure-JVM real-APK Dalvik bytecode simulation harness for testing [ImeHookPack]s end-to-end
 * against real Xiaomi IME APKs without an Android runtime or native `libdexkit.so`.
 */
object RealApkDexSimulationHarness {

    val DEFAULT_APK_DIR = File("d:/antigproj/xiaomiime")

    val APK_FILENAMES: Map<ImeTarget, String> = mapOf(
        ImeTarget.SOGOU to "搜狗输入法小米版_10.32.21.202609081832.apk",
        ImeTarget.BAIDU to "百度输入法小米版_10.6.125.158.apk",
        ImeTarget.XIAOAI to "超级小爱输入法_0.2.1363.60772063.apk"
    )

    private val databaseCache = ConcurrentHashMap<String, ApkDexDatabase>()

    fun apkFileFor(target: ImeTarget, apkDir: File = DEFAULT_APK_DIR): File =
        File(apkDir, APK_FILENAMES.getValue(target))

    fun loadDatabase(apkFile: File): ApkDexDatabase {
        require(apkFile.isFile) { "Missing APK file: ${apkFile.absolutePath}" }
        val key = "${apkFile.absolutePath}:${apkFile.length()}:${apkFile.lastModified()}"
        return databaseCache.computeIfAbsent(key) { ApkDexDatabase.parseApk(apkFile) }
    }

    fun openSimulation(
        target: ImeTarget,
        apkDir: File = DEFAULT_APK_DIR,
        resolveMode: SymbolResolveMode = SymbolResolveMode.AUTO,
        config: PrivacyConfig = PrivacyConfig(),
        prefs: SharedPreferences = InMemorySharedPreferences()
    ): SimulationSession {
        val apkFile = apkFileFor(target, apkDir)
        val db = loadDatabase(apkFile)
        val classLoader = DexBackedClassLoader(db, RealApkDexSimulationHarness::class.java.classLoader)
        val executor = ApkBytecodeDexKitExecutor(db)
        val appInfo = ApplicationInfo().apply {
            packageName = target.packageName
            sourceDir = apkFile.absolutePath
            publicSourceDir = apkFile.absolutePath
        }
        val remotePrefs = InMemorySharedPreferences()
        val effectiveConfig = config
            .withResolveMode(target, resolveMode)
            .let { cfg ->
                val minStamp = ConfigBridge.current.updatedAtMs + 1L
                if (cfg.updatedAtMs >= minStamp) cfg else cfg.copy(updatedAtMs = minStamp)
            }
        effectiveConfig.toSharedPreferences(remotePrefs, commitSync = true)
        val xposed = RecordingXposedInterface(
            moduleAppInfo = appInfo,
            remotePrefsMap = mapOf(ConfigContract.REMOTE_PREFS_NAME to remotePrefs)
        )
        ConfigBridge.detach()
        ConfigBridge.attachRemote(xposed)
        val resolutionTraces = Collections.synchronizedList(mutableListOf<SymbolResolutionTrace>())
        val registry = HookRegistry(
            xposed = xposed,
            target = target,
            appInfo = appInfo,
            packageVersionCode = target.expectedVersionCode,
            packageVersionName = "",
            processName = target.packageName,
            oldById = null,
            dexKitExecutorOverride = executor,
            symbolPrefsOverride = prefs,
            onResolveTrace = { trace -> resolutionTraces.add(trace) }
        )
        return SimulationSession(
            target = target,
            apkFile = apkFile,
            database = db,
            classLoader = classLoader,
            dexKitExecutor = executor,
            appInfo = appInfo,
            xposed = xposed,
            registry = registry,
            prefs = prefs,
            resolutionTraces = resolutionTraces
        )
    }

    data class SimulationSession(
        val target: ImeTarget,
        val apkFile: File,
        val database: ApkDexDatabase,
        val classLoader: DexBackedClassLoader,
        val dexKitExecutor: ApkBytecodeDexKitExecutor,
        val appInfo: ApplicationInfo,
        val xposed: RecordingXposedInterface,
        val registry: HookRegistry,
        val prefs: SharedPreferences,
        val resolutionTraces: MutableList<SymbolResolutionTrace>
    ) {
        fun installPack(pack: ImeHookPack): ImePackInstallResult =
            pack.install(
                context = null,
                classLoader = classLoader,
                appInfo = appInfo,
                registry = registry
            )
    }
}

/**
 * Recording [XposedInterface] double for JVM hook-pack simulation tests.
 */
class RecordingXposedInterface(
    private val moduleAppInfo: ApplicationInfo = ApplicationInfo(),
    private val remotePrefsMap: Map<String, SharedPreferences> = emptyMap()
) : XposedInterface {

    data class RecordedHook(
        val id: String?,
        val executable: Executable,
        val priority: Int,
        val exceptionMode: XposedInterface.ExceptionMode?,
        var hooker: XposedInterface.Hooker,
        var active: Boolean = true
    )

    val recordedHooks = mutableListOf<RecordedHook>()
    val logs = mutableListOf<String>()

    override fun getFrameworkName(): String = "RealApkSimXposed"

    override fun getFrameworkVersion(): String = "102.0"

    override fun getFrameworkVersionCode(): Long = 10200L

    override fun getFrameworkProperties(): Long = 0L

    override fun hook(executable: Executable): XposedInterface.HookBuilder {
        return object : XposedInterface.HookBuilder {
            private var id: String? = null
            private var priority: Int = XposedInterface.PRIORITY_DEFAULT
            private var exceptionMode: XposedInterface.ExceptionMode? = null

            override fun setPriority(priority: Int): XposedInterface.HookBuilder = apply {
                this.priority = priority
            }

            override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = apply {
                this.exceptionMode = mode
            }

            override fun setId(id: String?): XposedInterface.HookBuilder = apply {
                this.id = id
            }

            override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                val entry = RecordedHook(
                    id = id,
                    executable = executable,
                    priority = priority,
                    exceptionMode = exceptionMode,
                    hooker = hooker
                )
                synchronized(recordedHooks) {
                    recordedHooks.add(entry)
                }
                return object : XposedInterface.HookHandle {
                    override fun getExecutable(): Executable = executable

                    override fun unhook() {
                        entry.active = false
                    }

                    override fun getId(): String? = id

                    override fun replaceHook(newHooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                        entry.hooker = newHooker
                        return this
                    }
                }
            }
        }
    }

    override fun hookClassInitializer(clazz: Class<*>): XposedInterface.HookBuilder {
        val dummyCtor = clazz.declaredConstructors.firstOrNull()
            ?: Any::class.java.getDeclaredConstructor()
        return hook(dummyCtor)
    }

    override fun deoptimize(executable: Executable): Boolean = true

    override fun getInvoker(method: Method): XposedInterface.Invoker<*, Method> {
        throw UnsupportedOperationException("Invoker not supported in JVM simulation")
    }

    override fun <T : Any?> getInvoker(constructor: Constructor<T>): XposedInterface.CtorInvoker<T> {
        throw UnsupportedOperationException("CtorInvoker not supported in JVM simulation")
    }

    override fun log(priority: Int, tag: String?, msg: String) {
        synchronized(logs) { logs.add("[$priority][$tag] $msg") }
    }

    override fun log(priority: Int, tag: String?, msg: String, t: Throwable?) {
        synchronized(logs) { logs.add("[$priority][$tag] $msg (${t?.javaClass?.simpleName}: ${t?.message})") }
    }

    override fun getModuleApplicationInfo(): ApplicationInfo = moduleAppInfo

    override fun getRemotePreferences(name: String): SharedPreferences =
        remotePrefsMap[name] ?: InMemorySharedPreferences()

    override fun listRemoteFiles(): Array<String> = emptyArray()

    override fun openRemoteFile(name: String): ParcelFileDescriptor {
        throw FileNotFoundException(name)
    }
}

class InMemorySharedPreferences : SharedPreferences {
    private val data = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private val removals = hashSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = values?.toSet()
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) removals.add(key)
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearAll = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) data.clear()
            removals.forEach { data.remove(it) }
            pending.forEach { (k, v) ->
                if (v == null) data.remove(k) else data[k] = v
            }
        }
    }
}

// =============================================================================
// DEX Binary Parser & Bytecode Instruction Scanner
// =============================================================================

class DexFileIndex(
    val dexName: String,
    val strings: Array<String>,
    val typeDescriptors: Array<String>,
    val protoReturnTypeIdx: IntArray,
    val protoParamTypeIdxs: Array<IntArray>,
    val fieldClassIdx: IntArray,
    val fieldTypeIdx: IntArray,
    val fieldNameIdx: IntArray,
    val fieldDescriptors: Array<String>,
    val methodClassIdx: IntArray,
    val methodProtoIdx: IntArray,
    val methodNameIdx: IntArray,
    val methodDescriptors: Array<String>
) {
    fun fieldDescriptor(idx: Int): String = fieldDescriptors[idx]
    fun fieldName(idx: Int): String = strings[fieldNameIdx[idx]]
    fun fieldTypeDescriptor(idx: Int): String = typeDescriptors[fieldTypeIdx[idx]]
    fun fieldClassDescriptor(idx: Int): String = typeDescriptors[fieldClassIdx[idx]]

    fun methodDescriptor(idx: Int): String = methodDescriptors[idx]
    fun methodName(idx: Int): String = strings[methodNameIdx[idx]]
    fun methodClassDescriptor(idx: Int): String = typeDescriptors[methodClassIdx[idx]]
    fun methodReturnTypeDescriptor(idx: Int): String = typeDescriptors[protoReturnTypeIdx[methodProtoIdx[idx]]]
    fun methodParamTypeDescriptors(idx: Int): List<String> =
        protoParamTypeIdxs[methodProtoIdx[idx]].map { typeDescriptors[it] }
}

class ParsedField(
    val dex: DexFileIndex,
    val fieldIdx: Int,
    val modifiers: Int
) {
    val descriptor: String get() = dex.fieldDescriptors[fieldIdx]
    val declaredClassDescriptor: String get() = dex.fieldClassDescriptor(fieldIdx)
    val declaredClassName: String get() = DexTypeNames.descriptorToClassName(declaredClassDescriptor)
    val name: String get() = dex.fieldName(fieldIdx)
    val typeDescriptor: String get() = dex.fieldTypeDescriptor(fieldIdx)
}

class ParsedMethod(
    val dex: DexFileIndex,
    val methodIdx: Int,
    val modifiers: Int,
    val usingStringIds: IntArray,
    val usingNumbers: LongArray,
    val readFieldIds: IntArray,
    val writeFieldIds: IntArray,
    val invokeMethodIds: IntArray,
    val opCodes: IntArray
) {
    val descriptor: String get() = dex.methodDescriptors[methodIdx]
    val declaredClassDescriptor: String get() = dex.methodClassDescriptor(methodIdx)
    val declaredClassName: String get() = DexTypeNames.descriptorToClassName(declaredClassDescriptor)
    val name: String get() = dex.methodName(methodIdx)
    val returnTypeDescriptor: String get() = dex.methodReturnTypeDescriptor(methodIdx)
    val paramTypeDescriptors: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dex.methodParamTypeDescriptors(methodIdx)
    }
    val usingStrings: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (usingStringIds.isEmpty()) emptyList() else usingStringIds.map { dex.strings[it] }
    }
}

class ParsedClassDef(
    val dex: DexFileIndex,
    val descriptor: String,
    val className: String,
    val modifiers: Int,
    val superClassDescriptor: String?,
    val interfaceDescriptors: List<String>,
    val sourceFile: String?,
    val fields: List<ParsedField>,
    val methods: List<ParsedMethod>,
    val staticStringIds: IntArray,
    val staticNumbers: LongArray
) {
    val allUsingStrings: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val out = LinkedHashSet<String>()
        for (sid in staticStringIds) {
            out.add(dex.strings[sid])
        }
        for (m in methods) {
            for (sid in m.usingStringIds) {
                out.add(dex.strings[sid])
            }
        }
        out
    }
}

class ApkDexDatabase private constructor(
    val apkFile: File,
    val dexFiles: List<DexFileIndex>,
    val classes: List<ParsedClassDef>,
    val classesByDescriptor: Map<String, ParsedClassDef>,
    val classesByName: Map<String, ParsedClassDef>,
    val allMethods: List<ParsedMethod>,
    val allFields: List<ParsedField>,
    val referencedTypes: Set<String>,
    val referencedInterfaces: Set<String>
) {
    val methodsByDescriptor: Map<String, ParsedMethod> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val map = HashMap<String, ParsedMethod>(allMethods.size * 2)
        for (m in allMethods) {
            map.putIfAbsent(m.descriptor, m)
        }
        map
    }

    val fieldsByDescriptor: Map<String, ParsedField> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val map = HashMap<String, ParsedField>(allFields.size * 2)
        for (f in allFields) {
            map.putIfAbsent(f.descriptor, f)
        }
        map
    }

    val callersByMethodDescriptor: Map<String, List<ParsedMethod>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val map = HashMap<String, MutableList<ParsedMethod>>(allMethods.size / 2)
        for (m in allMethods) {
            val invIds = m.invokeMethodIds
            if (invIds.isEmpty()) continue
            val seen = HashSet<String>(invIds.size * 2)
            for (mid in invIds) {
                val targetDesc = m.dex.methodDescriptors[mid]
                if (seen.add(targetDesc)) {
                    map.computeIfAbsent(targetDesc) { ArrayList(2) }.add(m)
                }
            }
        }
        map
    }

    val readersByFieldDescriptor: Map<String, List<ParsedMethod>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val map = HashMap<String, MutableList<ParsedMethod>>()
        for (m in allMethods) {
            val fIds = m.readFieldIds
            if (fIds.isEmpty()) continue
            val seen = HashSet<String>(fIds.size * 2)
            for (fid in fIds) {
                val targetDesc = m.dex.fieldDescriptors[fid]
                if (seen.add(targetDesc)) {
                    map.computeIfAbsent(targetDesc) { ArrayList(2) }.add(m)
                }
            }
        }
        map
    }

    val writersByFieldDescriptor: Map<String, List<ParsedMethod>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val map = HashMap<String, MutableList<ParsedMethod>>()
        for (m in allMethods) {
            val fIds = m.writeFieldIds
            if (fIds.isEmpty()) continue
            val seen = HashSet<String>(fIds.size * 2)
            for (fid in fIds) {
                val targetDesc = m.dex.fieldDescriptors[fid]
                if (seen.add(targetDesc)) {
                    map.computeIfAbsent(targetDesc) { ArrayList(2) }.add(m)
                }
            }
        }
        map
    }

    companion object {
        private const val NO_INDEX = -1
        private val EMPTY_INT_ARRAY = IntArray(0)
        private val EMPTY_LONG_ARRAY = LongArray(0)
        private val DEX_ENTRY_REGEX = Regex("""^classes(\d*)\.dex$""")

        fun parseApk(apkFile: File): ApkDexDatabase {
            val dexFiles = mutableListOf<DexFileIndex>()
            val allClasses = mutableListOf<ParsedClassDef>()
            val classesByDescriptor = HashMap<String, ParsedClassDef>(16384)
            val classesByName = HashMap<String, ParsedClassDef>(16384)
            val allMethods = mutableListOf<ParsedMethod>()
            val allFields = mutableListOf<ParsedField>()
            val referencedTypes = HashSet<String>(32768)
            val referencedInterfaces = HashSet<String>(4096)

            ZipFile(apkFile).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .mapNotNull { entry ->
                        val match = DEX_ENTRY_REGEX.matchEntire(entry.name) ?: return@mapNotNull null
                        val order = match.groupValues[1].toIntOrNull() ?: 1
                        order to entry
                    }
                    .sortedBy { it.first }
                    .toList()

                require(dexEntries.isNotEmpty()) { "No classes*.dex entries in ${apkFile.name}" }

                for ((_, entry) in dexEntries) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    parseSingleDex(
                        dexName = entry.name,
                        bytes = bytes,
                        dexFiles = dexFiles,
                        allClasses = allClasses,
                        classesByDescriptor = classesByDescriptor,
                        classesByName = classesByName,
                        allMethods = allMethods,
                        allFields = allFields,
                        referencedTypes = referencedTypes,
                        referencedInterfaces = referencedInterfaces
                    )
                }
            }

            return ApkDexDatabase(
                apkFile = apkFile,
                dexFiles = dexFiles,
                classes = allClasses,
                classesByDescriptor = classesByDescriptor,
                classesByName = classesByName,
                allMethods = allMethods,
                allFields = allFields,
                referencedTypes = referencedTypes,
                referencedInterfaces = referencedInterfaces
            )
        }

        private fun parseSingleDex(
            dexName: String,
            bytes: ByteArray,
            dexFiles: MutableList<DexFileIndex>,
            allClasses: MutableList<ParsedClassDef>,
            classesByDescriptor: MutableMap<String, ParsedClassDef>,
            classesByName: MutableMap<String, ParsedClassDef>,
            allMethods: MutableList<ParsedMethod>,
            allFields: MutableList<ParsedField>,
            referencedTypes: MutableSet<String>,
            referencedInterfaces: MutableSet<String>
        ) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(bytes.size >= 0x70 && bytes[0] == 'd'.code.toByte() && bytes[1] == 'e'.code.toByte() && bytes[2] == 'x'.code.toByte()) {
                "Invalid DEX header in $dexName"
            }

            val stringIdsSize = buf.getInt(0x38)
            val stringIdsOff = buf.getInt(0x3C)
            val typeIdsSize = buf.getInt(0x40)
            val typeIdsOff = buf.getInt(0x44)
            val protoIdsSize = buf.getInt(0x48)
            val protoIdsOff = buf.getInt(0x4C)
            val fieldIdsSize = buf.getInt(0x50)
            val fieldIdsOff = buf.getInt(0x54)
            val methodIdsSize = buf.getInt(0x58)
            val methodIdsOff = buf.getInt(0x5C)
            val classDefsSize = buf.getInt(0x60)
            val classDefsOff = buf.getInt(0x64)

            val strings = Array(stringIdsSize) { i ->
                val strOff = buf.getInt(stringIdsOff + i * 4)
                readMutf8String(bytes, strOff)
            }

            val typeDescriptors = Array(typeIdsSize) { i ->
                val strIdx = buf.getInt(typeIdsOff + i * 4)
                val desc = strings[strIdx]
                if (desc.startsWith("L") && desc.endsWith(";")) {
                    referencedTypes.add(DexTypeNames.descriptorToClassName(desc))
                } else if (desc.startsWith("[")) {
                    val elem = desc.trimStart('[')
                    if (elem.startsWith("L") && elem.endsWith(";")) {
                        referencedTypes.add(DexTypeNames.descriptorToClassName(elem))
                    }
                }
                desc
            }

            val protoReturnTypeIdx = IntArray(protoIdsSize)
            val protoParamTypeIdxs = Array(protoIdsSize) { i ->
                val base = protoIdsOff + i * 12
                protoReturnTypeIdx[i] = buf.getInt(base + 4)
                val paramsOff = buf.getInt(base + 8)
                if (paramsOff == 0) {
                    EMPTY_INT_ARRAY
                } else {
                    val count = buf.getInt(paramsOff)
                    IntArray(count) { p -> buf.getShort(paramsOff + 4 + p * 2).toInt() and 0xFFFF }
                }
            }

            val fieldClassIdx = IntArray(fieldIdsSize)
            val fieldTypeIdx = IntArray(fieldIdsSize)
            val fieldNameIdx = IntArray(fieldIdsSize)
            val fieldDescriptors = Array(fieldIdsSize) { i ->
                val base = fieldIdsOff + i * 8
                val cIdx = buf.getShort(base).toInt() and 0xFFFF
                val tIdx = buf.getShort(base + 2).toInt() and 0xFFFF
                val nIdx = buf.getInt(base + 4)
                fieldClassIdx[i] = cIdx
                fieldTypeIdx[i] = tIdx
                fieldNameIdx[i] = nIdx
                "${typeDescriptors[cIdx]}->${strings[nIdx]}:${typeDescriptors[tIdx]}"
            }

            val methodClassIdx = IntArray(methodIdsSize)
            val methodProtoIdx = IntArray(methodIdsSize)
            val methodNameIdx = IntArray(methodIdsSize)
            val methodDescriptors = Array(methodIdsSize) { i ->
                val base = methodIdsOff + i * 8
                val cIdx = buf.getShort(base).toInt() and 0xFFFF
                val pIdx = buf.getShort(base + 2).toInt() and 0xFFFF
                val nIdx = buf.getInt(base + 4)
                methodClassIdx[i] = cIdx
                methodProtoIdx[i] = pIdx
                methodNameIdx[i] = nIdx
                buildString {
                    append(typeDescriptors[cIdx])
                    append("->")
                    append(strings[nIdx])
                    append('(')
                    for (pt in protoParamTypeIdxs[pIdx]) {
                        append(typeDescriptors[pt])
                    }
                    append(')')
                    append(typeDescriptors[protoReturnTypeIdx[pIdx]])
                }
            }

            val dexIndex = DexFileIndex(
                dexName = dexName,
                strings = strings,
                typeDescriptors = typeDescriptors,
                protoReturnTypeIdx = protoReturnTypeIdx,
                protoParamTypeIdxs = protoParamTypeIdxs,
                fieldClassIdx = fieldClassIdx,
                fieldTypeIdx = fieldTypeIdx,
                fieldNameIdx = fieldNameIdx,
                fieldDescriptors = fieldDescriptors,
                methodClassIdx = methodClassIdx,
                methodProtoIdx = methodProtoIdx,
                methodNameIdx = methodNameIdx,
                methodDescriptors = methodDescriptors
            )
            dexFiles.add(dexIndex)

            val reader = Cursor(bytes)
            for (i in 0 until classDefsSize) {
                val base = classDefsOff + i * 32
                val classIdx = buf.getInt(base)
                val accessFlags = buf.getInt(base + 4)
                val superIdx = buf.getInt(base + 8)
                val interfacesOff = buf.getInt(base + 12)
                val sourceFileIdx = buf.getInt(base + 16)
                val classDataOff = buf.getInt(base + 24)
                val staticValuesOff = buf.getInt(base + 28)

                val descriptor = typeDescriptors[classIdx]
                val className = DexTypeNames.descriptorToClassName(descriptor)
                val superDescriptor = if (superIdx == NO_INDEX) null else typeDescriptors[superIdx]
                val interfaces = if (interfacesOff == 0) {
                    emptyList()
                } else {
                    val ifaceCount = buf.getInt(interfacesOff)
                    List(ifaceCount) { idx ->
                        val tIdx = buf.getShort(interfacesOff + 4 + idx * 2).toInt() and 0xFFFF
                        val ifaceDesc = typeDescriptors[tIdx]
                        referencedInterfaces.add(DexTypeNames.descriptorToClassName(ifaceDesc))
                        ifaceDesc
                    }
                }
                val sourceFile = if (sourceFileIdx == NO_INDEX) null else strings[sourceFileIdx]

                val fields = mutableListOf<ParsedField>()
                val methods = mutableListOf<ParsedMethod>()
                if (classDataOff != 0) {
                    reader.pos = classDataOff
                    val staticFieldsSize = reader.readUleb128()
                    val instanceFieldsSize = reader.readUleb128()
                    val directMethodsSize = reader.readUleb128()
                    val virtualMethodsSize = reader.readUleb128()

                    var fieldIdx = 0
                    for (f in 0 until staticFieldsSize) {
                        fieldIdx += reader.readUleb128()
                        val flags = reader.readUleb128()
                        fields.add(ParsedField(dexIndex, fieldIdx, flags))
                    }
                    fieldIdx = 0
                    for (f in 0 until instanceFieldsSize) {
                        fieldIdx += reader.readUleb128()
                        val flags = reader.readUleb128()
                        fields.add(ParsedField(dexIndex, fieldIdx, flags))
                    }

                    var methodIdx = 0
                    for (m in 0 until directMethodsSize) {
                        methodIdx += reader.readUleb128()
                        val flags = reader.readUleb128()
                        val codeOff = reader.readUleb128()
                        methods.add(parseMethod(dexIndex, methodIdx, flags, codeOff, buf))
                    }
                    methodIdx = 0
                    for (m in 0 until virtualMethodsSize) {
                        methodIdx += reader.readUleb128()
                        val flags = reader.readUleb128()
                        val codeOff = reader.readUleb128()
                        methods.add(parseMethod(dexIndex, methodIdx, flags, codeOff, buf))
                    }
                }

                val staticStringsList = if (staticValuesOff != 0) mutableListOf<Int>() else null
                val staticNumbersList = if (staticValuesOff != 0) mutableListOf<Long>() else null
                if (staticValuesOff != 0 && staticStringsList != null && staticNumbersList != null) {
                    reader.pos = staticValuesOff
                    parseEncodedArrayConstants(reader, staticStringsList, staticNumbersList)
                }

                if (!classesByDescriptor.containsKey(descriptor)) {
                    val classDef = ParsedClassDef(
                        dex = dexIndex,
                        descriptor = descriptor,
                        className = className,
                        modifiers = accessFlags,
                        superClassDescriptor = superDescriptor,
                        interfaceDescriptors = interfaces,
                        sourceFile = sourceFile,
                        fields = fields,
                        methods = methods,
                        staticStringIds = staticStringsList?.toIntArray() ?: EMPTY_INT_ARRAY,
                        staticNumbers = staticNumbersList?.toLongArray() ?: EMPTY_LONG_ARRAY
                    )
                    classesByDescriptor[descriptor] = classDef
                    classesByName[className] = classDef
                    allClasses.add(classDef)
                    allFields.addAll(fields)
                    allMethods.addAll(methods)
                }
            }
        }

        private fun parseMethod(
            dex: DexFileIndex,
            methodIdx: Int,
            modifiers: Int,
            codeOff: Int,
            buf: ByteBuffer
        ): ParsedMethod {
            if (codeOff == 0) {
                return ParsedMethod(
                    dex = dex,
                    methodIdx = methodIdx,
                    modifiers = modifiers,
                    usingStringIds = EMPTY_INT_ARRAY,
                    usingNumbers = EMPTY_LONG_ARRAY,
                    readFieldIds = EMPTY_INT_ARRAY,
                    writeFieldIds = EMPTY_INT_ARRAY,
                    invokeMethodIds = EMPTY_INT_ARRAY,
                    opCodes = EMPTY_INT_ARRAY
                )
            }

            val insnsSize = buf.getInt(codeOff + 12)
            val insnsBase = codeOff + 16
            if (insnsSize <= 0) {
                return ParsedMethod(
                    dex = dex,
                    methodIdx = methodIdx,
                    modifiers = modifiers,
                    usingStringIds = EMPTY_INT_ARRAY,
                    usingNumbers = EMPTY_LONG_ARRAY,
                    readFieldIds = EMPTY_INT_ARRAY,
                    writeFieldIds = EMPTY_INT_ARRAY,
                    invokeMethodIds = EMPTY_INT_ARRAY,
                    opCodes = EMPTY_INT_ARRAY
                )
            }

            var strIds: MutableList<Int>? = null
            var numbers: MutableList<Long>? = null
            var readFields: MutableList<Int>? = null
            var writeFields: MutableList<Int>? = null
            var invokes: MutableList<Int>? = null
            val ops = IntArray(minOf(insnsSize, 4096))
            var opCount = 0

            var pc = 0
            while (pc < insnsSize) {
                val unit = buf.getShort(insnsBase + pc * 2).toInt() and 0xFFFF
                val opcode = unit and 0xFF

                if (opcode == 0x00) {
                    when (unit and 0xFF00) {
                        0x0100 -> { // packed-switch-payload
                            if (pc + 1 < insnsSize) {
                                val size = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                                pc += size * 2 + 4
                                continue
                            }
                        }
                        0x0200 -> { // sparse-switch-payload
                            if (pc + 1 < insnsSize) {
                                val size = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                                pc += size * 4 + 2
                                continue
                            }
                        }
                        0x0300 -> { // fill-array-data-payload
                            if (pc + 3 < insnsSize) {
                                val elemWidth = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                                val sizeLow = buf.getShort(insnsBase + (pc + 2) * 2).toInt() and 0xFFFF
                                val sizeHigh = buf.getShort(insnsBase + (pc + 3) * 2).toInt() and 0xFFFF
                                val size = (sizeHigh shl 16) or sizeLow
                                val dataUnits = ((size.toLong() * elemWidth.toLong() + 1L) / 2L).toInt()
                                pc += maxOf(4, dataUnits + 4)
                                continue
                            }
                        }
                    }
                }

                if (opCount < ops.size) {
                    ops[opCount++] = opcode
                }

                val width = DALVIK_OPCODE_WIDTHS[opcode]
                if (pc + width > insnsSize) break

                when (opcode) {
                    0x12 -> { // const/4
                        val value = ((unit shl 16) shr 28).toLong()
                        if (numbers == null) numbers = ArrayList(4)
                        numbers.add(value)
                    }
                    0x13 -> { // const/16
                        val value = buf.getShort(insnsBase + (pc + 1) * 2).toLong()
                        if (numbers == null) numbers = ArrayList(4)
                        numbers.add(value)
                    }
                    0x14 -> { // const
                        val lo = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                        val hi = buf.getShort(insnsBase + (pc + 2) * 2).toInt()
                        val value = ((hi shl 16) or lo).toLong()
                        if (numbers == null) numbers = ArrayList(4)
                        numbers.add(value)
                    }
                    0x15 -> { // const/high16
                        val hi = buf.getShort(insnsBase + (pc + 1) * 2).toInt()
                        val value = (hi shl 16).toLong()
                        if (numbers == null) numbers = ArrayList(4)
                        numbers.add(value)
                    }
                    0x16 -> { // const-wide/16
                        val value = buf.getShort(insnsBase + (pc + 1) * 2).toLong()
                        if (numbers == null) numbers = ArrayList(4)
                        numbers.add(value)
                    }
                    0x17 -> { // const-wide/32
                        val lo = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                        val hi = buf.getShort(insnsBase + (pc + 2) * 2).toInt()
                        val value = ((hi shl 16) or lo).toLong()
                        if (numbers == null) numbers = ArrayList(4)
                        numbers.add(value)
                    }
                    0x18 -> { // const-wide
                        val u1 = buf.getShort(insnsBase + (pc + 1) * 2).toLong() and 0xFFFFL
                        val u2 = buf.getShort(insnsBase + (pc + 2) * 2).toLong() and 0xFFFFL
                        val u3 = buf.getShort(insnsBase + (pc + 3) * 2).toLong() and 0xFFFFL
                        val u4 = buf.getShort(insnsBase + (pc + 4) * 2).toLong() and 0xFFFFL
                        val value = u1 or (u2 shl 16) or (u3 shl 32) or (u4 shl 48)
                        if (numbers == null) numbers = ArrayList(4)
                        numbers.add(value)
                    }
                    0x19 -> { // const-wide/high16
                        val hi = buf.getShort(insnsBase + (pc + 1) * 2).toLong() and 0xFFFFL
                        val value = hi shl 48
                        if (numbers == null) numbers = ArrayList(4)
                        numbers.add(value)
                    }
                    0x1A -> { // const-string
                        val strIdx = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                        if (strIdx < dex.strings.size) {
                            if (strIds == null) strIds = ArrayList(4)
                            strIds.add(strIdx)
                        }
                    }
                    0x1B -> { // const-string/jumbo
                        val lo = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                        val hi = buf.getShort(insnsBase + (pc + 2) * 2).toInt() and 0xFFFF
                        val strIdx = (hi shl 16) or lo
                        if (strIdx in dex.strings.indices) {
                            if (strIds == null) strIds = ArrayList(4)
                            strIds.add(strIdx)
                        }
                    }
                    in 0x52..0x58, in 0x60..0x66 -> { // iget*, sget*
                        val fieldIdx = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                        if (fieldIdx < dex.fieldDescriptors.size) {
                            if (readFields == null) readFields = ArrayList(4)
                            readFields.add(fieldIdx)
                        }
                    }
                    in 0x59..0x5F, in 0x67..0x6D -> { // iput*, sput*
                        val fieldIdx = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                        if (fieldIdx < dex.fieldDescriptors.size) {
                            if (writeFields == null) writeFields = ArrayList(4)
                            writeFields.add(fieldIdx)
                        }
                    }
                    in 0x6E..0x72, in 0x74..0x78, 0xFA, 0xFB -> { // invoke-*
                        val mIdx = buf.getShort(insnsBase + (pc + 1) * 2).toInt() and 0xFFFF
                        if (mIdx < dex.methodDescriptors.size) {
                            if (invokes == null) invokes = ArrayList(4)
                            invokes.add(mIdx)
                        }
                    }
                }

                pc += width
            }

            return ParsedMethod(
                dex = dex,
                methodIdx = methodIdx,
                modifiers = modifiers,
                usingStringIds = strIds?.toIntArray() ?: EMPTY_INT_ARRAY,
                usingNumbers = numbers?.toLongArray() ?: EMPTY_LONG_ARRAY,
                readFieldIds = readFields?.toIntArray() ?: EMPTY_INT_ARRAY,
                writeFieldIds = writeFields?.toIntArray() ?: EMPTY_INT_ARRAY,
                invokeMethodIds = invokes?.toIntArray() ?: EMPTY_INT_ARRAY,
                opCodes = if (opCount == 0) EMPTY_INT_ARRAY else ops.copyOf(opCount)
            )
        }

        private fun parseEncodedArrayConstants(
            cursor: Cursor,
            stringsOut: MutableList<Int>,
            numbersOut: MutableList<Long>
        ) {
            val size = cursor.readUleb128()
            for (i in 0 until size) {
                parseEncodedValueConstants(cursor, stringsOut, numbersOut)
            }
        }

        private fun parseEncodedValueConstants(
            cursor: Cursor,
            stringsOut: MutableList<Int>,
            numbersOut: MutableList<Long>
        ) {
            val header = cursor.readUByte()
            val valueType = header and 0x1F
            val valueArg = (header ushr 5) and 0x07
            when (valueType) {
                0x00 -> { // BYTE
                    numbersOut.add(cursor.readByte().toLong())
                }
                0x02 -> { // SHORT
                    numbersOut.add(cursor.readSignedLittle(valueArg + 1))
                }
                0x03 -> { // CHAR
                    numbersOut.add(cursor.readUnsignedLittle(valueArg + 1))
                }
                0x04 -> { // INT
                    numbersOut.add(cursor.readSignedLittle(valueArg + 1))
                }
                0x06 -> { // LONG
                    numbersOut.add(cursor.readSignedLittle(valueArg + 1))
                }
                0x10 -> { // FLOAT (zero-extended to the right to 4 bytes)
                    val bits = cursor.readZeroExtendedRightFloatBits(valueArg + 1)
                    numbersOut.add(bits.toLong())
                }
                0x11 -> { // DOUBLE (zero-extended to the right to 8 bytes)
                    val bits = cursor.readZeroExtendedRightDoubleBits(valueArg + 1)
                    numbersOut.add(bits)
                }
                0x17 -> { // STRING
                    val strIdx = cursor.readUnsignedLittle(valueArg + 1).toInt()
                    stringsOut.add(strIdx)
                }
                0x18, 0x19, 0x1A, 0x1B, 0x15, 0x16 -> { // TYPE, FIELD, METHOD, ENUM, METHOD_TYPE, METHOD_HANDLE
                    cursor.skip(valueArg + 1)
                }
                0x1C -> { // ARRAY
                    parseEncodedArrayConstants(cursor, stringsOut, numbersOut)
                }
                0x1D -> { // ANNOTATION
                    cursor.readUleb128() // type_idx
                    val count = cursor.readUleb128()
                    for (k in 0 until count) {
                        cursor.readUleb128() // name_idx
                        parseEncodedValueConstants(cursor, stringsOut, numbersOut)
                    }
                }
                0x1E, 0x1F -> Unit // NULL, BOOLEAN (stored in valueArg)
                else -> cursor.skip(valueArg + 1)
            }
        }

        private fun readMutf8String(bytes: ByteArray, offset: Int): String {
            var pos = offset
            // Skip ULEB128 utf16_size
            while (pos < bytes.size) {
                val b = bytes[pos++].toInt() and 0xFF
                if ((b and 0x80) == 0) break
            }
            val start = pos
            var asciiOnly = true
            while (pos < bytes.size) {
                val b = bytes[pos].toInt() and 0xFF
                if (b == 0) break
                if ((b and 0x80) != 0) asciiOnly = false
                pos++
            }
            if (asciiOnly) {
                return String(bytes, start, pos - start, Charsets.US_ASCII)
            }
            val sb = StringBuilder(pos - start)
            var i = start
            while (i < pos) {
                val a = bytes[i++].toInt() and 0xFF
                when {
                    a < 0x80 -> sb.append(a.toChar())
                    (a and 0xE0) == 0xC0 -> {
                        val b = if (i < pos) bytes[i++].toInt() and 0xFF else 0
                        sb.append((((a and 0x1F) shl 6) or (b and 0x3F)).toChar())
                    }
                    (a and 0xF0) == 0xE0 -> {
                        val b = if (i < pos) bytes[i++].toInt() and 0xFF else 0
                        val c = if (i < pos) bytes[i++].toInt() and 0xFF else 0
                        sb.append((((a and 0x0F) shl 12) or ((b and 0x3F) shl 6) or (c and 0x3F)).toChar())
                    }
                    else -> sb.append('\uFFFD')
                }
            }
            return sb.toString()
        }

        // Standard Dalvik opcode widths in 16-bit code units for opcodes 0x00..0xFF.
        private val DALVIK_OPCODE_WIDTHS = intArrayOf(
            // 0x00..0x0F
            1, 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 1, 1, 1, 1, 1,
            // 0x10..0x1F
            1, 1, 1, 2, 3, 2, 2, 3, 5, 2, 2, 3, 2, 1, 1, 2,
            // 0x20..0x2F
            2, 1, 2, 2, 3, 3, 3, 1, 1, 2, 3, 3, 3, 2, 2, 2,
            // 0x30..0x3F
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1,
            // 0x40..0x4F
            1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0x50..0x5F
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0x60..0x6F
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3,
            // 0x70..0x7F
            3, 3, 3, 1, 3, 3, 3, 3, 3, 1, 1, 1, 1, 1, 1, 1,
            // 0x80..0x8F
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            // 0x90..0x9F
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0xA0..0xAF
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0xB0..0xBF
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            // 0xC0..0xCF
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            // 0xD0..0xDF
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            // 0xE0..0xEF
            2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            // 0xF0..0xFF
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 4, 4, 3, 3, 2, 2
        )
    }

    private class Cursor(private val bytes: ByteArray, var pos: Int = 0) {
        fun readUByte(): Int = bytes[pos++].toInt() and 0xFF
        fun readByte(): Byte = bytes[pos++]
        fun skip(count: Int) {
            pos += count
        }

        fun readUleb128(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = bytes[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
            }
        }

        fun readUnsignedLittle(numBytes: Int): Long {
            var res = 0L
            for (i in 0 until numBytes) {
                res = res or ((bytes[pos++].toLong() and 0xFFL) shl (i * 8))
            }
            return res
        }

        fun readSignedLittle(numBytes: Int): Long {
            var res = 0L
            for (i in 0 until numBytes) {
                res = res or ((bytes[pos++].toLong() and 0xFFL) shl (i * 8))
            }
            val shift = (8 - numBytes) * 8
            return (res shl shift) shr shift
        }

        fun readZeroExtendedRightFloatBits(numBytes: Int): Int {
            var res = 0
            for (i in 0 until numBytes) {
                res = res or ((bytes[pos++].toInt() and 0xFF) shl ((4 - numBytes + i) * 8))
            }
            return res
        }

        fun readZeroExtendedRightDoubleBits(numBytes: Int): Long {
            var res = 0L
            for (i in 0 until numBytes) {
                res = res or ((bytes[pos++].toLong() and 0xFFL) shl ((8 - numBytes + i) * 8))
            }
            return res
        }
    }
}

object DexTypeNames {
    fun descriptorToClassName(descriptor: String): String {
        return when (descriptor) {
            "V" -> "void"
            "Z" -> "boolean"
            "B" -> "byte"
            "S" -> "short"
            "C" -> "char"
            "I" -> "int"
            "J" -> "long"
            "F" -> "float"
            "D" -> "double"
            else -> when {
                descriptor.startsWith("[") -> {
                    val dims = descriptor.indexOfFirst { it != '[' }
                    val base = descriptorToClassName(descriptor.substring(dims))
                    base + "[]".repeat(dims)
                }
                descriptor.startsWith("L") && descriptor.endsWith(";") ->
                    descriptor.substring(1, descriptor.length - 1).replace('/', '.')
                else -> descriptor.replace('/', '.')
            }
        }
    }

    fun descriptorToJvmInternalName(descriptor: String): String {
        return when {
            descriptor.startsWith("L") && descriptor.endsWith(";") ->
                descriptor.substring(1, descriptor.length - 1)
            descriptor.startsWith("[") -> descriptor
            else -> descriptor.replace('.', '/')
        }
    }
}

// =============================================================================
// Dynamic JVM ClassLoader backed by APK DEX definitions
// =============================================================================

class DexBackedClassLoader(
    private val database: ApkDexDatabase,
    parent: ClassLoader?
) : ClassLoader(parent) {

    private val definedClasses = ConcurrentHashMap<String, Class<*>>()

    override fun findClass(name: String): Class<*> {
        definedClasses[name]?.let { return it }

        val classDef = database.classesByName[name]
        val bytes = when {
            classDef != null -> buildClassBytes(classDef)
            database.referencedTypes.contains(name) -> {
                buildStubClassBytes(
                    className = name,
                    isInterface = database.referencedInterfaces.contains(name)
                )
            }
            else -> throw ClassNotFoundException(name)
        }

        val defined = synchronized(this) {
            definedClasses[name]?.let { return it }
            defineClass(name, bytes, 0, bytes.size).also {
                definedClasses[name] = it
            }
        }
        return defined
    }

    private fun buildClassBytes(classDef: ParsedClassDef): ByteArray {
        val cp = ConstantPoolBuilder()
        val isInterface = (classDef.modifiers and Modifier.INTERFACE) != 0
        val jvmClassFlags = if (isInterface) {
            (classDef.modifiers and 0x7611) or Modifier.INTERFACE or Modifier.ABSTRACT
        } else {
            (classDef.modifiers and 0x7611) or 0x0020 // ACC_SUPER
        }

        val thisClassIdx = cp.addClass(DexTypeNames.descriptorToJvmInternalName(classDef.descriptor))
        val superInternal = if (isInterface || classDef.superClassDescriptor == null) {
            "java/lang/Object"
        } else {
            DexTypeNames.descriptorToJvmInternalName(classDef.superClassDescriptor)
        }
        val superClassIdx = cp.addClass(superInternal)
        val ifaceIndices = classDef.interfaceDescriptors.distinct().map {
            cp.addClass(DexTypeNames.descriptorToJvmInternalName(it))
        }

        val seenFields = HashSet<String>()
        val fieldEntries = mutableListOf<Triple<Int, Int, Int>>()
        for (field in classDef.fields) {
            val key = "${field.name}:${field.typeDescriptor}"
            if (!seenFields.add(key)) continue
            val jvmFlags = field.modifiers and 0x50DF
            fieldEntries.add(Triple(jvmFlags, cp.addUtf8(field.name), cp.addUtf8(field.typeDescriptor)))
        }

        val codeAttrNameIdx = cp.addUtf8("Code")
        val seenMethods = HashSet<String>()
        val methodEntries = mutableListOf<MethodEntry>()
        for (method in classDef.methods) {
            val methodDesc = buildMethodJvmDescriptor(method)
            val key = "${method.name}:$methodDesc"
            if (!seenMethods.add(key)) continue
            val rawFlags = method.modifiers
            val normalizedFlags = (rawFlags and 0x1DFF) or
                (if ((rawFlags and 0x20000) != 0) Modifier.SYNCHRONIZED else 0)
            val needsCode = (normalizedFlags and (Modifier.ABSTRACT or Modifier.NATIVE)) == 0
            val paramSlots = countParamSlots(method.paramTypeDescriptors) +
                (if ((normalizedFlags and Modifier.STATIC) != 0) 0 else 1)
            methodEntries.add(
                MethodEntry(
                    accessFlags = normalizedFlags,
                    nameIdx = cp.addUtf8(method.name),
                    descIdx = cp.addUtf8(methodDesc),
                    needsCode = needsCode,
                    maxLocals = maxOf(1, paramSlots)
                )
            )
        }

        val outBytes = ByteArrayOutputStream()
        val out = DataOutputStream(outBytes)
        out.writeInt(0xCAFEBABE.toInt())
        out.writeShort(0) // minor
        out.writeShort(49) // Java 5: no StackMapTable required for stub Code attributes
        cp.writeTo(out)
        out.writeShort(jvmClassFlags)
        out.writeShort(thisClassIdx)
        out.writeShort(superClassIdx)
        out.writeShort(ifaceIndices.size)
        ifaceIndices.forEach { out.writeShort(it) }
        out.writeShort(fieldEntries.size)
        for ((flags, nameIdx, descIdx) in fieldEntries) {
            out.writeShort(flags)
            out.writeShort(nameIdx)
            out.writeShort(descIdx)
            out.writeShort(0) // attributes
        }
        out.writeShort(methodEntries.size)
        for (m in methodEntries) {
            out.writeShort(m.accessFlags)
            out.writeShort(m.nameIdx)
            out.writeShort(m.descIdx)
            if (m.needsCode) {
                out.writeShort(1) // 1 attribute: Code
                out.writeShort(codeAttrNameIdx)
                out.writeInt(14) // attribute_length = 2+2+4+2+2+2 = 14
                out.writeShort(1) // max_stack
                out.writeShort(m.maxLocals) // max_locals
                out.writeInt(2) // code_length
                out.writeByte(0x01) // aconst_null
                out.writeByte(0xBF) // athrow
                out.writeShort(0) // exception_table_length
                out.writeShort(0) // code attributes_count
            } else {
                out.writeShort(0)
            }
        }
        out.writeShort(0) // class attributes
        return outBytes.toByteArray()
    }

    private fun buildStubClassBytes(className: String, isInterface: Boolean): ByteArray {
        val cp = ConstantPoolBuilder()
        val internal = className.replace('.', '/')
        val thisClassIdx = cp.addClass(internal)
        val superClassIdx = cp.addClass("java/lang/Object")
        val initNameIdx = if (!isInterface) cp.addUtf8("<init>") else 0
        val initDescIdx = if (!isInterface) cp.addUtf8("()V") else 0
        val codeAttrIdx = if (!isInterface) cp.addUtf8("Code") else 0
        val objInitRefIdx = if (!isInterface) cp.addMethodRef(superClassIdx, initNameIdx, initDescIdx) else 0

        val outBytes = ByteArrayOutputStream()
        val out = DataOutputStream(outBytes)
        out.writeInt(0xCAFEBABE.toInt())
        out.writeShort(0)
        out.writeShort(49)
        cp.writeTo(out)
        out.writeShort(
            if (isInterface) {
                Modifier.PUBLIC or Modifier.INTERFACE or Modifier.ABSTRACT
            } else {
                Modifier.PUBLIC or 0x0020
            }
        )
        out.writeShort(thisClassIdx)
        out.writeShort(superClassIdx)
        out.writeShort(0) // interfaces
        out.writeShort(0) // fields
        if (isInterface) {
            out.writeShort(0)
        } else {
            out.writeShort(1)
            out.writeShort(Modifier.PUBLIC)
            out.writeShort(initNameIdx)
            out.writeShort(initDescIdx)
            out.writeShort(1)
            out.writeShort(codeAttrIdx)
            out.writeInt(17) // 2+2+4+5+2+2
            out.writeShort(1)
            out.writeShort(1)
            out.writeInt(5)
            out.writeByte(0x2A) // aload_0
            out.writeByte(0xB7) // invokespecial
            out.writeShort(objInitRefIdx)
            out.writeByte(0xB1) // return
            out.writeShort(0)
            out.writeShort(0)
        }
        out.writeShort(0)
        return outBytes.toByteArray()
    }

    private fun buildMethodJvmDescriptor(method: ParsedMethod): String = buildString {
        append('(')
        for (p in method.paramTypeDescriptors) {
            append(p)
        }
        append(')')
        append(method.returnTypeDescriptor)
    }

    private fun countParamSlots(paramDescriptors: List<String>): Int {
        var slots = 0
        for (p in paramDescriptors) {
            slots += if (p == "J" || p == "D") 2 else 1
        }
        return slots
    }

    private data class MethodEntry(
        val accessFlags: Int,
        val nameIdx: Int,
        val descIdx: Int,
        val needsCode: Boolean,
        val maxLocals: Int
    )

    private class ConstantPoolBuilder {
        private val entries = mutableListOf<ByteArray>()
        private val utf8Cache = HashMap<String, Int>()
        private val classCache = HashMap<String, Int>()

        fun addUtf8(value: String): Int {
            utf8Cache[value]?.let { return it }
            val b = ByteArrayOutputStream()
            val out = DataOutputStream(b)
            out.writeByte(1) // CONSTANT_Utf8
            out.writeUTF(value)
            entries.add(b.toByteArray())
            val idx = entries.size
            utf8Cache[value] = idx
            return idx
        }

        fun addClass(internalName: String): Int {
            classCache[internalName]?.let { return it }
            val utfIdx = addUtf8(internalName)
            val b = ByteArrayOutputStream()
            val out = DataOutputStream(b)
            out.writeByte(7) // CONSTANT_Class
            out.writeShort(utfIdx)
            entries.add(b.toByteArray())
            val idx = entries.size
            classCache[internalName] = idx
            return idx
        }

        fun addNameAndType(nameIdx: Int, descIdx: Int): Int {
            val b = ByteArrayOutputStream()
            val out = DataOutputStream(b)
            out.writeByte(12) // CONSTANT_NameAndType
            out.writeShort(nameIdx)
            out.writeShort(descIdx)
            entries.add(b.toByteArray())
            return entries.size
        }

        fun addMethodRef(classIdx: Int, nameIdx: Int, descIdx: Int): Int {
            val ntIdx = addNameAndType(nameIdx, descIdx)
            val b = ByteArrayOutputStream()
            val out = DataOutputStream(b)
            out.writeByte(10) // CONSTANT_Methodref
            out.writeShort(classIdx)
            out.writeShort(ntIdx)
            entries.add(b.toByteArray())
            return entries.size
        }

        fun writeTo(out: DataOutputStream) {
            out.writeShort(entries.size + 1)
            for (entry in entries) {
                out.write(entry)
            }
        }
    }
}

// =============================================================================
// DexKit 2.0.3 Compatible Query Evaluator over ApkDexDatabase
// =============================================================================

class ApkBytecodeDexKitExecutor(
    private val database: ApkDexDatabase
) : DexKitExecutor {

    override fun findClass(query: FindClass): List<DexDescriptorResult> {
        val searchPackages = query.searchPackages.orEmpty().map { it.trimEnd('.') }.filter { it.isNotEmpty() }
        val excludePackages = query.excludePackages.orEmpty().map { it.trimEnd('.') }.filter { it.isNotEmpty() }
        val ignorePkgCase = query.ignorePackagesCase
        val searchClassDescs = query.searchClasses?.map { it.descriptor }?.toHashSet()
        val matcher = query.matcher
        val findFirst = query.findFirst

        val results = mutableListOf<DexDescriptorResult>()
        for (cls in database.classes) {
            if (searchClassDescs != null && cls.descriptor !in searchClassDescs) continue
            if (!matchesPackageFilter(cls.className, searchPackages, excludePackages, ignorePkgCase)) continue
            if (matcher == null || matchesClass(cls, matcher)) {
                results.add(
                    DexDescriptorResult(
                        descriptor = cls.descriptor,
                        className = cls.className,
                        name = cls.className,
                        declaredClassName = cls.className
                    )
                )
                if (findFirst) break
            }
        }
        return results
    }

    override fun findMethod(query: FindMethod): List<DexDescriptorResult> {
        val searchPackages = query.searchPackages.orEmpty().map { it.trimEnd('.') }.filter { it.isNotEmpty() }
        val excludePackages = query.excludePackages.orEmpty().map { it.trimEnd('.') }.filter { it.isNotEmpty() }
        val ignorePkgCase = query.ignorePackagesCase
        val searchClassDescs = query.searchClasses?.map { it.descriptor }?.toHashSet()
        val searchMethodDescs = query.searchMethods?.map { it.descriptor }?.toHashSet()
        val matcher = query.matcher
        val findFirst = query.findFirst

        val results = mutableListOf<DexDescriptorResult>()
        for (method in database.allMethods) {
            if (searchMethodDescs != null && method.descriptor !in searchMethodDescs) continue
            if (searchClassDescs != null && method.declaredClassDescriptor !in searchClassDescs) continue
            if (!matchesPackageFilter(method.declaredClassName, searchPackages, excludePackages, ignorePkgCase)) continue
            if (matcher == null || matchesMethod(method, matcher)) {
                results.add(
                    DexDescriptorResult(
                        descriptor = method.descriptor,
                        className = method.declaredClassName,
                        name = method.name,
                        declaredClassName = method.declaredClassName,
                        usingFieldsProvider = { buildUsingFields(method) }
                    )
                )
                if (findFirst) break
            }
        }
        return results
    }

    private fun buildUsingFields(method: ParsedMethod): List<DexUsingField> {
        val allIds = method.readFieldIds.asSequence() + method.writeFieldIds.asSequence()
        val seen = HashSet<String>()
        val out = mutableListOf<DexUsingField>()
        for (fid in allIds) {
            val desc = method.dex.fieldDescriptors[fid]
            if (!seen.add(desc)) continue
            val parsed = database.fieldsByDescriptor[desc]
            val typeDesc = parsed?.typeDescriptor ?: method.dex.fieldTypeDescriptor(fid)
            out.add(
                DexUsingField(
                    DexFieldDescriptor(
                        descriptor = desc,
                        modifiers = parsed?.modifiers ?: 0,
                        type = DexTypeDescriptor(
                            name = DexTypeNames.descriptorToClassName(typeDesc),
                            descriptor = typeDesc
                        ),
                        name = parsed?.name ?: method.dex.fieldName(fid),
                        declaredClassName = parsed?.declaredClassName
                            ?: DexTypeNames.descriptorToClassName(method.dex.fieldClassDescriptor(fid))
                    )
                )
            )
        }
        return out
    }

    override fun findField(query: FindField): List<DexDescriptorResult> {
        val searchPackages = query.searchPackages.orEmpty().map { it.trimEnd('.') }.filter { it.isNotEmpty() }
        val excludePackages = query.excludePackages.orEmpty().map { it.trimEnd('.') }.filter { it.isNotEmpty() }
        val ignorePkgCase = query.ignorePackagesCase
        val searchClassDescs = query.searchClasses?.map { it.descriptor }?.toHashSet()
        val searchFieldDescs = query.searchFields?.map { it.descriptor }?.toHashSet()
        val matcher = query.matcher
        val findFirst = query.findFirst

        val results = mutableListOf<DexDescriptorResult>()
        for (field in database.allFields) {
            if (searchFieldDescs != null && field.descriptor !in searchFieldDescs) continue
            if (searchClassDescs != null && field.declaredClassDescriptor !in searchClassDescs) continue
            if (!matchesPackageFilter(field.declaredClassName, searchPackages, excludePackages, ignorePkgCase)) continue
            if (matcher == null || matchesField(field, matcher)) {
                results.add(
                    DexDescriptorResult(
                        descriptor = field.descriptor,
                        className = field.declaredClassName,
                        name = field.name,
                        declaredClassName = field.declaredClassName
                    )
                )
                if (findFirst) break
            }
        }
        return results
    }

    private fun matchesPackageFilter(
        className: String,
        searchPackages: List<String>,
        excludePackages: List<String>,
        ignoreCase: Boolean
    ): Boolean {
        val pkg = className.substringBeforeLast('.', "")
        if (searchPackages.isNotEmpty()) {
            val matched = searchPackages.any { p ->
                pkg.equals(p, ignoreCase) || pkg.startsWith("$p.", ignoreCase) || className.startsWith("$p.", ignoreCase)
            }
            if (!matched) return false
        }
        if (excludePackages.isNotEmpty()) {
            val excluded = excludePackages.any { p ->
                pkg.equals(p, ignoreCase) || pkg.startsWith("$p.", ignoreCase) || className.startsWith("$p.", ignoreCase)
            }
            if (excluded) return false
        }
        return true
    }

    private fun matchesClass(cls: ParsedClassDef, matcher: ClassMatcher): Boolean {
        matcher.sourceMatcher?.let { sm ->
            val src = cls.sourceFile ?: return false
            if (!matchesString(src, sm)) return false
        }
        matcher.classNameMatcher?.let { cm ->
            if (!matchesTypeNameOrDescriptor(cls.descriptor, cls.className, cm)) return false
        }
        matcher.modifiersMatcher?.let { mm ->
            if (!matchesModifiers(cls.modifiers, mm)) return false
        }
        matcher.superClassMatcher?.let { superM ->
            val superDesc = cls.superClassDescriptor ?: return false
            val superCls = database.classesByDescriptor[superDesc]
            if (superCls != null) {
                if (!matchesClass(superCls, superM)) return false
            } else {
                if (!matchesExternalTypeClassMatcher(superDesc, superM)) return false
            }
        }
        matcher.interfacesMatcher?.let { im ->
            if (!matchesInterfaces(cls.interfaceDescriptors, im)) return false
        }
        matcher.fieldsMatcher?.let { fm ->
            if (!matchesFieldsGroup(cls.fields, fm)) return false
        }
        matcher.methodsMatcher?.let { mm ->
            if (!matchesMethodsGroup(cls.methods, mm)) return false
        }
        matcher.usingStringsMatcher?.let { strMatchers ->
            if (strMatchers.isNotEmpty()) {
                val strings = cls.allUsingStrings
                for (sm in strMatchers) {
                    if (strings.none { matchesString(it, sm) }) return false
                }
            }
        }
        return true
    }

    private fun matchesExternalTypeClassMatcher(typeDesc: String, matcher: ClassMatcher): Boolean {
        if (matcher.sourceMatcher != null ||
            matcher.modifiersMatcher != null ||
            matcher.superClassMatcher != null ||
            matcher.interfacesMatcher != null ||
            matcher.fieldsMatcher != null ||
            matcher.methodsMatcher != null ||
            !matcher.usingStringsMatcher.isNullOrEmpty()
        ) {
            return false
        }
        val nameMatcher = matcher.classNameMatcher ?: return true
        val typeName = DexTypeNames.descriptorToClassName(typeDesc)
        return matchesTypeNameOrDescriptor(typeDesc, typeName, nameMatcher)
    }

    private fun matchesTypeRef(typeDesc: String, matcher: ClassMatcher): Boolean {
        val cls = database.classesByDescriptor[typeDesc]
        return if (cls != null) {
            matchesClass(cls, matcher)
        } else {
            matchesExternalTypeClassMatcher(typeDesc, matcher)
        }
    }

    private fun matchesInterfaces(interfaceDescriptors: List<String>, matcher: InterfacesMatcher): Boolean {
        matcher.rangeMatcher?.let { range ->
            if (!matchesIntRange(interfaceDescriptors.size, range)) return false
        }
        val items = matcher.interfacesMatcher.orEmpty()
        if (items.isEmpty()) return true
        return when (matcher.matchType) {
            MatchType.Contains -> items.all { im -> interfaceDescriptors.any { matchesTypeRef(it, im) } }
            MatchType.Equals -> {
                interfaceDescriptors.size == items.size &&
                    items.all { im -> interfaceDescriptors.any { matchesTypeRef(it, im) } }
            }
        }
    }

    private fun matchesFieldsGroup(fields: List<ParsedField>, matcher: FieldsMatcher): Boolean {
        matcher.rangeMatcher?.let { range ->
            if (!matchesIntRange(fields.size, range)) return false
        }
        val items = matcher.fieldsMatcher.orEmpty()
        if (items.isEmpty()) return true
        return when (matcher.matchType) {
            MatchType.Contains -> items.all { fm -> fields.any { matchesField(it, fm) } }
            MatchType.Equals -> {
                fields.size == items.size &&
                    items.all { fm -> fields.any { matchesField(it, fm) } }
            }
        }
    }

    private fun matchesMethodsGroup(methods: List<ParsedMethod>, matcher: MethodsMatcher): Boolean {
        matcher.rangeMatcher?.let { range ->
            if (!matchesIntRange(methods.size, range)) return false
        }
        val items = matcher.methodsMatcher.orEmpty()
        if (items.isEmpty()) return true
        return when (matcher.matchType) {
            MatchType.Contains -> items.all { mm -> methods.any { matchesMethod(it, mm) } }
            MatchType.Equals -> {
                methods.size == items.size &&
                    items.all { mm -> methods.any { matchesMethod(it, mm) } }
            }
        }
    }

    private fun matchesMethod(method: ParsedMethod, matcher: MethodMatcher): Boolean {
        matcher.nameMatcher?.let { nm ->
            if (!matchesString(method.name, nm)) return false
        }
        matcher.modifiersMatcher?.let { mm ->
            if (!matchesModifiers(method.modifiers, mm)) return false
        }
        matcher.classMatcher?.let { cm ->
            if (!matchesTypeRef(method.declaredClassDescriptor, cm)) return false
        }
        matcher.returnTypeMatcher?.let { rtm ->
            if (!matchesTypeRef(method.returnTypeDescriptor, rtm)) return false
        }
        matcher.paramsMatcher?.let { pm ->
            if (!matchesParameters(method.paramTypeDescriptors, pm)) return false
        }
        matcher.opCodesMatcher?.let { om ->
            if (!matchesOpCodes(method.opCodes, om)) return false
        }
        matcher.usingStringsMatcher?.let { strMatchers ->
            if (strMatchers.isNotEmpty()) {
                val strings = method.usingStrings
                for (sm in strMatchers) {
                    if (strings.none { matchesString(it, sm) }) return false
                }
            }
        }
        matcher.usingNumbersMatcher?.let { numMatchers ->
            if (numMatchers.isNotEmpty()) {
                for (nm in numMatchers) {
                    if (!matchesNumberInArray(method.usingNumbers, nm)) return false
                }
            }
        }
        matcher.usingFieldsMatcher?.let { ufMatchers ->
            for (ufm in ufMatchers) {
                if (!matchesUsingField(method, ufm)) return false
            }
        }
        matcher.invokeMethodsMatcher?.let { imm ->
            val invoked = method.invokeMethodIds.asSequence().mapNotNull { mid: Int ->
                database.methodsByDescriptor[method.dex.methodDescriptors[mid]]
            }.toList()
            if (!matchesMethodsGroup(invoked, imm)) return false
        }
        matcher.callerMethodsMatcher?.let { cmm ->
            val callers = database.callersByMethodDescriptor[method.descriptor].orEmpty()
            if (!matchesMethodsGroup(callers, cmm)) return false
        }
        return true
    }

    private fun matchesField(field: ParsedField, matcher: FieldMatcher): Boolean {
        matcher.nameMatcher?.let { nm ->
            if (!matchesString(field.name, nm)) return false
        }
        matcher.modifiersMatcher?.let { mm ->
            if (!matchesModifiers(field.modifiers, mm)) return false
        }
        matcher.classMatcher?.let { cm ->
            if (!matchesTypeRef(field.declaredClassDescriptor, cm)) return false
        }
        matcher.typeMatcher?.let { tm ->
            if (!matchesTypeRef(field.typeDescriptor, tm)) return false
        }
        matcher.getMethodsMatcher?.let { gmm ->
            val readers = database.readersByFieldDescriptor[field.descriptor].orEmpty()
            if (!matchesMethodsGroup(readers, gmm)) return false
        }
        matcher.putMethodsMatcher?.let { pmm ->
            val writers = database.writersByFieldDescriptor[field.descriptor].orEmpty()
            if (!matchesMethodsGroup(writers, pmm)) return false
        }
        return true
    }

    private fun matchesUsingField(method: ParsedMethod, matcher: UsingFieldMatcher): Boolean {
        val fieldMatcher = matcher.matcher ?: return true
        val usingType = matcher.usingType
        val candidateIds = when (usingType) {
            UsingType.Read -> method.readFieldIds.asSequence()
            UsingType.Write -> method.writeFieldIds.asSequence()
            UsingType.Any -> method.readFieldIds.asSequence() + method.writeFieldIds.asSequence()
        }
        return candidateIds.any { fid ->
            val fieldDesc = method.dex.fieldDescriptors[fid]
            val parsedField = database.fieldsByDescriptor[fieldDesc]
                ?: ParsedField(method.dex, fid, 0)
            matchesField(parsedField, fieldMatcher)
        }
    }

    private fun matchesParameters(paramDescriptors: List<String>, matcher: ParametersMatcher): Boolean {
        matcher.rangeMatcher?.let { range ->
            if (!matchesIntRange(paramDescriptors.size, range)) return false
        }
        val paramMatchers = matcher.paramsMatcher
        if (paramMatchers != null) {
            if (paramMatchers.size != paramDescriptors.size) return false
            for (i in paramMatchers.indices) {
                val typeM = paramMatchers[i]?.typeMatcher ?: continue
                if (!matchesTypeRef(paramDescriptors[i], typeM)) return false
            }
        }
        return true
    }

    private fun matchesOpCodes(opCodes: IntArray, matcher: OpCodesMatcher): Boolean {
        val expected = matcher.opCodes
        if (expected.isNullOrEmpty()) return true
        matcher.rangeMatcher?.let { range ->
            if (!matchesIntRange(opCodes.size, range)) return false
        }
        val expectedInts = expected.toIntArray()
        return when (matcher.matchType) {
            org.luckypray.dexkit.query.enums.OpCodeMatchType.Equals ->
                opCodes.contentEquals(expectedInts)
            org.luckypray.dexkit.query.enums.OpCodeMatchType.StartsWith ->
                opCodes.size >= expectedInts.size && expectedInts.indices.all { opCodes[it] == expectedInts[it] }
            org.luckypray.dexkit.query.enums.OpCodeMatchType.EndsWith ->
                opCodes.size >= expectedInts.size &&
                    expectedInts.indices.all { opCodes[opCodes.size - expectedInts.size + it] == expectedInts[it] }
            org.luckypray.dexkit.query.enums.OpCodeMatchType.Contains ->
                containsSubarray(opCodes, expectedInts)
        }
    }

    private fun containsSubarray(haystack: IntArray, needle: IntArray): Boolean {
        if (needle.isEmpty()) return true
        if (haystack.size < needle.size) return false
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun matchesNumberInArray(numbers: LongArray, matcher: NumberEncodeValueMatcher): Boolean {
        val encodeVal = matcher.value ?: return true
        val targetLong: Long = when (encodeVal) {
            is EncodeValueByte -> encodeVal.value.toLong()
            is EncodeValueShort -> encodeVal.value.toLong()
            is EncodeValueInt -> encodeVal.value.toLong()
            is EncodeValueLong -> encodeVal.value
            is EncodeValueFloat -> java.lang.Float.floatToRawIntBits(encodeVal.value).toLong()
            is EncodeValueDouble -> java.lang.Double.doubleToRawLongBits(encodeVal.value)
            else -> return false
        }
        for (n in numbers) {
            if (n == targetLong) return true
        }
        return false
    }

    private fun matchesTypeNameOrDescriptor(
        descriptor: String,
        className: String,
        matcher: StringMatcher
    ): Boolean {
        if (matchesString(className, matcher)) return true
        val pattern = matcher.value
        if (!pattern.isNullOrEmpty() && (pattern.startsWith("L") || pattern.startsWith("["))) {
            if (matchesString(descriptor, matcher)) return true
        }
        return false
    }

    private fun matchesString(target: String, matcher: StringMatcher): Boolean {
        val value = matcher.value ?: return true
        val ignoreCase = matcher.ignoreCase
        return when (matcher.matchType) {
            StringMatchType.Contains -> target.contains(value, ignoreCase = ignoreCase)
            StringMatchType.Equals -> target.equals(value, ignoreCase = ignoreCase)
            StringMatchType.StartsWith -> target.startsWith(value, ignoreCase = ignoreCase)
            StringMatchType.EndsWith -> target.endsWith(value, ignoreCase = ignoreCase)
            StringMatchType.SimilarRegex -> {
                val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                Regex(value, options).containsMatchIn(target)
            }
        }
    }

    private fun matchesModifiers(actual: Int, matcher: AccessFlagsMatcher): Boolean {
        val expected = matcher.modifiers
        return when (matcher.matchType) {
            MatchType.Equals -> actual == expected
            MatchType.Contains -> (actual and expected) == expected
        }
    }

    private fun matchesIntRange(value: Int, range: DexIntRange): Boolean {
        return value in range.min..range.max
    }
}
