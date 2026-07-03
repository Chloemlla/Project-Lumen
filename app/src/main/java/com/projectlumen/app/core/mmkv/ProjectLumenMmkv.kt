package com.projectlumen.app.core.mmkv

import android.content.Context
import com.tencent.mmkv.MMKV
import java.io.File

object ProjectLumenMmkv {
    @Volatile
    private var initialized = false
    private var initializationFailure: Throwable? = null

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        initializationFailure = null
        val rootDir = File(context.filesDir, ROOT_DIR_NAME).absolutePath
        runCatching { MMKV.initialize(context, rootDir) }
            .onSuccess { initialized = true }
            .onFailure { throwable ->
                initializationFailure = throwable
                throw IllegalStateException("Unable to initialize MMKV storage.", throwable)
            }
    }

    fun mmkvWithId(id: String): MMKV {
        checkInitialized()
        return MMKV.mmkvWithID(id)
    }

    fun multiProcessMmkvWithId(id: String): MMKV {
        checkInitialized()
        return MMKV.mmkvWithID(id, MMKV.MULTI_PROCESS_MODE)
    }

    fun encryptedMmkvWithId(id: String, cryptKey: String): MMKV {
        checkInitialized()
        return MMKV.mmkvWithID(id, MMKV.SINGLE_PROCESS_MODE, cryptKey)
    }

    private fun checkInitialized() {
        if (initialized) return
        initializationFailure?.let { throwable ->
            throw IllegalStateException("MMKV storage is unavailable after initialization failure.", throwable)
        }
        error("MMKV must be initialized from ProjectLumenApplication before use.")
    }

    private const val ROOT_DIR_NAME = "mmkv"
}
