package com.flashcapsule.data

import android.content.Context
import java.io.File

/** 音频文件存储（app 私有目录）。 */
class FileStore(context: Context) {
    private val audioDir: File = File(context.filesDir, "audio").apply { mkdirs() }
    fun newAudioFile(id: String): File = File(audioDir, "$id.m4a")
}
