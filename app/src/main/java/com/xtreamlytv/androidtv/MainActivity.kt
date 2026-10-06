package com.xtreamlytv.androidtv

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xtreamlytv.androidtv.ui.XtreamlyTvApp
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = java.io.StringWriter()
                error.printStackTrace(java.io.PrintWriter(sw))
                val crashLog = buildString {
                    append("CRASH TIME: "); append(java.util.Date()); append("\n")
                    append("THREAD: "); append(thread.name); append("\n\n")
                    append(sw.toString())
                }
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "xtreamly_crash_${System.currentTimeMillis()}.txt")
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { u ->
                    contentResolver.openOutputStream(u)?.use { os ->
                        os.write(crashLog.toByteArray())
                    }
                }
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(1)
        }

        

        val composeReady = AtomicBoolean(false)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !composeReady.get() }

        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.rgb(7, 16, 20)))
        enableEdgeToEdge()
        setContent {
            XtreamlyTvApp(onContentReady = { composeReady.set(true) })
        }
    }
}
