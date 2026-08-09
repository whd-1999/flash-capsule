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
Kotlin · Jetpack Compose · Room · whisper.cpp（端上，NDK/CMake）· 手动 DI（ServiceLocator，不引 Koin/Hilt）

## 文档
- [ARCHITECTURE.md](./ARCHITECTURE.md) —— 完整架构：模块划分、数据模型、
  CaptureSource/Sink 接口、捕获入口、省电策略、公开 Intent API、复用计划、里程碑。
- [DESIGN_FROM_VIDEO.md](./DESIGN_FROM_VIDEO.md) —— 逐帧反推的原版设计
- [CHANGELOG.md](./CHANGELOG.md) —— 版本历史

## 状态
✅ 可用的 MVP（v0.16.0）：悬浮把手（可拖、位置记忆、可水平拖换边）+ 边缘弹出面板 +
录音/波形/回放 + **播放控制条**（进度 Slider + 时间）+ **端上 Whisper 离线转写**（arm64）+
5 分类色标 + 操作栏（分享 / 落 Obsidian / 转日历 / 删除 / 置顶 / 提醒）+ **展开即自动网络搜索**
（Wikipedia 摘要 + Google/百度/Bing/维基引擎切换）+ **回收站（30天）** +
**已完成勾选与筛选** + **LLM 自动标题/分类**（DeepSeek API，可选）+
**ContentProvider 读接口**。全程零位图，纯 Compose 绘制。

## 里程碑
- **v0.1–v0.3** ✅ MVP 骨架：Room/Inbox、分享/磁贴/广播捕获、悬浮把手 + 边缘面板
- **v0.4–v0.6** ✅ 语音对齐原版：直接收音、录音 + 波形 + 回放（存声音）
- **v0.7–v0.10** ✅ 端上 AI + 交互完善：5 分类、操作栏、Whisper 离线转写、把手位置记忆
- **v0.11** ✅ 原版自动网络搜索面板
- **v0.12** ✅ 转写加速（base 模型）+ 语言参数接通
- **v0.13** ✅ 回收站 + 已完成勾选 + LLM 自动标题/分类
- **v0.14** ✅ 播放控制条 + 把手左右切换
- **v0.15** ✅ ObsidianSink SAF 目录选择器（直接落 vault）
- **v0.16** ✅ ContentProvider + 置顶 + 待办提醒 + 把手水平拖动换边
- **待做** ⏳：SAF 直接落 vault、ContentProvider 读接口、语义搜索 + 同步
