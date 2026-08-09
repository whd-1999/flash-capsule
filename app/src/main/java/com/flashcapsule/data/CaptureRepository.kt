package com.flashcapsule.data

import com.flashcapsule.ai.CapsuleEnricher
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Collections
import java.util.UUID

/**
 * 对来源/去向无感的核心仓库。
 * 输入统一走 [ingest]，输出统一走 [SinkRegistry]，转写走 [Transcriber]。
 */
class CaptureRepository(
    private val dao: CapsuleDao,
    private val sinks: SinkRegistry,
    private val transcriber: Transcriber,
    private val settings: Settings,
    private val scope: CoroutineScope,
    private val enricher: CapsuleEnricher? = null,
) {
    fun observeAll(): Flow<List<Capsule>> =
        dao.observeAll().map { list -> list.map(CapsuleEntity::toModel) }

    fun search(q: String): Flow<List<Capsule>> =
        dao.search(q).map { list -> list.map(CapsuleEntity::toModel) }

    fun observeTrash(): Flow<List<Capsule>> =
        dao.observeTrash().map { list -> list.map(CapsuleEntity::toModel) }

    /** 按 id 同步查（提醒 receiver 用）。 */
    fun searchById(id: String): Capsule? = runBlocking { dao.byId(id)?.toModel() }

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
            waveform = raw.waveform,
        )
        dao.upsert(capsule.toEntity())
        sinks.dispatchAuto(capsule)
        // 语音胶囊（有音频、无文字）→ 后台用 Whisper 转写填字
        if (raw.text.isNullOrBlank() && raw.audioPath != null) {
            scope.launch { transcribeInto(capsule.id, raw.audioPath) }
        } else if (capsule.text.isNotBlank()) {
            // 文字捕获 → 直接尝试 AI 标题/分类
            scope.launch { enrich(capsule.id) }
        }
        return capsule
    }

    private suspend fun transcribeInto(id: String, audioPath: String) {
        val lang = settings.sttLanguage
        val text = runCatching { transcriber.transcribe(audioPath, lang) }.getOrDefault("")
        if (text.isNotBlank()) {
            val e = dao.byId(id) ?: return
            dao.upsert(
                e.copy(
                    text = text,
                    status = CapsuleStatus.TRANSCRIBED.name,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            // 转写成功 → AI 标题/分类
            enrich(id)
        }
    }

    suspend fun setColor(id: String, color: ColorTag?) {
        val e = dao.byId(id) ?: return
        dao.upsert(e.copy(colorTag = color?.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateText(id: String, text: String) {
        val e = dao.byId(id) ?: return
        dao.upsert(e.copy(text = text, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateTitle(id: String, title: String) {
        val e = dao.byId(id) ?: return
        dao.upsert(e.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    /** 软删除：进回收站。音频文件保留（恢复后仍可用）。 */
    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    /** 恢复。doneAt 原样保留（删前已勾完成的，恢复后仍算完成）。 */
    suspend fun restore(id: String) {
        dao.restore(id, System.currentTimeMillis())
    }

    /** 彻底删：物理 DELETE + 顺带清掉音频文件。 */
    suspend fun permanentDelete(id: String) {
        dao.byId(id)?.audioPath?.let { p -> runCatching { File(p).delete() } }
        dao.permanentDelete(id)
    }

    /** 勾/取消勾完成。 */
    suspend fun toggleDone(id: String) {
        val now = System.currentTimeMillis()
        val e = dao.byId(id) ?: return
        dao.setDone(id, if (e.doneAt == null) now else null, now)
    }

    /** 置顶/取消置顶。 */
    suspend fun togglePin(id: String) {
        val e = dao.byId(id) ?: return
        dao.upsert(e.copy(pinned = !e.pinned, updatedAt = System.currentTimeMillis()))
    }

    /** 设置提醒时间；null = 取消提醒。 */
    suspend fun setReminder(id: String, time: Long?) {
        val e = dao.byId(id) ?: return
        dao.upsert(e.copy(reminderAt = time, updatedAt = System.currentTimeMillis()))
    }

    suspend fun exportTo(sinkId: String, id: String): Result<Unit> {
        val e = dao.byId(id) ?: return Result.failure(IllegalStateException("capsule not found"))
        return sinks.dispatch(sinkId, e.toModel())
    }

    /**
     * AI 标题/分类：一次调用生成 title + colorTag + tags。
     * 三层去重防重复计费：① DB 里 title 非空即跳过（幂等标记）② 未配 key 跳过 ③ 进程内并发集合防双击。
     */
    private val enriching = Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun enrich(id: String, force: Boolean = false) {
        val e = dao.byId(id) ?: return
        if (!force && e.title.isNullOrBlank().not()) return      // ① 已有标题 = 已处理过
        if (settings.apiKey.isBlank()) return                     // ② 未配 key
        val enricher = enricher ?: return
        if (!enriching.add(id)) return                            // ③ 进程内并发去重
        try {
            val text = e.text.trim().take(ENRICH_MAX_CHARS)
            if (text.isBlank()) return
            val result = enricher.enrich(text) ?: return          // 失败 → 静默降级
            val cur = dao.byId(id) ?: return
            if (!force && cur.title.isNullOrBlank().not()) return // 双检：期间被手动改过则不动
            dao.upsert(
                cur.copy(
                    title = result.title,
                    colorTag = result.colorTag?.name ?: cur.colorTag,
                    tags = if (result.tags.isNotEmpty()) result.tags.joinToString(",") else cur.tags,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        } finally {
            enriching.remove(id)
        }
    }

    /**
     * 给旧胶囊补 AI 标题：text 非空但 title 空的，节流批量补。
     * 这样 v0.13 之前存的旧胶囊也能自动出标题。
     */
    suspend fun enrichPending(limit: Int = 10) {
        if (settings.apiKey.isBlank()) return
        val last = settings.lastAutoEnrichAt
        if (System.currentTimeMillis() - last < AUTO_ENRICH_INTERVAL_MS) return
        val pending = dao.untitled().take(limit)
        if (pending.isNotEmpty()) {
            pending.forEach { enrich(it.id) }
            settings.lastAutoEnrichAt = System.currentTimeMillis()
        }
    }

    /** 回收站 30 天清理：App 启动时惰性调用，每日最多一次（不引后台任务）。 */
    suspend fun purgeExpiredTrash(force: Boolean = false) {
        val last = settings.lastTrashPurgeAt
        if (!force && last > 0 && System.currentTimeMillis() - last < PURGE_INTERVAL_MS) return
        val cutoff = System.currentTimeMillis() - TRASH_TTL_MS
        dao.trashedBefore(cutoff).forEach { e ->
            e.audioPath?.let { p -> runCatching { File(p).delete() } }
        }
        dao.purgeTrashBefore(cutoff)
        settings.lastTrashPurgeAt = System.currentTimeMillis()
    }

    companion object {
        private const val TRASH_TTL_MS = 30L * 24 * 3600 * 1000   // 30 天
        private const val PURGE_INTERVAL_MS = 24L * 3600 * 1000   // 每天最多跑一次
        private const val ENRICH_MAX_CHARS = 2000
        private const val AUTO_ENRICH_INTERVAL_MS = 60L * 60 * 1000 // 补标题每小时最多一次
    }
}
