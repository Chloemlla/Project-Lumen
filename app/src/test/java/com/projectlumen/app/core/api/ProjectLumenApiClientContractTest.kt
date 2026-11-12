package com.projectlumen.app.core.api

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 请求路径契约：业务端点必须是**无版本段**的相对路径（`auth/email/start`、`me`…）。
 *
 * 2026-11-11 线上：客户端 20 个端点全写成 `v1/…`，而 Synapse 把整棵 Lumen 路由挂在
 * `/api/lumen` 上且没有 `v1` 子段 ⇒ 除 `health` 之外每个请求都是 Express 兜底 404，
 * 用户看到的就是一句 `Project Lumen API request failed with HTTP 404`。
 *
 * 这里刻意做**源码级**断言而不是拿拦截器跑真请求：`request()` 里读
 * `android.os.SystemClock.elapsedRealtime()` 做耗时诊断，而它在 JVM 单元测试里是
 * 未实现的 stub（`RuntimeException: Stub!`），执行路径必然崩 —— 与本仓
 * `BackendCommunicationArchitectureTest` 同一套做法（读源码断言架构契约）。
 */
class ProjectLumenApiClientContractTest {

    private val apiSource = read("core/api/ProjectLumenApiClient.kt")

    @Test
    fun everyEndpointPathIsRelativeAndUnversioned() {
        val paths = ENDPOINT_PATH.findAll(apiSource).map { it.groupValues[1] }.toList()
        assertTrue("应至少解析出 20 个端点路径，实际 ${paths.size}", paths.size >= 20)
        paths.forEach { path ->
            assertTrue("端点路径不能以 / 开头: $path", !path.startsWith("/"))
            assertTrue("端点路径不能带版本段（服务端没有 /v1 子段）: $path", !path.startsWith("v1/"))
            assertTrue("端点路径不能为空", path.isNotBlank())
        }
    }

    @Test
    fun theKnownEndpointFamiliesAreAllPresent() {
        val paths = ENDPOINT_PATH.findAll(apiSource).map { it.groupValues[1] }.toSet()
        EXPECTED_PATHS.forEach { expected ->
            assertTrue("缺少端点路径: $expected（现有 ${paths.sorted()}）", expected in paths)
        }
    }

    @Test
    fun theBaseUrlIsComposedExactlyOnce() {
        // 路径里不写基址/主机，避免出现「基址 + 绝对路径」拼出双重前缀的写法。
        val paths = ENDPOINT_PATH.findAll(apiSource).map { it.groupValues[1] }.toList()
        paths.forEach { path ->
            assertTrue("端点路径里不应出现主机名: $path", !path.contains("://"))
            assertTrue("端点路径里不应出现 /api 前缀: $path", !path.startsWith("api/"))
        }
        assertEquals(
            "路径前缀只能来自 resolvedBaseUrl",
            1,
            Regex("""resolvedBaseUrl\.trimEnd\('/'\)""").findAll(apiSource).count(),
        )
    }

    private fun read(relativePath: String): String {
        val file = File(findAppSourceRoot(), relativePath)
        assertTrue("Missing production source: $relativePath", file.isFile)
        return file.readText()
    }

    private fun findAppSourceRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir")).absoluteFile
        return generateSequence(workingDirectory) { it.parentFile }
            .flatMap { directory ->
                sequenceOf(
                    File(directory, "app/src/main/java/com/projectlumen/app"),
                    File(directory, "src/main/java/com/projectlumen/app"),
                )
            }
            .firstOrNull { it.isDirectory }
            ?: error("Cannot locate Project Lumen application sources from $workingDirectory")
    }

    private companion object {
        val ENDPOINT_PATH = Regex("""path = "([^"]*)"""")
        val EXPECTED_PATHS = setOf(
            "health",
            "auth/email/start",
            "auth/email/verify",
            "auth/session/refresh",
            "me",
            "devices/register",
            "entitlements",
            "config/feature-flags",
            "purchases/google/verify",
            "sync/push",
            "telemetry",
            "face-analysis/frames",
            "device-control/vision/sessions",
            "device-control/vision/heartbeat",
            "device-control/vision/frames",
            "device-control/vision/surface-frames",
            "device-control/lifecycle/events",
            "backups",
            "backups/latest",
        )
    }
}
