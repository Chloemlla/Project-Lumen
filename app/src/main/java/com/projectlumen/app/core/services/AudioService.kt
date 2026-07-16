package com.projectlumen.app.core.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.net.toUri
import com.projectlumen.app.core.haptics.HapticPlaybackService

/**
 * Short reminder tones only.
 *
 * Android 17 tightens background audio: continuous media requires mediaPlayback FGS with WIU.
 * Lumen only needs transient notification cues, so we use USAGE_NOTIFICATION attributes and avoid
 * long-running background media sessions.
 */
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
        if (soundPath.isNotBlank() && playCustomSound(soundPath, volumePercent)) return
        playSystemBeep(volumePercent)
    }

    private fun playSystemBeep(volumePercent: Int) {
        runCatching {
            ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                volumePercent.coerceIn(0, 100),
            ).startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        }
    }

    private fun playCustomSound(soundPath: String, volumePercent: Int): Boolean {
        val player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, soundPath.toUri())
                val volume = (volumePercent.coerceIn(0, 100) / 100f)
                setVolume(volume, volume)
                setOnCompletionListener { completedPlayer -> completedPlayer.release() }
                setOnErrorListener { failedPlayer, _, _ ->
                    failedPlayer.release()
                    true
                }
                prepare()
            }
        }.getOrNull() ?: return false
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
