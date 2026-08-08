package com.flashcapsule.transcribe

import kotlinx.coroutines.flow.MutableStateFlow

/** 全局转写/模型下载状态，供 UI 显示进度。 */
object TranscriptionState {
    /** 模型下载进度 0..100；null = 未在下载。 */
    val modelDownload = MutableStateFlow<Int?>(null)
}
