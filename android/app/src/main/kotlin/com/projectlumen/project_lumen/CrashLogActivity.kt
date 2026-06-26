package com.projectlumen.project_lumen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CrashLogActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = intent.getStringExtra(EXTRA_CRASH_REPORT)
            ?: AndroidCrashGuard.getLastCrashReport(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val title = TextView(this).apply {
            text = "Project-Lumen Android crash log"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        }

        val copyButton = Button(this).apply {
            text = "\u5168\u90e8\u590d\u5236"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Project-Lumen crash log", report))
                Toast.makeText(
                    this@CrashLogActivity,
                    "\u5df2\u590d\u5236\u5168\u90e8\u65e5\u5fd7",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        val logText = TextView(this).apply {
            text = report
            textSize = 12f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val scrollView = ScrollView(this).apply {
            addView(logText)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }

        root.addView(title)
        root.addView(copyButton)
        root.addView(scrollView)
        root.gravity = Gravity.CENTER_HORIZONTAL
        setContentView(root)
    }

    companion object {
        const val EXTRA_CRASH_REPORT = "project_lumen_crash_report"
    }
}
