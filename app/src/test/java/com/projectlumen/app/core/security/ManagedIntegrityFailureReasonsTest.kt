package com.projectlumen.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class ManagedIntegrityFailureReasonsTest {
    @Test
    fun reportsManagedSignalsWithoutEvaluatingNativeIdentity() {
        assertEquals(
            emptyList<String>(),
            managedIntegrityFailureReasons(
                javaDebugDetected = false,
                runtimeHookDetected = false,
            ),
        )
        assertEquals(
            listOf("java_debugger"),
            managedIntegrityFailureReasons(
                javaDebugDetected = true,
                runtimeHookDetected = false,
            ),
        )
        assertEquals(
            listOf("java_debugger", "java_runtime_hook"),
            managedIntegrityFailureReasons(
                javaDebugDetected = true,
                runtimeHookDetected = true,
            ),
        )
    }
}
