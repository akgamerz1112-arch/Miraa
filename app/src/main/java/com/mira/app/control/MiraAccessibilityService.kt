package com.mira.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * The only way an app can look at the screen and tap/type for you. The user must switch it on by hand in
 * Android Settings > Accessibility. It does nothing by itself: MIRA only uses it while a voice session
 * has "Screen dikhao" (and "Control do") turned on.
 */
class MiraAccessibilityService : AccessibilityService() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        worker.shutdown()
        super.onDestroy()
    }

    fun screenSize(): Pair<Int, Int> {
        val m = resources.displayMetrics
        return m.widthPixels to m.heightPixels
    }

    /** Screenshot as a small JPEG (needs Android 11+). null if it failed (for example a secure/blocked screen). */
    fun screenshotJpeg(maxWidth: Int, quality: Int, onResult: (ByteArray?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            onResult(null)
            return
        }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY, worker,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        var jpeg: ByteArray? = null
                        val hw = result.hardwareBuffer
                        try {
                            val wrapped = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                            if (wrapped != null) {
                                val soft = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                                wrapped.recycle()
                                val (tw, th) = ScreenMath.scaledSize(soft.width, soft.height, maxWidth)
                                val scaled = if (tw != soft.width) Bitmap.createScaledBitmap(soft, tw, th, true) else soft
                                val out = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                                jpeg = out.toByteArray()
                                if (scaled !== soft) scaled.recycle()
                                soft.recycle()
                            }
                        } catch (e: Exception) {
                            jpeg = null
                        } finally {
                            hw.close()
                        }
                        onResult(jpeg)
                    }

                    override fun onFailure(errorCode: Int) {
                        onResult(null)
                    }
                }
            )
        } catch (e: Exception) {
            onResult(null)
        }
    }

    fun tap(x: Float, y: Float, cb: (Boolean) -> Unit) {
        stroke(Path().apply { moveTo(x, y) }, 60, cb)
    }

    fun swipe(s: ScreenMath.Swipe, cb: (Boolean) -> Unit) {
        stroke(Path().apply { moveTo(s.x1, s.y1); lineTo(s.x2, s.y2) }, 350, cb)
    }

    private fun stroke(path: Path, durationMs: Long, cb: (Boolean) -> Unit) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = cb(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = cb(false)
            },
            null
        )
        if (!accepted) cb(false)
    }

    /** back / home / recents / notifications. */
    fun press(button: String): Boolean = when (button.trim().lowercase()) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        else -> false
    }

    /** Types into the text box that has focus. Returns null on success, or a Hinglish error. Never types into password boxes. */
    fun typeText(text: String): String? {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return "Koi text box select nahi hai. Pehle text box pe tap karo."
        try {
            if (node.isPassword) return "Password box mein MIRA type nahi karegi."
            val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current + text)
            }
            return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) null else "Text set nahi hua."
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    /** Opens an installed app by (part of) its name. Returns null on success, or a Hinglish error. */
    @Suppress("DEPRECATION")
    fun openApp(query: String): String? {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val idx = AppMatch.best(query, apps.map { it.loadLabel(pm).toString() })
            ?: return "'$query' naam ka app nahi mila."
        val launch = pm.getLaunchIntentForPackage(apps[idx].activityInfo.packageName)
            ?: return "Ye app khul nahi sakta."
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(launch)
            null
        } catch (e: Exception) {
            "Android ne app kholne se roka."
        }
    }

    companion object {
        @Volatile var instance: MiraAccessibilityService? = null
    }
}
