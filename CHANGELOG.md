# Changelog / 开发日志

本项目每次改动记一笔，方便随时知道进度到哪了。
格式参考 [Keep a Changelog](https://keepachangelog.com/)，版本遵循语义化。

## [Unreleased]
### Planned
- 侧边悬浮把手 + 边缘弹出面板（闪念胶囊标志性交互）
- 端上 Whisper 离线转写（whisper.cpp + NDK）
- LLM 自动标题 / 自动分类
- ObsidianSink 改用 SAF 目录选择器（直接落 vault）
- ContentProvider 读接口（供自动化脚本拉取）

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
