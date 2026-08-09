package com.flashcapsule.capture

import android.media.MediaPlayer

/** 单例播放器：同一时刻只播一条胶囊录音。 */
object AudioPlayer {
    private var player: MediaPlayer? = null
    private var playingPath: String? = null

    val currentPath: String? get() = playingPath

    /** 当前播放位置（毫秒）；未播放返回 0。 */
    fun position(): Int = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    /** 当前音频总时长（毫秒）；未播放返回 0。 */
    fun duration(): Int = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    /** 跳到指定位置（毫秒）。 */
    fun seekTo(ms: Int) {
        runCatching {
            val p = player ?: return
            p.seekTo(ms.coerceAtLeast(0))
        }
    }

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
