package com.flashcapsule.capture

import android.media.MediaPlayer

/** 单例播放器：同一时刻只播一条胶囊录音。 */
object AudioPlayer {
    private var player: MediaPlayer? = null
    private var playingPath: String? = null

    val currentPath: String? get() = playingPath

    fun toggle(path: String, onStateChange: () -> Unit) {
        if (playingPath == path) { stop(); onStateChange(); return }
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stop(); onStateChange() }
                prepare()
                start()
            }
            playingPath = path
        }.onFailure { playingPath = null }
        onStateChange()
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        playingPath = null
    }
}
