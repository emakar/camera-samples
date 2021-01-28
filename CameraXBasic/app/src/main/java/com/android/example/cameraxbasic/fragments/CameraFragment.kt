/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Logger
import androidx.camera.core.Preview
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.android.example.cameraxbasic.MainActivity
import com.android.example.cameraxbasic.R
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
@SuppressLint("RestrictedApi")
class CameraFragment : Fragment() {

    private lateinit var container: ConstraintLayout
    private lateinit var viewFinder: PreviewView
    private lateinit var outputDirectory: File

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private lateinit var preview: Preview
    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture

    private var imageAnalysis: ImageAnalysis? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var capturedImage: Bitmap? = null
    private var currentMaxResolution: Size? = null

    private lateinit var frameProcessor: FrameProcessor
    private lateinit var yuvToRgbConverter: YuvToRgbConverter

    private lateinit var camera: Camera
    private lateinit var cameraProvider: ProcessCameraProvider

    private val cameraExecutor = CameraXExecutors.mainThreadExecutor()

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    CameraFragmentDirections.actionCameraToPermissions()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Shut down our background executor
//        cameraExecutor.shutdown()
        initCameraJob?.cancel()
        imageAnalysis?.clearAnalyzer()
        yuvToRgbConverter.destroy()
        scope.coroutineContext.cancelChildren()
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_camera, container, false)

    private fun setGalleryThumbnail(uri: Uri) {
        // Reference of the view that holds the gallery thumbnail
        val thumbnail = container.findViewById<ImageButton>(R.id.photo_view_button)

        // Run the operations in the view's thread
        thumbnail.post {

            // Remove thumbnail padding
            thumbnail.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

            // Load thumbnail into circular button using Glide
            Glide.with(thumbnail)
                    .load(uri)
                    .apply(RequestOptions.circleCropTransform())
                    .into(thumbnail)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view as ConstraintLayout
        viewFinder = container.findViewById(R.id.view_finder)

        val context = requireContext()
        outputDirectory = MainActivity.getOutputDirectory(context)

        frameProcessor = FrameProcessor(context, 10_000)
        yuvToRgbConverter = YuvToRgbConverter(context)

        viewFinder.doOnLayout {
            updateCameraUi()
            setUpCamera()
        }
    }

    private val scope = MainScope()
    private var initCameraJob: Job? = null

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        initCameraJob?.cancel()
        initCameraJob = scope.launch {
            cameraProvider = requireContext().getCameraProvider()
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }
            updateCameraSwitchButton()
            bindCameraUseCases()
//            startRec()
        }
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases(maxResolution: Size? = Size(1280, 720)) {
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)

        val rotation = viewFinder.display.rotation

        val cameraProvider = cameraProvider

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        currentMaxResolution = maxResolution

        // Preview
        preview = Preview.Builder()
            .apply {
                maxResolution?.let { setMaxResolution(it) }
            }
            .build()

        videoCapture = VideoCapture.Builder()
//            .setVideoFrameRate(30)
//            .setBitRate(600)
//            .setMaxResolution()
            .build()

        imageAnalysis?.clearAnalyzer()
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(analysisExecutor, imageAnalyzer)
            }

        // ImageCapture
