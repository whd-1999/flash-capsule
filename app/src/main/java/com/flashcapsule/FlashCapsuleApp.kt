package com.flashcapsule

import android.app.Application
import android.content.Context
import androidx.room.Room
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
            .fallbackToDestructiveMigration()
            .build()
        val sinks = SinkRegistry(
            listOf(
                ObsidianSink(this, auto = false), // 需要自动落 vault 时改 auto = true
                ShareSink(this),
            )
        )
        repository = CaptureRepository(
            dao = db.capsuleDao(),
            sinks = sinks,
            transcriber = WhisperTranscriber(this),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    companion object {
        fun from(context: Context): FlashCapsuleApp =
            context.applicationContext as FlashCapsuleApp
    }
}
