package com.flashcapsule.sink

import com.flashcapsule.model.Capsule

/** 任何"把胶囊送出去"的去向都实现它。新增导出目标 = 新增一个实现并注册。 */
interface Sink {
    val id: String
    val displayName: String

    /** true = 捕获时自动导出；false = 仅手动"发送到…"触发。 */
    val auto: Boolean get() = false

    suspend fun export(capsule: Capsule): Result<Unit>
}
