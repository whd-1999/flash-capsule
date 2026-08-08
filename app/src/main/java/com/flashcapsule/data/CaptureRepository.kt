package com.flashcapsule.data

import com.flashcapsule.data.db.CapsuleDao
import com.flashcapsule.data.db.CapsuleEntity
import com.flashcapsule.data.db.toEntity
import com.flashcapsule.data.db.toModel
import com.flashcapsule.model.Capsule
import com.flashcapsule.model.CapsuleStatus
import com.flashcapsule.model.ColorTag
import com.flashcapsule.model.RawCapture
import com.flashcapsule.sink.SinkRegistry
import com.flashcapsule.transcribe.Transcriber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 对来源/去向无感的核心仓库。
 * 输入统一走 [ingest]，输出统一走 [SinkRegistry]，转写走 [Transcriber]。
 */
class CaptureRepository(
    private val dao: CapsuleDao,
    private val sinks: SinkRegistry,
    @Suppress("unused") private val transcriber: Transcriber, // v2: 音频→文字的差插口
) {
    fun observeAll(): Flow<List<Capsule>> =
        dao.observeAll().map { list -> list.map(CapsuleEntity::toModel) }

    fun search(q: String): Flow<List<Capsule>> =
        dao.search(q).map { list -> list.map(CapsuleEntity::toModel) }

    /** 零决策捕获入口：任何来源产出 RawCapture 后调用它即可。 */
    suspend fun ingest(raw: RawCapture): Capsule {
        val now = System.currentTimeMillis()
        val capsule = Capsule(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now,
            text = raw.text ?: "",
            audioPath = raw.audioPath,
            status = if (raw.text.isNullOrBlank() && raw.audioPath != null)
                CapsuleStatus.TRANSCRIBING else CapsuleStatus.CAPTURED,
            colorTag = raw.colorTag,
            tags = raw.tags,
            source = raw.source,
        )
        dao.upsert(capsule.toEntity())
        sinks.dispatchAuto(capsule)
        return capsule
    }

    suspend fun setColor(id: String, color: ColorTag?) {
        val e = dao.byId(id) ?: return
        dao.upsert(e.copy(colorTag = color?.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateText(id: String, text: String) {
        val e = dao.byId(id) ?: return
        dao.upsert(e.copy(text = text, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun exportTo(sinkId: String, id: String): Result<Unit> {
        val e = dao.byId(id) ?: return Result.failure(IllegalStateException("capsule not found"))
        return sinks.dispatch(sinkId, e.toModel())
    }
}
