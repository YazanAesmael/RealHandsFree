package com.yaxan.realhandsfree.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt

class HandTrackingManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onResults: (HandLandmarkerResult) -> Unit,
    private val onCursorUpdate: (Float, Float, Boolean) -> Unit,
    private val onClick: (Float, Float) -> Unit,
    // NEW: Callback for Scrolling
    private val onScroll: (Float, Float, Float, Float) -> Unit
) {

    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var isBusy = false

    private var smoothedX = 0f
    private var smoothedY = 0f
    private val smoothingFactor = 0.5f

    // Scroll/Click Logic variables
    private var isPinching = false
    private var startPinchX = 0f
    private var startPinchY = 0f
    private var lastActionTime = 0L
    private val actionDebounceTime = 300L
    private val pinchThreshold = 0.08f
    private val scrollThreshold = 0.05f // Needs to move 5% of screen to count as scroll

    init {
        backgroundExecutor.execute { setupHandLandmarker() }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startCamera()
        }, 1000)
    }

    private fun setupHandLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(Delegate.GPU)

        val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener { error -> Log.e("HandTracker", "MP Error: ${error.message}") }

        try {
            handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            Log.e("HandTracker", "Failed to init MediaPipe: ${e.message}")
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(backgroundExecutor) { imageProxy ->
                detectLiveStream(imageProxy)
            }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
            } catch (exc: Exception) {
                Log.e("HandTracker", "Binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun detectLiveStream(imageProxy: ImageProxy) {
        if (handLandmarker == null || isBusy) {
            imageProxy.close()
            return
        }

        isBusy = true
        val frameTime = SystemClock.uptimeMillis()

        val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        handLandmarker?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(result: HandLandmarkerResult, inputImage: MPImage) {
        isBusy = false
        onResults(result)

        if (result.landmarks().isEmpty()) {
            onCursorUpdate(smoothedX, smoothedY, false)
            return
        }

        val landmarks = result.landmarks()[0]
        val indexTip = landmarks[8]
        val thumbTip = landmarks[4]

        smoothedX += (indexTip.x() - smoothedX) * smoothingFactor
        smoothedY += (indexTip.y() - smoothedY) * smoothingFactor

        val distance = sqrt(
            (indexTip.x() - thumbTip.x()).pow(2) +
                    (indexTip.y() - thumbTip.y()).pow(2)
        )

        // --- NEW CLICK/SCROLL LOGIC ---

        if (distance < pinchThreshold) {
            // User is currently Pinching
            if (!isPinching) {
                // START of Pinch
                isPinching = true
                startPinchX = smoothedX
                startPinchY = smoothedY
            }
            // If already pinching, we just keep tracking smoothedX/Y
        } else {
            // User Released the Pinch
            if (isPinching) {
                isPinching = false

                // Calculate how far we moved while pinching
                val moveDistance = sqrt(
                    (smoothedX - startPinchX).pow(2) +
                            (smoothedY - startPinchY).pow(2)
                )

                val currentTime = SystemClock.uptimeMillis()
                if (currentTime - lastActionTime > actionDebounceTime) {
                    lastActionTime = currentTime

                    if (moveDistance > scrollThreshold) {
                        // Large movement = SCROLL
                        Log.d("HandTracker", "SCROLL DETECTED! Dist: $moveDistance")
                        onScroll(startPinchX, startPinchY, smoothedX, smoothedY)
                    } else {
                        // Small movement = CLICK
                        Log.d("HandTracker", "CLICK DETECTED! Dist: $moveDistance")
                        onClick(startPinchX, startPinchY) // Click where we started or ended? usually start is safer
                    }
                }
            }
        }

        onCursorUpdate(smoothedX, smoothedY, isPinching)
    }

    fun stop() {
        backgroundExecutor.execute {
            handLandmarker?.close()
            handLandmarker = null
        }
        cameraProvider?.unbindAll()
        backgroundExecutor.shutdown()
    }
}