package com.flashcapsule

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.flashcapsule.ai.DeepSeekEnricher
import com.flashcapsule.data.CaptureRepository
import com.flashcapsule.data.Settings
import com.flashcapsule.data.db.AppDatabase
import com.flashcapsule.sink.ObsidianSink
import com.flashcapsule.sink.ShareSink
import com.flashcapsule.sink.SinkRegistry
import com.flashcapsule.transcribe.WhisperTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 轻量手动 DI（ServiceLocator）。不引 Koin/Hilt，保持简洁。 */
class FlashCapsuleApp : Application() {

    lateinit var repository: CaptureRepository
        private set

    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "flashcapsule.db")
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()
        val sinks = SinkRegistry(
            listOf(
                ObsidianSink(this, auto = false), // 需要自动落 vault 时改 auto = true
                ShareSink(this),
            )
        )
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = CaptureRepository(
            dao = db.capsuleDao(),
            sinks = sinks,
            transcriber = WhisperTranscriber(this),
            settings = settings,
            scope = appScope,
            enricher = DeepSeekEnricher(settings),
        )
        // 回收站 30 天惰性清理（启动时跑，每日最多一次）
        appScope.launch { repository.purgeExpiredTrash() }
        // 给旧胶囊补 AI 标题（节流，每小时最多一次）
        appScope.launch { repository.enrichPending() }
    }

    companion object {
        fun from(context: Context): FlashCapsuleApp =
            context.applicationContext as FlashCapsuleApp
    }
}
