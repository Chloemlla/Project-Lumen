package com.chloemlla.lumen.crash

import androidx.core.content.FileProvider

/**
 * Distinct FileProvider subclass so the SDK provider can merge into hosts that already
 * declare `androidx.core.content.FileProvider` under a different authority.
 */
class LumenCrashFileProvider : FileProvider()
