package com.projectlumen.app.core.haptics

/**
 * Optional vendor HE haptic playback.
 *
 * `android.os.HapticPlayer` / `DynamicEffect` come from the hestub compile-only surface and are
 * not present on stock AOSP emulators. Keep all vendor API access reflective so class loading
 * never fails on devices without those framework classes (baseline-profile managed devices).
 */
class HapticPlaybackService {
    @Volatile
    private var activePlayer: Any? = null

    fun isAvailable(): Boolean {
        val playerClass = hapticPlayerClass() ?: return false
        return runCatching {
            playerClass.getMethod("isAvailable").invoke(null) as? Boolean
        }.getOrNull() == true
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
        val player = createPlayer(json) ?: return false
        val started = runCatching {
            startPlayer(player, options)
            true
        }.getOrDefault(false)
        activePlayer = if (started) player else null
        return started
    }

    @Synchronized
    fun updateInterval(intervalMillis: Int): Boolean {
        val player = activePlayer ?: return false
        return invokeBoolean(player, "updateInterval", intervalMillis.coerceIn(0, 1000))
    }

    @Synchronized
    fun updateAmplitude(amplitude: Int): Boolean {
        val player = activePlayer ?: return false
        return invokeBoolean(player, "updateAmplitude", amplitude.coerceIn(1, 255))
    }

    @Synchronized
    fun updateFrequency(frequency: Int): Boolean {
        val player = activePlayer ?: return false
        return invokeBoolean(player, "updateFrequency", frequency)
    }

    @Synchronized
    fun updateParameter(
        intervalMillis: Int = -1,
        amplitude: Int = -1,
        frequency: Int = -1,
    ): Boolean {
        val player = activePlayer ?: return false
        return runCatching {
            player.javaClass.getMethod(
                "updateParameter",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(
                player,
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
            player.javaClass.getMethod("stop").invoke(player)
            true
        }.getOrDefault(false)
    }

    private fun createPlayer(json: String): Any? {
        val playerClass = hapticPlayerClass() ?: return null
        val effectClass = dynamicEffectClass() ?: return null
        return runCatching {
            val effect = effectClass.getMethod("create", String::class.java).invoke(null, json)
            playerClass.getConstructor(effectClass).newInstance(effect)
        }.getOrNull()
    }

    private fun startPlayer(player: Any, options: HapticPlaybackOptions) {
        val interval = options.intervalMillis?.coerceIn(0, 1000)
        val amplitude = options.amplitude?.coerceIn(1, 255)
        val frequency = options.frequency
        val playerClass = player.javaClass
        when {
            frequency != null -> {
                playerClass.getMethod(
                    "start",
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).invoke(player, options.loop, interval ?: 0, amplitude ?: 255, frequency)
            }
            interval != null || amplitude != null -> {
                playerClass.getMethod(
                    "start",
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                ).invoke(player, options.loop, interval ?: 0, amplitude ?: 255)
            }
            else -> {
                playerClass.getMethod("start", Boolean::class.javaPrimitiveType)
                    .invoke(player, options.loop)
            }
        }
    }

    private fun invokeBoolean(player: Any, methodName: String, value: Int): Boolean {
        return runCatching {
            player.javaClass
                .getMethod(methodName, Int::class.javaPrimitiveType)
                .invoke(player, value)
            true
        }.getOrDefault(false)
    }

    private fun hapticPlayerClass(): Class<*>? {
        return runCatching { Class.forName("android.os.HapticPlayer") }.getOrNull()
    }

    private fun dynamicEffectClass(): Class<*>? {
        return runCatching { Class.forName("android.os.DynamicEffect") }.getOrNull()
    }
}
