package dev.splitthebill.android.scan

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.splitthebill.android.theme.SplitTheBillTheme

enum class CameraAccess {
    Granted,
    NeedsRequest,
    Denied,
    Blocked,
}

@Composable
fun ScanRoute(
    reading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onPhoto: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var access by remember { mutableStateOf(cameraAccess(context, asked)) }
    var captureMessage by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        asked = true
        access = cameraAccess(context, asked = true)
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            captureMessage = "Couldn't open that photo."
        } else {
            captureMessage = null
            onPhoto(bytes)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, asked) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = cameraAccess(context, asked)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ScanScreen(
        access = access,
        reading = reading,
        message = captureMessage ?: errorMessage,
        onBack = onBack,
        onAllowCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onOpenSettings = { context.startActivity(settingsIntent(context)) },
        onChoosePhoto = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onCapture = {
            val capture = imageCapture
            if (capture == null) {
                captureMessage = "The camera isn't ready yet."
                return@ScanScreen
            }
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = image.jpegBytes()
                        image.close()
                        if (bytes == null) {
                            captureMessage = "Couldn't save that photo. Try again."
                        } else {
                            captureMessage = null
                            onPhoto(bytes)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        captureMessage = imageCaptureErrorMessage(exception)
                    }
                },
            )
        },
        preview = {
            CameraPreview(
                onImageCapture = { imageCapture = it },
                onUnavailable = { captureMessage = it },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    access: CameraAccess,
    reading: Boolean,
    message: String?,
    onBack: () -> Unit,
    onAllowCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onChoosePhoto: () -> Unit,
    onCapture: () -> Unit,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Scan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (access == CameraAccess.Granted) {
                CameraReady(
                    message = message,
                    onChoosePhoto = onChoosePhoto,
                    onCapture = onCapture,
                    preview = preview,
                )
            } else {
                PermissionPrompt(
                    access = access,
                    message = message,
                    onAllowCamera = onAllowCamera,
                    onOpenSettings = onOpenSettings,
                    onChoosePhoto = onChoosePhoto,
                )
            }
            if (reading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scheme.scrim.copy(alpha = 0.45f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = scheme.primary)
                    Text(
                        text = "Reading the receipt…",
                        modifier = Modifier.padding(top = 16.dp),
                        color = scheme.surface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraReady(
    message: String?,
    onChoosePhoto: () -> Unit,
    onCapture: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            preview()
        }
        Surface(color = scheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (message != null) {
                    Text(
                        text = message,
                        color = scheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onChoosePhoto) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Choose photo")
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Take photo"
                            }
                            .padding(0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = onCapture, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    access: CameraAccess,
    message: String?,
    onAllowCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onChoosePhoto: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val blocked = access == CameraAccess.Blocked
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = if (access == CameraAccess.NeedsRequest) "Scan a receipt" else "Camera access is off",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = when (access) {
                CameraAccess.NeedsRequest ->
                    "Allow the camera to photograph a receipt, or choose a photo you already have."
                CameraAccess.Denied ->
                    "The camera permission was denied. You can allow it and try again, or choose a photo."
                CameraAccess.Blocked ->
                    "Camera permission is turned off for Split the Bill. Enable it in Settings, or choose a photo."
                CameraAccess.Granted -> ""
            },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                modifier = Modifier.padding(top = 12.dp),
                color = scheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = if (blocked) onOpenSettings else onAllowCamera,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Text(
                when (access) {
                    CameraAccess.NeedsRequest -> "Allow camera"
                    CameraAccess.Denied -> "Try again"
                    CameraAccess.Blocked -> "Open Settings"
                    CameraAccess.Granted -> "Allow camera"
                },
            )
        }
        TextButton(onClick = onChoosePhoto, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Choose a photo")
        }
    }
}

@Composable
fun ViewfinderPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.inverseSurface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Camera preview",
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

internal fun cameraAccess(context: Context, asked: Boolean): CameraAccess {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
    if (granted) return CameraAccess.Granted
    val activity = context.findActivity()
    val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.CAMERA,
    )
    return when {
        canAskAgain -> CameraAccess.Denied
        asked -> CameraAccess.Blocked
        else -> CameraAccess.NeedsRequest
    }
}

private fun settingsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(name = "Camera permission", widthDp = 390, heightDp = 844)
@Composable
private fun ScanPermissionPreview() {
    SplitTheBillTheme {
        ScanScreen(
            access = CameraAccess.NeedsRequest,
            reading = false,
            message = null,
            onBack = {},
            onAllowCamera = {},
            onOpenSettings = {},
            onChoosePhoto = {},
            onCapture = {},
            preview = {},
        )
    }
}

@Preview(name = "Camera blocked", widthDp = 390, heightDp = 844)
@Composable
private fun ScanBlockedPreview() {
    SplitTheBillTheme {
        ScanScreen(
            access = CameraAccess.Blocked,
            reading = false,
            message = null,
            onBack = {},
            onAllowCamera = {},
            onOpenSettings = {},
            onChoosePhoto = {},
            onCapture = {},
            preview = {},
        )
    }
}

@Preview(name = "Camera ready", widthDp = 390, heightDp = 844)
@Composable
private fun ScanReadyPreview() {
    SplitTheBillTheme {
        ScanScreen(
            access = CameraAccess.Granted,
            reading = false,
            message = null,
            onBack = {},
            onAllowCamera = {},
            onOpenSettings = {},
            onChoosePhoto = {},
            onCapture = {},
            preview = { ViewfinderPlaceholder() },
        )
    }
}
