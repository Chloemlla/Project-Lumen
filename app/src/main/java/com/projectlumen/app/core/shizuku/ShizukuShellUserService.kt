package com.projectlumen.app.core.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import kotlin.system.exitProcess

class ShizukuShellUserService : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_EXEC -> {
                data.enforceInterface(DESCRIPTOR)
                val result = execute(data.readString().orEmpty())
                reply?.writeNoException()
                reply?.writeInt(result.exitCode)
                reply?.writeString(result.output)
                reply?.writeString(result.error)
                true
            }
            TRANSACTION_DESTROY,
            TRANSACTION_DESTROY_AIDL -> {
                reply?.writeNoException()
                exitProcess(0)
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    private fun execute(command: String): ShellExecutionResult {
        return runCatching {
            val process = ProcessBuilder("sh", "-c", command).start()
            var output = ""
            var error = ""
            val outputThread = Thread {
                output = process.inputStream.bufferedReader().use { it.readText() }
            }
            val errorThread = Thread {
                error = process.errorStream.bufferedReader().use { it.readText() }
            }
            outputThread.start()
            errorThread.start()
            var exitCode = EXIT_TIMEOUT
            val waitThread = Thread {
                exitCode = process.waitFor()
            }
            waitThread.start()
            waitThread.join(COMMAND_TIMEOUT_MILLIS)
            val finished = !waitThread.isAlive
            if (!finished) {
                process.destroy()
            }
            outputThread.join(THREAD_JOIN_TIMEOUT_MILLIS)
            errorThread.join(THREAD_JOIN_TIMEOUT_MILLIS)
            ShellExecutionResult(
                exitCode = if (finished) exitCode else EXIT_TIMEOUT,
                output = output,
                error = if (finished) error else error.ifBlank { "Command timed out." },
            )
        }.getOrElse { throwable ->
            ShellExecutionResult(
                exitCode = EXIT_EXCEPTION,
                output = "",
                error = throwable.message.orEmpty().ifBlank { throwable.javaClass.simpleName },
            )
        }
    }

    private data class ShellExecutionResult(
        val exitCode: Int,
        val output: String,
        val error: String,
    )

    companion object {
        const val DESCRIPTOR = "com.projectlumen.app.core.shizuku.ShizukuShellUserService"
        const val TRANSACTION_EXEC = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_DESTROY = 16_777_115
        const val TRANSACTION_DESTROY_AIDL = 16_777_114

        private const val COMMAND_TIMEOUT_MILLIS = 10_000L
        private const val THREAD_JOIN_TIMEOUT_MILLIS = 1_000L
        private const val EXIT_TIMEOUT = 124
        private const val EXIT_EXCEPTION = -1
    }
}
