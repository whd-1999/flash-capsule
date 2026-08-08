# Flash Capsule（闪念胶囊·复刻+增强）架构设计

> 目标：在 Android 上复刻锤子「闪念胶囊」的**零决策全局捕获**体验，并加上 2026 该有的
> 端上 AI（Whisper 转写 + LLM 自动标题/分类）与**可插拔 I/O 接口**，最终焊进作者现有的
> Obsidian vault / 自动化工作流。
>
> 设计红线：**轻量、省电、简洁**。平时它应该像不存在一样（0 后台常驻、0 耗电），
> 只有用户唤起的那几秒才活。

---

## 0. 产品本质（不能丢的三条）

1. **捕获零决策**：想法还没溜走就已存下，不让用户当场选"存哪/分哪类"。分类、处理留到以后。
2. **入口无处不在**：任意界面一个手势即可捕获（语音优先，文字次之）。
3. **捕获与处理分离**：先无脑存进 Inbox，之后再批量整理/导出（后期可玩成"信息处理中心"）。

> 复刻优先级：**捕获体验 > 一切**。UI 花哨、功能多都不重要，捕获慢一秒这产品就废了。

---

## 1. 技术栈

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 | Kotlin | 作者主栈（clean-douyin / BiliPai 同栈） |
| UI | Jetpack Compose + Material 3 | 声明式、轻 |
| 端上转写 | whisper.cpp（JNI，tiny/base int8，NNAPI/GPU） | 离线、免云、私密 |
| 存储 | Room（元数据）+ 文件系统（音频/附件） | 轻量、无服务端 |
| 异步 | Coroutines + Flow | — |
| 后台任务 | WorkManager | 省电、Doze 友好、可加约束 |
| DI | Koin | 比 Hilt 轻，够用 |
| 序列化 | kotlinx.serialization | 导出 JSON / 接口 |

> 跨平台可选：若想复用 iOS，可走 **Compose Multiplatform + SQLDelight**（NotelyVoice 的路子）。
> 但第一版**只做 Android**，别为跨平台增加复杂度。

---

## 2. 模块划分（Gradle 多模块，保持轻）

```
:app                    // 组装、导航、入口 Activity
:core:model             // 纯数据类（Capsule / Attachment / Tag / Reminder），无 Android 依赖
:core:database          // Room：DAO / Entity / DB
:core:data              // Repository（对来源/去向无感）+ FileStore
:feature:capture        // 捕获会话 UI（语音/文字输入浮层）
:feature:inbox          // 胶囊列表 / 搜索 / 标记 / 编辑
:capture-sources        // CaptureSource 各实现（Assistant / Share / Tile / Widget / IntentApi）
:sinks                  // Sink 各实现（Share / Markdown / Obsidian / Calendar / Webhook / Sync）
:transcribe             // Transcriber 接口 + whisper.cpp 封装
:ai                     // 可选：LLM 自动标题/分类/语义搜索（接口 + 实现）
```

> 关键：`:core:data` 只依赖**接口**（`CaptureSource` / `Sink` / `Transcriber` / `SyncProvider`），
> 具体实现全部在外围模块。以后加任何输入/输出都不动核心。

---

## 3. 分层架构

```
        ┌──────────────────────────────────────────────┐
        │  捕获入口（事件驱动，空闲零成本）                 │
        │  Assistant · QS Tile · Widget · Shortcut      │
        │  · Share 目标 · 公开 Intent API                │
        └───────────────┬──────────────────────────────┘
                        │  push(RawCapture)
                        ▼
   Compose UI ──▶ ViewModel ──▶ CaptureRepository
   (capture/inbox)                 │
                                   ├─ LocalStore: Room + FileStore
                                   ├─ Transcriber（WorkManager 一次性任务）
                                   ├─ AI（可选：标题/分类/embedding）
                                   ├─ Sinks（导出/发送到…）
                                   └─ SyncProvider（可选）
```

数据流（一次典型捕获）：
1. 入口触发 → 开录音会话（**仅此刻开麦**）。
2. 松手/确认 → 立即落库为 `Capsule(status = CAPTURED)` + 保存音频文件，**释放麦**，UI 立刻消失。
3. WorkManager 排一个转写任务（可延迟到充电/WiFi，视模型大小）。
4. 转写完 → 更新 `transcript`，`status = TRANSCRIBED`；可选触发 AI 生成标题+分类。
5. 用户之后在 Inbox 整理，或由 Sink 自动导出（如落进 Obsidian vault）。

> **捕获路径（步骤 1-2）必须同步、无网络、无重活**，一切耗时操作都推到步骤 3 之后。

---

## 4. 数据模型（`:core:model`）

