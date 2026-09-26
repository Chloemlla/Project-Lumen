package com.chloemlla.lumen.crash

import android.content.Context

/**
 * Resolves the context a crash report should be read from and written to.
 *
 * `Application.attachBaseContext()` runs before `LoadedApk` records the process Application, so
 * `getApplicationContext()` still returns `null` when a host installs the SDK from there — the
 * framework only publishes it before `Application.onCreate()`. Dereferencing it eagerly therefore
 * threw `NullPointerException: getApplicationContext(...) must not be null`, which left the process
 * without a reporter for the whole `attachBaseContext` → `onCreate` window.
 *
 * The Application instance is already a usable [Context] at that point, so it is the fallback.
 * Every call made after the framework has published the Application still resolves through
 * `getApplicationContext()`, so hosts that pass an Activity keep the previous behaviour.
 */
internal fun Context.crashInstallContext(): Context = applicationContext ?: this
