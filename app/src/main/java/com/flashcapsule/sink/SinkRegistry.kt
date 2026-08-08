package com.flashcapsule.sink

import com.flashcapsule.model.Capsule

/** 输出注册表。核心通过它派发，不关心具体去向。 */
class SinkRegistry(private val sinks: List<Sink>) {

    fun all(): List<Sink> = sinks

    /** 仅手动触发的去向（给"发送到…"菜单用）。 */
    fun manual(): List<Sink> = sinks.filterNot { it.auto }

    suspend fun dispatch(id: String, capsule: Capsule): Result<Unit> =
        sinks.firstOrNull { it.id == id }?.export(capsule)
            ?: Result.failure(IllegalArgumentException("no sink: $id"))

    /** 捕获时自动导出（如自动落 Obsidian）。 */
    suspend fun dispatchAuto(capsule: Capsule) {
        sinks.filter { it.auto }.forEach { runCatching { it.export(capsule) } }
    }
}
