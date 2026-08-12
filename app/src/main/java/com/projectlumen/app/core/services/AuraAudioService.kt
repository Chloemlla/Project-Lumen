package com.projectlumen.app.core.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.projectlumen.app.core.runtime.AudioEvent

class AuraAudioService(private val context: Context) {
    fun playReminderTone(event: AudioEvent.ReminderTone) {
        if (!event.enabled) return
        val intent = Intent(ACTION_PLAY_SOUND)
            .setPackage(AURA_PACKAGE)
            .putExtra(EXTRA_SOUND_ID, event.soundId)
            .putExtra(EXTRA_VOLUME, event.volumePercent.coerceIn(0, 100) / 100f)
        runCatching { context.sendBroadcast(intent) }
            .onFailure { Log.w(TAG, "Unable to delegate reminder sound to Aura", it) }
    }

    companion object {
        private const val TAG = "AuraAudioService"
        private const val AURA_PACKAGE = "com.chloemlla.aura"
        private const val ACTION_PLAY_SOUND = "com.chloemlla.aura.action.PLAY_SOUND"
        private const val EXTRA_SOUND_ID = "com.chloemlla.aura.extra.SOUND_ID"
        private const val EXTRA_VOLUME = "com.chloemlla.aura.extra.VOLUME"
    }
}
