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
)