```kotlin
data class Capsule(
    val id: String,                 // UUID
    val createdAt: Long,            // epoch millis
    val updatedAt: Long,
    val text: String = "",          // 文字内容 / 转写结果
    val audioPath: String? = null,  // 原声音频（可留可删）
    val status: CapsuleStatus,      // CAPTURED / TRANSCRIBING / TRANSCRIBED / ARCHIVED
    val colorTag: ColorTag? = null, // 颜色标记（对应原版）
    val tags: List<String> = emptyList(),
    val source: String,             // 来源标识："assistant" / "share" / "intent" / ...
    val reminderAt: Long? = null,   // 提醒时间（有则设 todo）
    val pinned: Boolean = false,
)

data class Attachment(
    val id: String,
    val capsuleId: String,
    val uri: String,
    val mime: String,
    val sizeBytes: Long,
)
// 约束（对齐原版）：一条 ≤14 个附件，单条附件总大小 ≤30MB

enum class CapsuleStatus { CAPTURED, TRANSCRIBING, TRANSCRIBED, ARCHIVED }
enum class ColorTag { RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, GRAY }

// 未处理进 Inbox 前的原始捕获载荷
data class RawCapture(
    val text: String? = null,
    val audioPath: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val source: String,
)
```

---

## 5. 核心接口（"留充足接口"的落点）

### 5.1 输入：CaptureSource

```kotlin
/** 任何能产生一条胶囊的来源都实现它；对 Repository 无感。 */
interface CaptureSource {
    val id: String
    /** 由具体实现在被触发时调用，把原始捕获交给核心。 */
    suspend fun emit(raw: RawCapture)
}
```
Repository 侧只暴露一个入口：
```kotlin
suspend fun CaptureRepository.ingest(raw: RawCapture): Capsule
```

### 5.2 输出：Sink（导出 / 发送到…）

```kotlin
/** 任何"把胶囊送出去"的去向都实现它；可注册多个。 */
interface Sink {
    val id: String
    val displayName: String
    suspend fun export(capsule: Capsule): Result<Unit>
    /** 是否自动导出（false = 仅手动"发送到…"触发） */
    val auto: Boolean get() = false
}
```

### 5.3 转写：Transcriber（可换后端）

```kotlin
interface Transcriber {
    suspend fun transcribe(audioPath: String, lang: String? = null): String
}
// 实现：WhisperCppTranscriber（离线）/ SystemSttTranscriber（系统免费快）/ CloudTranscriber（可选）
```

### 5.4 AI 增强（可选，接口隔离，可整块砍掉）

```kotlin
interface CapsuleEnricher {
    suspend fun titleFor(text: String): String
    suspend fun classify(text: String): Pair<ColorTag?, List<String>>  // 自动上色 + 打标签
    suspend fun embed(text: String): FloatArray                        // 语义搜索用
}
```

### 5.5 同步（可选）

```kotlin
interface SyncProvider {
    suspend fun push(changes: List<Capsule>)
    suspend fun pull(since: Long): List<Capsule>
}
// 实现：WebDavSync / MemosApiSync / CloudFolderSync（云盘文件夹）
```

> 注册表模式：`SinkRegistry`、`CaptureSourceRegistry` 在 `:app` 里装配，新增集成 = 新增一个实现类并注册，核心零改动。

---

## 6. 捕获入口实现要点

| 入口 | 机制 | 备注 |
|---|---|---|
| **默认语音助手** | `VoiceInteractionService` + `VoiceInteractionSessionService` | 最接近原版"长按 Home/侧键"；用户需在系统设置里设为默认助手。系统只在手势时唤醒，**平时不跑** |
| **QS 磁贴** | `TileService` | 下拉快捷设置一点即录，后台零成本 |
| **桌面 Widget** | `AppWidgetProvider`（Compose Glance） | 一键语音/文字 |
| **App Shortcut** | 静态/动态快捷方式 | 长按图标即录 |
| **系统分享目标** | `intent-filter ACTION_SEND / SEND_MULTIPLE` | 对应原版"分享到闪念胶囊"，接文字/图/链接/文件 |
| **公开 Intent API** | 导出的 `Activity` + `BroadcastReceiver`，公开 ACTION | 供 Tasker / 自动化 / adb 塞入（见 §9） |

> **不做常驻悬浮球**（overlay 有持续开销、伤电）。若确实要，做成"开会话才画、用完即撤"，且默认关闭。

---

## 7. 省电策略（硬规则）

