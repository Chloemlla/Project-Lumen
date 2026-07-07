package com.projectlumen.app.core.haptics

import android.os.DynamicEffect
import android.os.HapticPlayer

class HapticPlaybackService {
    @Volatile
    private var activePlayer: HapticPlayer? = null

    fun isAvailable(): Boolean {
        return runCatching { HapticPlayer.isAvailable() }
            .getOrDefault(false)
    }

    @Synchronized
    fun playTransient(
        effect: HapticTransientEffect = HapticTransientEffect(),
        options: HapticPlaybackOptions = HapticPlaybackOptions(),
    ): Boolean {
        return playHeJson(HapticHeEffect.transient(effect), options)
    }

    @Synchronized
    fun playContinuous(
        effect: HapticContinuousEffect = HapticContinuousEffect(),
        options: HapticPlaybackOptions = HapticPlaybackOptions(),
    ): Boolean {
        return playHeJson(HapticHeEffect.continuous(effect), options)
    }

    @Synchronized
    fun playReminderNudge(): Boolean {
        return playHeJson(
            json = HapticHeEffect.reminderNudge(),
            options = HapticPlaybackOptions(amplitude = 180, intervalMillis = 0),
        )
    }

    @Synchronized
    fun playHeJson(
        json: String,
        options: HapticPlaybackOptions = HapticPlaybackOptions(),
    ): Boolean {
        if (!isAvailable()) return false
        val player = runCatching {
            HapticPlayer(DynamicEffect.create(json))
        }.getOrNull() ?: return false
        val started = runCatching {
            player.start(options)
            true
        }.getOrDefault(false)
        activePlayer = if (started) player else null
        return started
    }

    @Synchronized
    fun updateInterval(intervalMillis: Int): Boolean {
        val player = activePlayer ?: return false
        return runCatching {
            player.updateInterval(intervalMillis.coerceIn(0, 1000))
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun updateAmplitude(amplitude: Int): Boolean {
        val player = activePlayer ?: return false
        return runCatching {
            player.updateAmplitude(amplitude.coerceIn(1, 255))
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun updateFrequency(frequency: Int): Boolean {
        val player = activePlayer ?: return false
        return runCatching {
            player.updateFrequency(frequency)
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun updateParameter(
        intervalMillis: Int = -1,
        amplitude: Int = -1,
        frequency: Int = -1,
    ): Boolean {
        val player = activePlayer ?: return false
        return runCatching {
            player.updateParameter(
                intervalMillis.takeUnless { it == -1 }?.coerceIn(0, 1000) ?: -1,
                amplitude.takeUnless { it == -1 }?.coerceIn(1, 255) ?: -1,
                frequency,
            )
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun stop(): Boolean {
        val player = activePlayer ?: return false
        activePlayer = null
        return runCatching {
            player.stop()
            true
        }.getOrDefault(false)
    }

    private fun HapticPlayer.start(options: HapticPlaybackOptions) {
        val interval = options.intervalMillis?.coerceIn(0, 1000)
        val amplitude = options.amplitude?.coerceIn(1, 255)
        val frequency = options.frequency
        when {
            frequency != null -> start(options.loop, interval ?: 0, amplitude ?: 255, frequency)
            interval != null || amplitude != null -> start(options.loop, interval ?: 0, amplitude ?: 255)
            else -> start(options.loop)
        }
    }
}
