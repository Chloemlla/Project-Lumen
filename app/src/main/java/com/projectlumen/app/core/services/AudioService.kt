package com.projectlumen.app.core.services

import android.content.Context
import android.media.ToneGenerator
import android.media.AudioManager

class AudioService(private val context: Context) {
    fun playReminderTone(enabled: Boolean) {
        if (!enabled) return
        val audioManager = context.getSystemService(AudioManager::class.java)
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).startTone(ToneGenerator.TONE_PROP_BEEP, 180)
    }
}
