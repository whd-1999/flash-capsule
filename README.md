# Flash Capsule 闪念胶囊（复刻 + 增强）

Android 上的**零决策全局捕获**工具 —— 复刻锤子「闪念胶囊」的捕获体验，
加端上 AI（Whisper 转写 + LLM 自动标题/分类）与可插拔 I/O 接口，
最终对接 Obsidian vault / 自动化工作流。

## 设计红线
**轻量、省电、简洁**：平时 0 后台常驻、0 耗电，只有唤起的那几秒才活。

## 核心原则
1. 捕获零决策（先无脑存，处理留到以后）
2. 入口无处不在（语音优先）
3. 捕获与处理分离

## 技术栈
Kotlin · Jetpack Compose · Room · whisper.cpp（端上）· WorkManager · Koin

## 文档
- [ARCHITECTURE.md](./ARCHITECTURE.md) —— 完整架构：模块划分、数据模型、
  CaptureSource/Sink 接口、捕获入口、省电策略、公开 Intent API、复用计划、里程碑。

## 状态
📐 架构设计完成，待开工。基座候选：fork [NotelyVoice](https://github.com/Notely-Voice/NotelyVoice)（注意 GPL-3.0）。

## 里程碑
- **v1**（MVP）：助手唤起 + 分享输入 + 系统 STT + Room/Inbox + ObsidianSink
- **v2**：端上 Whisper + LLM 标题/分类 + Intent API + 磁贴/Widget
- **v3**：语义搜索 + 同步 + 提醒/附件 + 悬浮 UI
