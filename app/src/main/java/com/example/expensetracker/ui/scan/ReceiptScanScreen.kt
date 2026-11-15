package com.example.expensetracker.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensetracker.data.ocr.ReceiptScan
import com.example.expensetracker.util.Money
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Photo -> on-device OCR -> review sheet -> result.
 *
 * [onResult] hands back the amount the user confirmed (and a merchant guess). Nothing is saved
 * here and the photo itself is never written to disk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScanScreen(
    viewModel: ReceiptScanViewModel,
    onResult: (amountMinor: Long, merchant: String?) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // The system photo picker needs no storage permission.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }
    val pickFromGallery = {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (hasCameraPermission) {
                CameraCapture(
                    enabled = !state.isProcessing && state.scan == null,
                    onCaptureStarted = viewModel::onCaptureStarted,
                    onCaptured = viewModel::onPhotoCaptured,
                    onCaptureFailed = viewModel::onCaptureFailed,
                    onPickFromGallery = pickFromGallery,
                )
            } else {
                PermissionMessage(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onPickFromGallery = pickFromGallery,
                )
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Text("Reading receipt…", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }

    state.scan?.let { scan ->
        ModalBottomSheet(onDismissRequest = viewModel::reset) {
            ScanReview(
                scan = scan,
                selectedAmountMinor = state.selectedAmountMinor,
                onCandidateSelected = viewModel::onCandidateSelected,
                onUse = { state.selectedAmountMinor?.let { onResult(it, scan.merchant) } },
                onRetake = viewModel::reset,
            )
        }
    }
}

@Composable
private fun CameraCapture(
    enabled: Boolean,
    onCaptureStarted: () -> Unit,
    onCaptured: (Bitmap, Int) -> Unit,
    onCaptureFailed: (String) -> Unit,
    onPickFromGallery: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // CameraController owns Preview + ImageCapture and wires up tap-to-focus and pinch-to-zoom.
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY // sharper text beats shutter speed
        }
    }
    val captureExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.unbind()
            captureExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = cameraController
                }
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Fit the whole receipt in the frame, with the total clearly visible.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            ShutterButton(
                enabled = enabled,
                onClick = {
                    onCaptureStarted()
                    cameraController.takePicture(
                        captureExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                // Runs on captureExecutor, so decoding the JPEG stays off the main thread.
                                val rotation = image.imageInfo.rotationDegrees
                                val bitmap = try {
                                    image.toBitmap()
                                } catch (e: Exception) {
                                    null
                                } finally {
                                    image.close()
                                }
                                if (bitmap != null) {
                                    onCaptured(bitmap, rotation)
                                } else {
                                    onCaptureFailed("Couldn't read the photo. Please try again.")
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onCaptureFailed("Couldn't take the photo. Please try again.")
                            }
                        },
                    )
                },
            )
            FilledTonalButton(onClick = onPickFromGallery, enabled = enabled) {
                Text("Choose a photo instead")
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClickLabel = "Take photo", role = Role.Button, onClick = onClick)
            .padding(9.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f)),
    )
}

@Composable
private fun PermissionMessage(onRequestPermission: () -> Unit, onPickFromGallery: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Camera access is needed to photograph a receipt. Photos are read on this device and never uploaded.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequestPermission) { Text("Allow camera") }
        OutlinedButton(onClick = onPickFromGallery) { Text("Choose a photo instead") }
        Text(
            "If the camera prompt no longer appears, turn the permission on in system Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ScanReview(
    scan: ReceiptScan,
    selectedAmountMinor: Long?,
    onCandidateSelected: (Long) -> Unit,
    onUse: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectedAmountMinor == null) {
            Text("No amount found", style = MaterialTheme.typography.titleLarge)
            Text(
                "Try again with the receipt flat, in good light, and the total in the frame.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Total found", style = MaterialTheme.typography.labelLarge)
            Text(Money.format(selectedAmountMinor), style = MaterialTheme.typography.displaySmall)
            scan.merchant?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (scan.candidatesMinor.size > 1) {
                Text("Not right? Other amounts on this receipt:", style = MaterialTheme.typography.bodyMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(scan.candidatesMinor) { candidate ->
                        FilterChip(
                            selected = candidate == selectedAmountMinor,
                            onClick = { onCandidateSelected(candidate) },
                            label = { Text(Money.format(candidate)) },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Retake") }
            Button(
                onClick = onUse,
                enabled = selectedAmountMinor != null,
                modifier = Modifier.weight(1f),
            ) { Text("Use amount") }
        }
    }
}
