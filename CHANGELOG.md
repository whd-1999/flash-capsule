# Changelog / 开发日志

本项目每次改动记一笔，方便随时知道进度到哪了。
格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本遵循语义化。

## [Unreleased]
### Planned
- 端上 Whisper 离线转写（whisper.cpp + NDK）
- LLM 自动标题 / 自动分类
- ObsidianSink 改用 SAF 目录选择器（直接落 vault）
- ContentProvider 读接口（供自动化脚本拉取）
- 把手拖到左缘 / 边缘位置记忆；面板内直接编辑/删除

## [0.7.0] - 2026-08-08
### Added
- **展开胶囊 = 原版形态**：点胶囊弹出面板，顶部 **5 个分类色标**（便签蓝/重要红/待办橙/待发送绿/灵感紫，用采样配色）→ 点选即归类染色（左侧色条）
- **底部操作栏**：分享 / 落 Obsidian / 转日历(系统日历事件) / 删除
- 展开面板内也显示语音胶囊的**波形 + ▶ 播放**
- 全矢量绘制，零图片资源

## [0.6.1] - 2026-08-08
### Fixed
- 磁贴/数字助理/主界面长按的语音**也改成录音**（之前仍走系统 STT 只出字不存音频）→ 现在**所有语音入口都存声音 + 波形**，一致
- CaptureActivity 重做：voiceMode = 录音界面（实时波形 + 完成/取消）；文字模式纯打字

## [0.6.0] - 2026-08-08
### Added
- **录音为主（对齐原版）**：悬浮面板「说话」改为录音——AudioRecorder 录 m4a（16k，备 Whisper）+ **实时波形**，点「完成」保存
- 胶囊**存音频 + 波形 + ▶ 播放**：语音胶囊显示波形和播放按钮，可回放原声（AudioPlayer）
- 数据层加 waveform 字段；DB 升级到 v2（fallbackToDestructiveMigration）
### Notes
- 本步不含转写；文字转写将在 v0.6.1 用端上 Whisper 从录音生成（NDK 已就绪）
- 磁贴/助理/主界面语音暂仍走系统 STT（出字不留音频），后续统一为录音+Whisper

## [0.5.0] - 2026-08-08
### Changed
- **全部入口统一直接收音**：磁贴/数字助理/主界面长按的语音也改用 SpeechRecognizer，不再跳系统语音界面（CaptureActivity 重做）
- 悬浮面板胶囊改**紧凑形态**（更窄 250dp、圆角 16、内距更小、字号收小），更接近原版闪念胶囊
### Fixed
- 面板"点空白关闭"现在**点哪都能关**：内容改为贴底包裹高度，列表不再吃掉下方空白的点击

## [0.4.0] - 2026-08-08
### Added
- **说话 = 直接收音**：悬浮面板改用 SpeechRecognizer 后台开麦，不再跳系统语音界面、不离开当前 App；面板内显示「聆听中…」+ 实时文字，识别完直接存
- **打字 = 面板内直接写**：不再跳走，弹出编辑框当场输入保存
- 首次说话自动请求麦克风权限（MicPermissionActivity）
### Notes
- 磁贴/助理/主界面长按的语音暂仍走系统语音界面，后续统一

## [0.3.5] - 2026-08-08
### Added
- 胶囊**点开编辑/删除**：点卡片弹出编辑框，可改文字（保存）或删除；悬浮窗内直接输入（softInputMode ADJUST_RESIZE + 自动弹键盘）
### Changed
- 遮罩从 ~90% 调浅到 ~60%，没那么压抑

## [0.3.4] - 2026-08-08
### Fixed
- 面板看不清：遮罩加深到 ~90% 黑；胶囊卡改为**不透明浅色卡 + 深色字**（真闪念胶囊那种白卡），花哨壁纸上也清晰
- 说话/打字按钮从顶部**移到底部拇指区**，不再够不到、不再和首张卡重叠
- 强制全屏真实像素尺寸（currentWindowMetrics/getRealMetrics），修复下半屏没被遮罩盖住

## [0.3.3] - 2026-08-08
### Changed
- 面板 UI 重做为闪念胶囊风格：拆掉整块长条，改为**从右侧堆叠的独立圆角"胶囊"卡片**
- 全屏暗色遮罩覆盖状态栏/挖孔区（LAYOUT_NO_LIMITS + cutout ALWAYS），修复"顶部露出原界面"的割裂感
- 顶部改为「说话/打字」两颗胶囊按钮；卡片圆角 22dp、悬浮阴影、可选颜色左条

## [0.3.2] - 2026-08-08
### Changed
- 边缘面板从"透明 Activity"改为**真正的悬浮窗（overlay window）**：盖在当前 App 之上、不再切走用户正在用的应用（修复"点开进到 app"）
- 面板在非 Activity 环境承载 Compose：自带 Lifecycle / SavedState / ViewModelStore 宿主

## [0.3.1] - 2026-08-08
### Fixed
- 侧边把手太窄(7dp)且贴边，撞上手势导航返回区导致点不到 → 加宽到 26dp、加高到 112dp、往里挪 2dp、并用 systemGestureExclusionRects 排除返回手势

## [0.3.0] - 2026-08-08
### Added
- 侧边悬浮把手（OverlayService，屏幕右缘常驻半透明小条，可上下拖动）
- 边缘弹出面板（PanelActivity，从右缘滑入：最近胶囊 + 说话/打字快速捕获，点空白关闭）
- 主界面顶栏「侧边把手」开关：一键开/关，自动申请悬浮窗权限
### Notes
- 把手为常驻悬浮窗 + 极低优先级前台服务（specialUse），与"零常驻省电"有取舍，属招牌交互的必要代价

## [0.2.0] - 2026-08-08
### Added
- 界面美化：紫色 Material3 主题、卡片式列表、颜色标记左侧色条、空状态提示
- 单按钮捕获：点 = 打字，长按 = 说话（语音说完自动存，步骤最少）
- 语音语言可选并**记忆**（跟随系统 / 中文 / 日本語 / English / 繁中 / 한국어），存 SharedPreferences
- 数字助理：VoiceInteractionService + Session + Recognition stub，可设为默认助理 → 长按电源/侧边键唤起语音捕获
- 快捷设置磁贴改为语音优先（一点直接进语音）

## [0.1.0] - 2026-08-08
### Added
- v1 MVP 架构落地：Kotlin + Jetpack Compose + Room
- 捕获入口：App 主界面 / 系统分享 / 快捷设置磁贴 / 公开广播 API（com.flashcapsule.action.CAPTURE）
- 系统 STT 语音转文字（RecognizerIntent）
- Room 本地存储 + Inbox（列表 / 关键词搜索 / 删除）
- 输出 Sink：ObsidianSink（写 .md）+ ShareSink（系统分享）
- 可插拔 I/O 接口：CaptureSource（输入）/ Sink + SinkRegistry（输出）
- 设计红线：轻量、省电（事件驱动零常驻）、留足 I/O 接口
