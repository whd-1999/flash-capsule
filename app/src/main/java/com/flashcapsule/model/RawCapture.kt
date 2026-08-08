package com.flashcapsule.model

/** 未进 Inbox 前的原始捕获载荷。所有 CaptureSource 都产出它。 */
data class RawCapture(
    val text: String? = null,
    val audioPath: String? = null,
    val source: String = "app",
    val colorTag: ColorTag? = null,
    val tags: List<String> = emptyList(),
)