- **绝不常驻前台服务、绝不常开麦**。捕获入口全部事件驱动。
- 录音仅在活跃会话期间，`onStop` 立即释放 `AudioRecord`/`MediaRecorder`。
- 转写走 **WorkManager 一次性任务**，不做流式常驻：
  - tiny/int8 模型 → 可即时跑；
  - base 及以上 → 加约束 `setRequiresCharging(true)` / `NetworkType.UNMETERED` 延迟批处理。
- 提醒才用 `AlarmManager.setExactAndAllowWhileIdle`，**按需一次性**，不轮询。
- 不用 `WakeLock`；尊重 Doze / App Standby。
- APK 轻量：Compose + 精简依赖，R8/proguard，按 ABI 拆分 whisper 原生库；tiny 内置、base 按需下载。

---

## 8. I/O 接口目录（可插拔清单）

**输入（CaptureSource 实现）**
- `AssistantSource` · `TileSource` · `WidgetSource` · `ShortcutSource`
- `ShareSource`（系统分享目标）
- `IntentApiSource`（公开 ACTION，见 §9）
- `ContentProviderSource`（供他 App 直接 insert）
- 可选：`ClipboardSource`（监听剪贴板，默认关）

**输出（Sink 实现）**
- `ShareSink`（系统分享出去）
- `MarkdownExportSink` / `JsonExportSink`（批量导出）
- **`ObsidianSink`**（每条处理完写成 `.md` 落进 vault 指定文件夹）← 对作者最值
- `CalendarSink`（转日历事件）/ `ReminderSink`（转系统提醒）
- `MemosApiSink` / `WebhookSink`（POST 到自建服务/webhook）
- 读接口：`CapsuleContentProvider` + 可选本地 `HttpReadEndpoint`（供作者现有脚本拉取）

---

## 9. 公开 Intent API 契约（自动化 / 脚本对接）

**写入一条胶囊**
```
ACTION: com.flashcapsule.action.CAPTURE
extras:
  text      String   (可选)
  audioPath String   (可选，file uri)
  source    String   (可选，默认 "intent")
  colorTag  String   (可选：RED/BLUE/...)
  tags      String[] (可选)
返回：capsuleId
```
示例（adb / Tasker）：
```
am broadcast -a com.flashcapsule.action.CAPTURE --es text "买净水器滤芯" --es source tasker
```

**读取胶囊**：通过 `CapsuleContentProvider`
```
content://com.flashcapsule.provider/capsules?since=<epoch>
```
返回 JSON（id/text/createdAt/tags/colorTag/status），供作者的 vault 自动化脚本轮询导入。

---

## 10. 复用计划（别从零写）

| 需求 | 复用来源 | 做法 |
|---|---|---|
| 端上 Whisper + 笔记/标签/搜索基座 | **NotelyVoice**（Compose MP, GPL-3.0, 端上 Whisper） | **首选基座**：fork 后加全局捕获 + I/O 接口层 |
| whisper.cpp JNI 模板 | ishizuki-tech/WhispersCpp-Android | 若不 fork NotelyVoice，用它接转写 |
| 全局浮动语音输入参考 | kafkasl/phone-whisper | 抄它的浮动录入交互 |
| 悬浮 UI（如需） | dofire/Floating-Bubble-View（支持 Compose） | 边缘把手 |
| 后端/API 设计参考 | usememos/memos（REST/gRPC、quick-capture 哲学） | 抄接口与导出思路 |

> ⚠️ NotelyVoice 是 **GPL-3.0**：fork 后若分发，衍生代码需同样开源。个人自用无碍；要闭源商用则**别 fork，改为参考重写 + 用 whisper.cpp 模板**。

---

## 11. 里程碑

**v1（一周内可跑，MVP）**
- 默认助手唤起 + 系统分享输入两条 CaptureSource
- 系统 STT 转写（`SystemSttTranscriber`，先不上 Whisper）
- Room 存储 + Compose Inbox（列表 / 颜色标记 / 关键词搜索 / 滑动删除）
- `ObsidianSink`（自动落 md 进 vault）+ `ShareSink`

**v2（增强）**
- 端上 Whisper（whisper.cpp，离线）+ WorkManager 转写管线
- LLM 自动标题 + 自动分类（`CapsuleEnricher`）
- 公开 Intent API + ContentProvider（对接自动化/脚本）
- QS 磁贴 + Widget

**v3（进阶）**
- 语义搜索（embedding）
- SyncProvider（WebDAV / 云盘文件夹）
- 提醒/待办、附件（≤14/条、≤30MB）
- 悬浮把手 UI（可选）

---

## 12. 一句话总纲

> **平时零存在感（0 后台/0 耗电），唤起即捕获（同步、无重活、零决策），
> 输入输出全走可插拔的 Source/Sink 接口，最终焊进作者的 Obsidian 工作流。**
