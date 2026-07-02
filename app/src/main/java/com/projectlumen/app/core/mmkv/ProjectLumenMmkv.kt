package com.projectlumen.app.core.mmkv

import android.content.Context
import com.tencent.mmkv.MMKV

object ProjectLumenMmkv {
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        MMKV.initialize(context.applicationContext)
        initialized = true
    }

    fun mmkvWithId(id: String): MMKV {
        check(initialized) { "MMKV must be initialized from ProjectLumenApplication before use." }
        return MMKV.mmkvWithID(id)
    }

    fun multiProcessMmkvWithId(id: String): MMKV {
        check(initialized) { "MMKV must be initialized from ProjectLumenApplication before use." }
        return MMKV.mmkvWithID(id, MMKV.MULTI_PROCESS_MODE)
    }

    fun encryptedMmkvWithId(id: String, cryptKey: String): MMKV {
        check(initialized) { "MMKV must be initialized from ProjectLumenApplication before use." }
        return MMKV.mmkvWithID(id, MMKV.SINGLE_PROCESS_MODE, cryptKey)
    }
}
