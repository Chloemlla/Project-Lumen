package com.projectlumen.app.core.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Android 17/18-ready share helpers.
 *
 * Vivo/Android guidance: do not rely on implicit URI grants for ACTION_SEND*.
 * Always attach ClipData + FLAG_GRANT_READ_URI_PERMISSION so receivers can open FileProvider URIs.
 */
internal object SecureShareIntents {
    fun shareStream(
        context: Context,
        uri: Uri,
        mimeType: String,
        subject: String,
        chooserTitle: String,
        extraFlags: Int = Intent.FLAG_ACTIVITY_NEW_TASK,
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(extraFlags)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(extraFlags)
        }
        context.startActivity(chooser)
    }

    fun viewApk(
        context: Context,
        uri: Uri,
    ): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newUri(context.contentResolver, "apk", uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
    }
}
