package com.flashcapsule.ai

import com.flashcapsule.model.ColorTag

/**
 * 一次 LLM 调用同时产出标题 + 分类（比 ARCHITECTURE 里 titleFor/classify 分开少一次往返）。
 * 失败（网络/解析/无 key）一律返回 null，调用方静默降级。
 */
interface CapsuleEnricher {
    suspend fun enrich(text: String): Enrichment?
}

data class Enrichment(
    val title: String,
    val colorTag: ColorTag?,
    val tags: List<String>,
    /** 智能判断：note / search / reminder / calendar。 */
    val kind: String = "note",
)
