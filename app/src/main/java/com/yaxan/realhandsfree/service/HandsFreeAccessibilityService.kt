package com.yaxan.realhandsfree.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.yaxan.realhandsfree.CursorOverlay
import com.yaxan.realhandsfree.tracking.HandTrackingManager

class HandsFreeAccessibilityService : AccessibilityService(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private var handTrackingManager: HandTrackingManager? = null
    private var cursorOverlay: CursorOverlay? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenWidth = 0
    private var screenHeight = 0

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            screenWidth = size.x
            screenHeight = size.y
        }

        cursorOverlay = CursorOverlay(this)
        cursorOverlay?.show()

        handTrackingManager = HandTrackingManager(
            context = this,
            lifecycleOwner = this,
            onResults = { result ->
                cursorOverlay?.updateData(result, 0f, 0f, false)
            },
            onCursorUpdate = { x, y, isPinching ->
                cursorOverlay?.updateData(null, x, y, isPinching)
            },
            onClick = { x, y ->
                val (screenX, screenY) = cursorOverlay?.getScreenCoordinates(x, y) ?: Pair(0f, 0f)
                performClick(screenX, screenY)
            },
            onScroll = { startX, startY, endX, endY ->
                val (screenStartX, screenStartY) = cursorOverlay?.getScreenCoordinates(startX, startY) ?: Pair(0f, 0f)
                val (screenEndX, screenEndY) = cursorOverlay?.getScreenCoordinates(endX, endY) ?: Pair(0f, 0f)

                performScroll(screenStartX, screenStartY, screenEndX, screenEndY)
            }
        )
    }

    private fun performClick(x: Float, y: Float) {
        cursorOverlay?.stepAside(true)

        mainHandler.postDelayed({
            val path = Path()
            path.moveTo(x, y)
            path.lineTo(x, y + 10f)

            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    cursorOverlay?.stepAside(false)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    cursorOverlay?.stepAside(false)
                }
            }, null)
        }, 20)
    }

    private fun performScroll(startX: Float, startY: Float, endX: Float, endY: Float) {
        cursorOverlay?.stepAside(true)

        mainHandler.postDelayed({
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            val stroke = GestureDescription.StrokeDescription(path, 0, 300)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d("HandsFree", "Scroll Finished")
                    cursorOverlay?.stepAside(false)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    cursorOverlay?.stepAside(false)
                }
            }, null)
        }, 20)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        handTrackingManager?.stop()
        cursorOverlay?.remove()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
}