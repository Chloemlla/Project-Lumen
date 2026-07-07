package com.projectlumen.app.core.services

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.net.toUri
import com.projectlumen.app.core.haptics.HapticPlaybackService

class AudioService(
    private val context: Context,
    private val haptics: HapticPlaybackService,
) {
    fun playReminderTone(
        enabled: Boolean,
        soundPath: String = "",
        volumePercent: Int = 70,
        vibrate: Boolean = false,
    ) {
        if (vibrate) vibrate()
        if (!enabled) return
        val audioManager = context.getSystemService(AudioManager::class.java)
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        if (soundPath.isNotBlank() && playCustomSound(soundPath)) return
        ToneGenerator(
            AudioManager.STREAM_NOTIFICATION,
            volumePercent.coerceIn(0, 100),
        ).startTone(ToneGenerator.TONE_PROP_BEEP, 180)
    }

    private fun playCustomSound(soundPath: String): Boolean {
        val player = runCatching { MediaPlayer.create(context, soundPath.toUri()) }.getOrNull() ?: return false
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

    private fun vibrate() {
        if (haptics.playReminderNudge()) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(90L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
