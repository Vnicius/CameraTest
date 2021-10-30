package io.vnicius.github.cameraxtest

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recorder.DEFAULT_QUALITY_SELECTOR
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.vnicius.github.cameraxtest.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var videoCapture: VideoCapture<Recorder>? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        binding.cameraCaptureButton.setOnClickListener {
            recordVideo()
        }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun recordVideo() {
        val videoCapture = videoCapture ?: return
        val videoRecorderEventListener =
            androidx.core.util.Consumer<VideoRecordEvent> { event ->
                when (event) {
                    is VideoRecordEvent.Start -> Log.d(TAG, "VIDEO START")
                    is VideoRecordEvent.Pause -> Log.d(TAG, "VIDEO PAUSE")
                    is VideoRecordEvent.Finalize -> Log.d(TAG, "VIDEO FINALIZE")
                    is VideoRecordEvent.Resume -> Log.d(TAG, "VIDEO RESUME")
                    is VideoRecordEvent.Status -> {
                        Log.d(TAG, "STATUS ${event.recordingStats}")
                    }
                }
            }
        val pendingVideoRecord = videoCapture.output.prepareRecording(this, getOutput())
            .withEventListener(ContextCompat.getMainExecutor(this), videoRecorderEventListener)

        // Enable video if has permission
        pendingVideoRecord.apply {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                withAudioEnabled()
            }
        }

        val activeRecording = pendingVideoRecord.start()

        // record 10 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            activeRecording.stop()
        }, 10000L)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
            val recorder = Recorder.Builder().setQualitySelector(DEFAULT_QUALITY_SELECTOR).build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } catch (e: Exception) {
                Log.e(TAG, "cameraError", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun getOutput() = MediaStoreOutputOptions.Builder(
        contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    )
        .setContentValues(ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                SimpleDateFormat(
                    FILENAME_FORMAT,
                    Locale.US
                ).format(System.currentTimeMillis()) + ".mp4"
            )
        })
        .build()

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}
