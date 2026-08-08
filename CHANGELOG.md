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
