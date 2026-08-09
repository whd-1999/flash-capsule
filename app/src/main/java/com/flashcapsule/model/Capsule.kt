package com.flashcapsule.model

enum class CapsuleStatus { CAPTURED, TRANSCRIBING, TRANSCRIBED, ARCHIVED }

enum class ColorTag { RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, GRAY }

/** 一条闪念胶囊。核心领域模型，无 Android 依赖。 */
data class Capsule(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val text: String = "",
    val audioPath: String? = null,
    val status: CapsuleStatus = CapsuleStatus.CAPTURED,
    val colorTag: ColorTag? = null,
    val tags: List<String> = emptyList(),
    val source: String = "app",
    val reminderAt: Long? = null,
    val pinned: Boolean = false,
    /** 录音波形振幅采样（0..32767），用于绘制胶囊里的波形。 */
    val waveform: List<Int> = emptyList(),
    /** AI 摘要标题；空 = 未生成（列表回退显示正文）。 */
    val title: String = "",
    /** 软删除时间；null = 未删（回收站）。 */
    val deletedAt: Long? = null,
    /** 已完成时间；null = 未完成（勾选完成时记录）。 */
    val doneAt: Long? = null,
    /** AI 智能判断的类型：note / search / reminder / calendar（3.0 速记/搜索/指令）。 */
    val kind: String = "",
)
