package com.example.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.permissions.PermissionHelper
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary

/**
 * In-app Live CameraX Viewfinder & Frame Capture card.
 * Requests CAMERA runtime permission via Jetpack Compose contracts before binding CameraX,
 * supports front/back lens switching and torch, and returns the captured rotated [Bitmap].
 */
@Composable
fun JarvisCameraCaptureCard(
    onCapturedBitmap: (Bitmap) -> Unit,
    onPermissionUpdated: () -> Unit = {},
    captureButtonLabel: String = "Snap & Analyze with Gemini",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCamPermission by remember { mutableStateOf(PermissionHelper.hasCameraPermission(context)) }
    var previewActive by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCamPermission = granted
        onPermissionUpdated()
        if (granted) {
            previewActive = true
            cameraError = null
        } else {
            cameraError = "Camera permission denied. Grant CAMERA access to stream live vision."
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, if (previewActive) JarvisCyan else JarvisBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = JarvisCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live JARVIS Camera Vision",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary
                    )
                    Text(
                        text = if (hasCamPermission) "Camera access granted (CameraX)" else "Camera permission required for live viewfinder",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasCamPermission) JarvisEmerald else JarvisGold
                    )
                }
                if (previewActive) {
                    IconButton(
                        onClick = { previewActive = false },
                        modifier = Modifier.size(44.dp).testTag("camera_close_preview_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close live camera", tint = JarvisTextMuted)
                    }
                }
            }

            if (!hasCamPermission) {
                Button(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("camera_grant_permission_button")
                ) {
                    Text("Grant Camera Access", fontWeight = FontWeight.Bold)
                }
            } else if (!previewActive) {
                Button(
                    onClick = {
                        cameraError = null
                        previewActive = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("camera_open_live_preview_button")
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Live Camera Viewfinder", fontWeight = FontWeight.Bold)
                }
            } else {
                val previewView = remember { PreviewView(context) }

                DisposableEffect(lifecycleOwner, useFrontCamera, previewActive) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    val executor = ContextCompat.getMainExecutor(context)
                    var providerRef: ProcessCameraProvider? = null

                    cameraProviderFuture.addListener({
                        runCatching {
                            val provider = cameraProviderFuture.get()
                            providerRef = provider
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            val selector = if (useFrontCamera) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }
                            provider.unbindAll()
                            val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                            boundCamera = cam
                            imageCapture = capture
                            if (!useFrontCamera && torchEnabled) {
                                cam.cameraControl.enableTorch(true)
                            }
                        }.onFailure { err ->
                            cameraError = err.localizedMessage ?: "Unable to start CameraX preview."
                        }
                    }, executor)

                    onDispose {
                        providerRef?.unbindAll()
                        boundCamera = null
                        imageCapture = null
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!useFrontCamera) {
                            Surface(
                                color = JarvisSurface.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val next = !torchEnabled
                                        torchEnabled = next
                                        boundCamera?.cameraControl?.enableTorch(next)
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.FlashlightOn,
                                        contentDescription = "Toggle camera flash",
                                        tint = if (torchEnabled) JarvisGold else JarvisTextPrimary
                                    )
                                }
                            }
                        }
                        Surface(
                            color = JarvisSurface.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            IconButton(
                                onClick = { useFrontCamera = !useFrontCamera },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Cameraswitch,
                                    contentDescription = "Switch front/back camera",
                                    tint = JarvisCyan
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val capture = imageCapture ?: return@Button
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        try {
                                            val raw = image.toBitmap()
                                            val rotated = rotateBitmap(raw, image.imageInfo.rotationDegrees)
                                            onCapturedBitmap(rotated)
                                        } finally {
                                            image.close()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        cameraError = exception.localizedMessage ?: "Capture failed."
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("camera_capture_frame_button")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(captureButtonLabel, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { previewActive = false },
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Close", color = JarvisTextMuted)
                    }
                }
            }

            cameraError?.let { err ->
                Surface(
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisAlertRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        color = JarvisAlertRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return source
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
