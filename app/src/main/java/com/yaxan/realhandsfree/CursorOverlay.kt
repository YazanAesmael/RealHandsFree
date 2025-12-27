package com.yaxan.realhandsfree

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.roundToInt

class CursorOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Window 1: The small Cursor (Red Dot)
    private var cursorView: CursorView? = null
    private lateinit var cursorParams: WindowManager.LayoutParams

    // Window 2: The full-screen Skeleton
    private var skeletonView: SkeletonView? = null
    private lateinit var skeletonParams: WindowManager.LayoutParams

    private var screenWidth = 0
    private var screenHeight = 0
    private val cursorSize = 60

    // View for the Red Dot
    private inner class CursorView(context: Context) : View(context) {
        private val paint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
        var isPinching = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = if (isPinching) Color.GREEN else Color.RED
            paint.alpha = 200
            val radius = width / 2f
            canvas.drawCircle(radius, radius, radius, paint)
        }
    }

    // View for the Skeleton Lines
    private inner class SkeletonView(context: Context) : View(context) {
        private val linePaint = Paint().apply {
            color = Color.CYAN
            strokeWidth = 8f
            style = Paint.Style.STROKE
            isAntiAlias = true
            alpha = 150
        }
        private val pointPaint = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 12f
            style = Paint.Style.FILL
            isAntiAlias = true
            alpha = 150
        }

        var handResult: HandLandmarkerResult? = null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            handResult?.let { result ->
                for (landmark in result.landmarks()) {
                    // Draw Connections
                    HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                        val start = landmark[connection!!.start()]
                        val end = landmark[connection.end()]
                        canvas.drawLine(
                            start.x() * screenWidth, start.y() * screenHeight,
                            end.x() * screenWidth, end.y() * screenHeight,
                            linePaint
                        )
                    }
                    // Draw Points
                    for (point in landmark) {
                        canvas.drawPoint(
                            point.x() * screenWidth,
                            point.y() * screenHeight,
                            pointPaint
                        )
                    }
                }
            }
        }
    }

    init {
        updateScreenDimensions()
    }

    private fun updateScreenDimensions() {
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
    }

    fun show() {
        if (cursorView != null) return

        // 1. Setup Skeleton Window (Full Screen)
        skeletonView = SkeletonView(context)
        skeletonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        // Important: Skeleton goes BEHIND the cursor if z-ordering matters,
        // but both are overlays. Usually adding first puts it at bottom.

        // 2. Setup Cursor Window (Small)
        cursorView = CursorView(context)
        cursorParams = WindowManager.LayoutParams(
            cursorSize, cursorSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        cursorParams.gravity = Gravity.TOP or Gravity.START
        cursorParams.x = 0
        cursorParams.y = 0

        try {
            windowManager.addView(skeletonView, skeletonParams)
            windowManager.addView(cursorView, cursorParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateData(result: HandLandmarkerResult?, cx: Float, cy: Float, pinching: Boolean) {
        // Update Skeleton
        skeletonView?.let { view ->
            if (result != null) view.handResult = result
            view.post { view.invalidate() }
        }

        // Update Cursor
        cursorView?.let { view ->
            // Map 0.0-1.0 to screen pixels
            val xPixel = (cx * screenWidth).roundToInt()
            val yPixel = (cy * screenHeight).roundToInt()

            cursorParams.x = xPixel - (cursorSize / 2)
            cursorParams.y = yPixel - (cursorSize / 2)
            view.isPinching = pinching

            view.post {
                view.invalidate()
                try {
                    windowManager.updateViewLayout(view, cursorParams)
                } catch (_: Exception) {}
            }
        }
    }

    // "Step Aside": Shrink BOTH windows to allow clicks through
    fun stepAside(stepAside: Boolean) {
        // Handle Cursor
        cursorView?.post {
            if (stepAside) {
                cursorParams.width = 0
                cursorParams.height = 0
            } else {
                cursorParams.width = cursorSize
                cursorParams.height = cursorSize
            }
            try { windowManager.updateViewLayout(cursorView, cursorParams) } catch (_: Exception) {}
        }

        // Handle Skeleton
        skeletonView?.post {
            if (stepAside) {
                skeletonParams.width = 0
                skeletonParams.height = 0
            } else {
                skeletonParams.width = WindowManager.LayoutParams.MATCH_PARENT
                skeletonParams.height = WindowManager.LayoutParams.MATCH_PARENT
            }
            try { windowManager.updateViewLayout(skeletonView, skeletonParams) } catch (_: Exception) {}
        }
    }

    fun getScreenCoordinates(normX: Float, normY: Float): Pair<Float, Float> {
        return Pair(normX * screenWidth, normY * screenHeight)
    }

    fun remove() {
        try {
            if (cursorView != null) windowManager.removeView(cursorView)
            if (skeletonView != null) windowManager.removeView(skeletonView)
        } catch (_: Exception) {}
        cursorView = null
        skeletonView = null
    }
}