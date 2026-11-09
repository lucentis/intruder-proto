
package fr.lucentis.intruderproto

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CaptureFailure
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class CameraCapture(
    context: Context,
    private val onFinished: (String) -> Unit
) {

    private val appContext = context.applicationContext

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null

    private val finished = AtomicBoolean(false)

    fun start() {
        if (finished.get()) return

        cameraThread = HandlerThread("IntruderCameraThread").apply {
            start()
        }

        cameraHandler = Handler(cameraThread!!.looper)

        try {
            val cameraManager =
                appContext.getSystemService(CameraManager::class.java)

            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            }

            if (cameraId == null) {
                finish("front_camera_unavailable")
                return
            }

            val characteristics =
                cameraManager.getCameraCharacteristics(cameraId)

            val map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )

            val sizes = map?.getOutputSizes(ImageFormat.JPEG)

            val size = sizes
                ?.filter { it.width <= 1920 && it.height <= 1920 }
                ?.maxByOrNull { it.width.toLong() * it.height }
                ?: sizes?.maxByOrNull {
                    it.width.toLong() * it.height
                }

            if (size == null) {
                finish("no_supported_image_size")
                return
            }

            imageReader = ImageReader.newInstance(
                size.width,
                size.height,
                ImageFormat.JPEG,
                2
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                    ?: return@setOnImageAvailableListener

                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    val directory = File(appContext.filesDir, "captures")
                    directory.mkdirs()

                    val file = File(
                        directory,
                        "capture_${System.currentTimeMillis()}.jpg"
                    )

                    FileOutputStream(file).use { output ->
                        output.write(bytes)
                    }

                    finish("success:${file.name}")
                } catch (exception: Exception) {
                    finish("save_failed:${exception.javaClass.simpleName}")
                } finally {
                    image.close()
                }
            }, cameraHandler)

            cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {

                    override fun onOpened(camera: CameraDevice) {
                        if (finished.get()) {
                            camera.close()
                            return
                        }

                        cameraDevice = camera
                        createCaptureSession(camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        finish("camera_disconnected")
                    }

                    override fun onError(
                        camera: CameraDevice,
                        error: Int
                    ) {
                        camera.close()
                        finish("camera_error:$error")
                    }
                },
                cameraHandler
            )

        } catch (exception: SecurityException) {
            finish("camera_security_exception")
        } catch (exception: Exception) {
            finish("camera_open_failed:${exception.javaClass.simpleName}")
        }
    }

    fun cancel() {
        finish("capture_cancelled")
    }

    private fun createCaptureSession(camera: CameraDevice) {
        if (finished.get()) return

        val reader = imageReader ?: run {
            finish("image_reader_unavailable")
            return
        }

        try {
            camera.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(
                        session: CameraCaptureSession
                    ) {
                        if (finished.get()) {
                            session.close()
                            return
                        }

                        captureSession = session

                        try {
                            val request = camera.createCaptureRequest(
                                CameraDevice.TEMPLATE_STILL_CAPTURE
                            ).apply {
                                addTarget(reader.surface)
                                set(
                                    CaptureRequest.CONTROL_MODE,
                                    CaptureRequest.CONTROL_MODE_AUTO
                                )
                            }

                            session.capture(
                                request.build(),
                                object : CaptureCallback() {

                                    override fun onCaptureFailed(
                                        session: CameraCaptureSession,
                                        request: CaptureRequest,
                                        failure: CaptureFailure
                                    ) {
                                        finish("capture_failed:${failure.reason}")
                                    }
                                },
                                cameraHandler
                            )

                        } catch (exception: Exception) {
                            finish(
                                "capture_request_failed:${exception.javaClass.simpleName}"
                            )
                        }
                    }

                    override fun onConfigureFailed(
                        session: CameraCaptureSession
                    ) {
                        finish("capture_session_configuration_failed")
                    }
                },
                cameraHandler
            )
        } catch (exception: Exception) {
            finish("capture_session_failed:${exception.javaClass.simpleName}")
        }
    }

    private fun finish(result: String) {
        if (!finished.compareAndSet(false, true)) return

        captureSession?.close()
        captureSession = null

        cameraDevice?.close()
        cameraDevice = null

        imageReader?.close()
        imageReader = null

        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null

        onFinished(result)
    }
}
