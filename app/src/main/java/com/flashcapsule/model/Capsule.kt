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
)
