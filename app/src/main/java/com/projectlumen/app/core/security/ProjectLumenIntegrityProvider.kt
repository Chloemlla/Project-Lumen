package com.projectlumen.app.core.security

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.projectlumen.app.BuildConfig
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ProjectLumenIntegrityProvider(context: Context) {
    private val appContext = context.applicationContext
    private val manager by lazy { IntegrityManagerFactory.create(appContext) }

    suspend fun tokenForRequest(path: String, bodyText: String?): String? {
        val cloudProjectNumber = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER
        if (cloudProjectNumber <= 0L) return null
        val request = IntegrityTokenRequest.builder()
            .setCloudProjectNumber(cloudProjectNumber)
            .setNonce(nonce(path, bodyText.orEmpty()))
            .build()

        return suspendCancellableCoroutine { continuation ->
            manager.requestIntegrityToken(request)
                .addOnSuccessListener { response ->
                    if (continuation.isActive) continuation.resume(response.token())
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }

    private fun nonce(path: String, bodyText: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${UUID.randomUUID()}:$path:$bodyText".toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
