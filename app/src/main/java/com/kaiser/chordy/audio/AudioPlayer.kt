package com.kaiser.chordy.audio

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/**
 * One-shot audio player for TTS bytes. MediaPlayer wants a seekable source on the
 * main thread, so bytes land in a cache-dir temp file first. A new event
 * mid-playback cancels and replaces whatever was speaking — Chordy never talks
 * over himself. Audio is garnish by contract: every failure is swallowed, the
 * text bubble is the meal.
 */
class AudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    /** Play audio bytes, replacing any in-flight playback. Call from any thread. */
    fun play(bytes: ByteArray) {
        stop()
        var file: File? = null
        try {
            file = File.createTempFile("chordy_tts", ".mp3", context.cacheDir).apply {
                writeBytes(bytes)
            }
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(file.absolutePath)
            mp.setVolume(1f, 1f)
            mp.setOnCompletionListener { done ->
                runCatching { done.release() }
                file?.delete()
                if (player === done) player = null
            }
            mp.setOnErrorListener { failed, _, _ ->
                runCatching { failed.release() }
                file?.delete()
                if (player === failed) player = null
                true
            }
            mp.prepare()   // local file — fast, no async prepare ceremony needed
            mp.start()
        } catch (e: Exception) {
            // Corrupt bytes, full disk, unsupported codec — skip audio, keep living.
            runCatching { player?.release() }
            player = null
            file?.delete()
        }
    }

    /** Cancel any in-flight playback and drop resources. Called on every new event. */
    fun stop() {
        player?.let { p ->
            runCatching {
                if (p.isPlaying) p.stop()
                p.release()
            }
        }
        player = null
    }
}
