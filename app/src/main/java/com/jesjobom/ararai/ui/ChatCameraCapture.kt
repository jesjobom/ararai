package com.jesjobom.ararai.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jesjobom.ararai.R

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun ChatCameraCaptureDialog(
    mediaServices: ChatMediaServices,
    onCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
    automaticCaptureRequestId: Long? = null,
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val providerFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var previewReady by remember { mutableStateOf(false) }
    val cameraUnavailable = stringResource(R.string.chat_camera_unavailable)
    val captureFailed = stringResource(R.string.chat_camera_capture_failed)

    fun capturePhoto() {
        val capture = imageCapture ?: return
        if (capturing) return
        val target = mediaServices.cameraFileFactory.create()
        capturing = true
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(target).build(),
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onCaptured(Uri.fromFile(target))
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    target.delete()
                    onError(exception.message ?: captureFailed)
                }
            },
        )
    }

    LaunchedEffect(automaticCaptureRequestId, previewReady) {
        if (automaticCaptureRequestId != null && previewReady) capturePhoto()
    }

    DisposableEffect(providerFuture, lifecycleOwner) {
        val listener = Runnable {
            runCatching {
                val provider = providerFuture.get()
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                imageCapture = capture
                provider.unbindAll()
            }.onFailure { onError(it.message ?: cameraUnavailable) }
        }
        providerFuture.addListener(listener, executor)
        onDispose {
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!capturing) onDismiss() },
        title = { Text(stringResource(R.string.chat_take_photo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AndroidView(
                    factory = { previewContext ->
                        PreviewView(previewContext).apply {
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            providerFuture.addListener(
                                {
                                    runCatching {
                                        val provider = providerFuture.get()
                                        val preview =
                                            Preview.Builder().build().also {
                                                it.surfaceProvider = surfaceProvider
                                            }
                                        val capture =
                                            imageCapture
                                                ?: ImageCapture.Builder().build().also {
                                                    imageCapture = it
                                                }
                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture,
                                        )
                                        if (!previewReady) {
                                            previewReady = true
                                            onReady()
                                        }
                                    }.onFailure {
                                        onError(
                                            it.message ?: cameraUnavailable,
                                        )
                                    }
                                },
                                executor,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                )
                Button(
                    onClick = ::capturePhoto,
                    enabled = imageCapture != null && previewReady && !capturing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text(stringResource(R.string.chat_capture_photo))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !capturing) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