//        imageCapture = ImageCapture.Builder()
//                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
//                // We request aspect ratio but no resolution to match preview config, but letting
//                // CameraX optimize for whatever specific resolution best fits our use cases
//                .setTargetAspectRatio(screenAspectRatio)
//                // Set initial target rotation, we will have to call this again if rotation changes
//                // during the lifecycle of this use case
//                .setTargetRotation(rotation)
//                .build()

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            container.findViewById<CameraInputLayout>(R.id.camera_input).camera = camera

            preview.setSurfaceProvider(viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private val imageAnalyzer = ImageAnalysis.Analyzer { proxy ->
        val frame = proxy.use { it.toBitmap() }
        capturedImage = frame
        frame?.let { frameProcessor.processFrame(it) }
    }
    /**
     *  [androidx.camera.core.impl.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    var recording = false

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {

        // Remove previous UI if any
        container.findViewById<ConstraintLayout>(R.id.camera_ui_container)?.let {
            container.removeView(it)
        }

        // Inflate a new view containing all UI for controlling the camera
        val context = requireContext()
        val controls = View.inflate(context, R.layout.camera_ui_container, container)

        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
            }?.max()?.let {
                setGalleryThumbnail(Uri.fromFile(it))
            }
        }

        // Listener for button used to capture photo
        controls.findViewById<ImageButton>(R.id.camera_capture_button).setOnClickListener {
            if (recording) {
                recording = false
//                videoCapture.stopRecording(true)
                frameProcessor.stop()?.let { storage ->
                    scope.launch {
                        Grabber.process(requireContext(), storage)?.let { video ->
                            openVideo(video)
                        }
                    }
                }
            } else {
                startRec()
            }
        }

        // Setup for button used to switch cameras
        controls.findViewById<ImageButton>(R.id.camera_switch_button).let {

            // Disable the button until the camera is set up
            it.isEnabled = false

            // Listener for button used to switch cameras. Only called if the button is enabled
            it.setOnClickListener {
                lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                // Re-bind use cases to update selected camera
                bindCameraUseCases()
            }
        }

        // Listener for button used to view the most recent photo
        controls.findViewById<ImageButton>(R.id.photo_view_button).setOnClickListener {
            // Only navigate when the gallery has photos
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                Navigation.findNavController(
                        requireActivity(), R.id.fragment_container
                ).navigate(CameraFragmentDirections
                        .actionCameraToGallery(outputDirectory.absolutePath))
            }
        }
    }

    private fun startRec() {
        val context = requireContext()
        val videoFile = createFile(outputDirectory, FILENAME, ".mp4")

        recording = true
        frameProcessor.stop()
        frameProcessor.start("key")
//        val options = VideoCapture.OutputFileOptions.Builder(videoFile).build()
//        videoCapture.startRecording(options, cameraExecutor, object : VideoCapture.OnVideoSavedCallback {
//            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
//                val savedUri = outputFileResults.savedUri ?: Uri.fromFile(videoFile)
//                Log.d(TAG, "Photo capture succeeded: $savedUri")
//
//                openVideo(videoFile)
//            }
//
//            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
//                Log.e("CameraTest", "onCameraError", cause)
//            }
//        })
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        val switchCamerasButton = container.findViewById<ImageButton>(R.id.camera_switch_button)
        try {
            switchCamerasButton.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            switchCamerasButton.isEnabled = false
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun ImageProxy.toBitmap(): Bitmap? {
        val image = image ?: return null
        val resolution = currentMaxResolution
        val maxHeight = resolution?.height
        val maxWidth = resolution?.width
        val imageHeight = image.cropRect.height()
        val imageWidth = image.cropRect.width()
        return when (image.format) {
            ImageFormat.YUV_420_888 -> {
                val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                yuvToRgbConverter.yuvToRgb(image, bitmap)
                bitmap
            }
            ImageFormat.JPEG -> {
                JpegToRgbConverter.jpegToRgb(image)
            }
            else -> {
                Logger.d("CameraFragment", "unsupported format: ${image.format}")
                null
            }
        }?.transform(
            rotation = imageInfo.rotationDegrees,
            scale = if (maxHeight != null && maxWidth != null && (imageHeight > maxHeight || imageWidth > maxWidth)) {
                min(maxHeight.toFloat() / imageHeight, maxWidth.toFloat() / imageWidth)
            } else {
                1f
            }
        )
    }

    private fun openVideo(videoFile: File) {
        val context = requireContext()
        val packageManager = context.packageManager
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", videoFile)
        val intent = Intent()
            .setAction(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/mp4")
            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    companion object {

        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
                File(baseFolder, SimpleDateFormat(format, Locale.US)
                        .format(System.currentTimeMillis()) + extension)
    }
}
