package com.projectlumen.app.core.services

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri

class AudioService(private val context: Context) {
    fun playReminderTone(enabled: Boolean, soundPath: String = "") {
        if (!enabled) return
        val audioManager = context.getSystemService(AudioManager::class.java)
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        if (soundPath.isNotBlank() && playCustomSound(soundPath)) return
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).startTone(ToneGenerator.TONE_PROP_BEEP, 180)
    }

    private fun playCustomSound(soundPath: String): Boolean {
        val player = runCatching { MediaPlayer.create(context, Uri.parse(soundPath)) }.getOrNull() ?: return false
        player.setOnCompletionListener { completedPlayer -> completedPlayer.release() }
        player.setOnErrorListener { failedPlayer, _, _ ->
            failedPlayer.release()
            true
        }
        return runCatching {
            player.start()
            true
        }.getOrElse {
            player.release()
            false
        }
    }
}
