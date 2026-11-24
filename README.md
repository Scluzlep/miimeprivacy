# 米输净盾 (MiImePrivacy)

针对小米生态三款主流中文输入法的 **libxposed API 102** 隐私净化、云输入载荷最小化与模式瞬切模块。

## 模块定位与边界

- **输入法隐私净化，而非粗暴断网阉割**：默认保留用户主动开启的云联想候选与正常词库/字典更新能力，在请求发出前剥离与当前拼音/语音识别无关的上下文、设备指纹与宿主隐私字段。
- **不破解任何付费特权或会员状态**：不绕过会员付费墙、不读取或导出任何明文密钥或个人历史语料库。
- **诚实降级与显式诊断**：支持通过 DexKit 动态解析与结构锚点自适应非基准版本；若任意关键 Hook 未匹配，界面会以醒目警告提示并在诊断列表如实呈现 `FAILED` / `DEGRADED` / `FALLBACK`，绝不虚报成功。

## 支持输入法与审计基准版本

| 输入法 | 包名 | 审计基准 `versionCode` | 默认状态 |
| :--- | :--- | :--- | :--- |
| **搜狗输入法小米版** | `com.sohu.inputmethod.sogou.xiaomi` | `20160` | 🟢 云输入（载荷脱敏） |
| **百度输入法小米版** | `com.baidu.input_mi` | `7108` | 🟢 云输入（载荷脱敏） |
| **超级小爱输入法** | `com.xiaomi.type` | `21363` | 🟢 云输入（载荷脱敏） |

## 功能矩阵

| 类别 | 默认状态 | 说明 |
| :--- | :--- | :--- |
| **全局与分应用云输入控制** | 开启（附带脱敏） | 支持三家全局一键切纯本地，或按单款输入法设为「跟随全局 / 强制开启 / 强制本地」；关闭云输入不影响字典更新 |
| **遥测与日志隔离** | 开启 | 拦截搜狗 `kpi2` / `instantpb` / `MutualData` / `TDDiag` / `Alive` 绕过；拦截百度 `sj0.a case 14` 周期上报、明文回传与 `mobile_log_salvage`；修复超级小爱 CTA Fail-Open、`OneTrack` 绕过与密码框先计数缺陷 |
| **云输入载荷最小化** | 开启 | 剥离聊天上下文 `dialogue`、光标前后 100 字上下文、宿主包名、触控坐标、地理/城市位置及 `IMEI`/`IMSI` 设备标识 |
| **剪贴板与权限隔离** | 开启 | 保留正常手动粘贴，阻断隐私模式反向落盘、后台静默读取、`cpn` 计数上报及通讯录/短信/全量扫包 |
| **未签名动态 DEX / 脚本拦截** | 开启 | 拦截搜狗 `hj` 仅 MD5 校验的远端任务 DEX 与百度远端插件/QuickJS 动态执行 |
| **严格可选开关** | **默认关闭** | 阻断搜狗/百度 HTTPDNS 直连旁路（`MSDKDnsResolver` / `httpsdns.baidu.com`）；阻断超级小爱热词纠错跨应用文本追踪（`HotwordRemoteClient`） |

## 快捷交互：键盘微胶囊 (`🟢 / 🔒 / 🔴`) 与控制中心磁贴

1. **键盘顶栏角落微胶囊（Option A）**
   - `🟢`：当前输入法受模块接管且允许云输入（已开启字段脱敏）。
   - `🔒`：当前输入法受模块接管且处于纯本地离线模式。
   - `🔴`：当前输入法未接管或存在异常（点击直接打开快捷面板，避免误切换配置）。
   - **单击**（`🟢 ↔ 🔒`）：瞬切当前输入法的云输入/纯本地状态并实时跨进程同步。
   - **长按**：弹出浮动快捷面板，提供全局/单输入法开关以及「快捷进入模块主页 ↗」。
   - **按住拖动**：自由调整悬浮微胶囊在键盘上的偏移位置，按三款输入法独立记忆并支持在 App 内一键重置为 `(12dp, 2dp)`。
2. **HyperOS 控制中心磁贴**
   - 从屏幕顶部下拉控制中心 -> 编辑 -> 添加 **「输入法云输入」**（`CloudToggleTileService`），即可随时一键切换全局云输入与纯本地离线状态。

## Miuix + 莫奈动态取色 UI

应用采用 `top.yukonga.miuix.kmp:miuix-ui-android:0.9.0` 原生组件体系构建 4 个控制中心页面：

- **总览**：`XposedService` 绑定状态（API `102`、`RemotePreferences` 实时同步状态、运行中目标进程列表）、一键申请作用域、热重载运行中模块、全局云输入开关、会话累计拦截计数与三家输入法独立卡片。
- **净化**：分功能组切换全部隐私净化开关，提供可按输入法筛选的逐条 Hook 真实命中诊断视图（`ACTIVE FastPath` / `DEXKIT` / `FALLBACK` / `DEGRADED` / `FAILED` / `SKIPPED` + 真实匹配签名与计数）。
- **挂件**：微胶囊实时预览（可切换预览 `🟢`/`🔒`/`🔴` 与不透明度 25%–100%）、各输入法 X/Y 偏移量重置与控制中心磁贴添加引导。
- **外观与关于**：支持切换主题模式（跟随系统 / 浅色 / 深色）、莫奈动态取色开关（Android 12+）以及 9 种 `MonetPaletteStyle`（`TonalSpot`、`Neutral`、`Vibrant`、`Expressive` 等）并实时预览色板，关闭莫奈时使用 HyperOS `#007DFF` 主题色。

## 本地构建与 Release 签名校验

### 本地测试与 Debug 构建

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

要求：`JDK 21`、`compileSdk 37`（Android 37 SDK / Build-Tools `37.0.0`）、Gradle `9.7.0`。

### CI 发布与 `android-sig` 签名校验

GitHub Actions 工作流（[`.github/workflows/build-release.yml`](.github/workflows/build-release.yml)）在 `check` 阶段校验单元测试、`META-INF/xposed/*` 纯 LF 换行与 API 102 元数据、`libdexkit.so` 未压缩存储（`compress_type == 0`）及清单组件（`LAUNCHER`、`XposedProvider`、`CloudToggleTileService` 且无 `xposedminversion`）；打 `v*` 标签或手动触发发布时自动 `zipalign` 并使用 SigHelper PKCS#12 密钥签名生成 `miimeprivacy-<tag>.apk` 与 `SHA256SUMS.txt`。

校验已发布的 Release APK 签名：

```bash
android-sig veri <owner>/<repo> --apk miimeprivacy-<tag>.apk
```
