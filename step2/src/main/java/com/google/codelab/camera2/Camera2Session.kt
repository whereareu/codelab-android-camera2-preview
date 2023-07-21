package com.google.codelab.camera2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.LogPrinter
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * @Author: Liao
 * @Date: 2023/7/21
 * @Email: xibo0601@gmail.com
 */
class Camera2Session(
    private val activity: AppCompatActivity,
    private val textureView: TextureView,
    private val requestPermission: () -> Unit
): DefaultLifecycleObserver, View.OnAttachStateChangeListener {

    /** Main Looper*/
    private val mainLooperHandler = Handler(Looper.getMainLooper())

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("MyCameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** hardcoded back camera*/
    var cameraID = "0"

    /** [DisplayManager] to listen to display changes */
    private val displayManager: DisplayManager by lazy {
        activity.applicationContext.getSystemService(AppCompatActivity.DISPLAY_SERVICE) as DisplayManager
    }

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        activity.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** Keep track of display rotations*/
    private var displayRotation = 0

    /** The [CameraDevice] that will be opened in this Activity */
    var cameraDevice: CameraDevice? = null

    /** [Surface] target of the CameraCaptureRequest*/
    private var surface: Surface? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val difference = displayManager.getDisplay(displayId).rotation - displayRotation
            displayRotation = displayManager.getDisplay(displayId).rotation

            if (difference == 2 || difference == -2) {
                createCaptureSession()
            }
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            try {
                mainLooperHandler.post {
                    if (textureView.isAvailable) {
                        createCaptureSession()
                    }
                    textureView.surfaceTextureListener = surfaceTextureListener
                }
            } catch (t: Throwable) {
                closeCamera()
                release()
                Log.e(TAG, "Failed to initialize camera.", t)
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice = camera
            closeCamera()
            release()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice = camera
            Log.e(TAG, "on Error: $error")
        }
    }

    private val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            try {
                val captureRequest = cameraDevice?.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
                )
                captureRequest?.addTarget(surface!!)
                cameraCaptureSession.setRepeatingRequest(
                    captureRequest?.build()!!, null, cameraHandler
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to open camera preview.", t)
            }
        }

        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
            Log.e(TAG, "Failed to configure camera.")
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera() = activity.lifecycleScope.launch(Dispatchers.Main) {
        cameraManager.openCamera(cameraID, cameraStateCallback, cameraHandler)
    }

    fun closeCamera() = activity.lifecycleScope.launch(Dispatchers.Main) {
        cameraDevice?.close()
    }

    fun switchCamera() {
        cameraID = if (cameraID == "0") {
            "1"
        } else {
            "0"
        }
        closeCamera()
        openCamera()
    }

    /**
     * Before creating the [CameraCaptureSession] we apply a transformation to the TextureView to
     * avoid distortion in the preview
     */
    private fun createCaptureSession() {
        if (cameraDevice == null || !textureView.isAvailable) return

        val transformedTexture = CameraUtils.buildTargetTexture(
            textureView, cameraManager.getCameraCharacteristics(cameraID),
            displayManager.getDisplay(Display.DEFAULT_DISPLAY).rotation
        )

        this.surface = Surface(transformedTexture)
        try {
            cameraDevice?.createCaptureSession(
                listOf(surface), sessionStateCallback, cameraHandler
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create session.", t)
        }
    }

    /**
     * Every time the size of the TextureSize changes,
     * we calculate the scale and create a new session
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            createCaptureSession()
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            createCaptureSession()
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            requestPermission.invoke()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        closeCamera()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        release()
    }

    override fun onViewAttachedToWindow(v: View) {
        registerDisplayL()
    }

    override fun onViewDetachedFromWindow(v: View) {
        unRegisterDisplayL()
    }

    private fun release() {
        try {
            surface?.release()
            cameraThread.quitSafely()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to release resources.", t)
        }
    }

    private fun registerDisplayL() {
        displayManager.registerDisplayListener(displayListener, mainLooperHandler)
    }

    private fun unRegisterDisplayL() {
        displayManager.unregisterDisplayListener(displayListener)
    }

    companion object {

        fun newInstance(
            activity: AppCompatActivity,
            textureView: TextureView,
            requestPermission: () -> Unit
        ): Camera2Session {
            return Camera2Session(
                activity,
                textureView,
                requestPermission
            ).apply {
                activity.lifecycle.addObserver(this)
                activity.window.decorView.addOnAttachStateChangeListener(this)
            }

        }
    }
}