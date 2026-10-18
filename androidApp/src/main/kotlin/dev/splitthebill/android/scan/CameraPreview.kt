package dev.splitthebill.android.scan

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun CameraPreview(
    onImageCapture: (ImageCapture) -> Unit,
    onUnavailable: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    LaunchedEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        suspendCancellableCoroutine { continuation ->
            future.addListener(
                { if (continuation.isActive) continuation.resume(Unit) },
                ContextCompat.getMainExecutor(context),
            )
        }
        val provider = future.get()
        val preview = Preview.Builder().build().also { useCase ->
            useCase.surfaceProvider = previewView.surfaceProvider
        }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
            onImageCapture(imageCapture)
        } catch (_: Exception) {
            onUnavailable("This device doesn't have a camera ready. Choose a photo instead.")
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            val future = ProcessCameraProvider.getInstance(context)
            if (future.isDone) {
                future.get().unbindAll()
            }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@SuppressLint("UnsafeOptInUsageError")
fun ImageProxy.jpegBytes(): ByteArray? {
    val buffer = planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}

fun imageCaptureErrorMessage(error: ImageCaptureException): String = "Couldn't take the photo. Try again."
