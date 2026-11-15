package com.example.expensetracker.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Runs ML Kit's on-device text recogniser and hands the result to [ReceiptParser].
 *
 * The bundled Latin model (com.google.mlkit:text-recognition) is packaged inside the APK, so
 * this works in airplane mode and nothing about the receipt leaves the device.
 */
class ReceiptTextRecognizer(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** For a frame captured with CameraX. [rotationDegrees] comes from ImageProxy.imageInfo. */
    suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): ReceiptScan =
        process(InputImage.fromBitmap(bitmap, rotationDegrees))

    /** For a photo picked from the gallery; EXIF rotation is handled by ML Kit. */
    suspend fun recognize(uri: Uri): ReceiptScan =
        process(withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) })

    private suspend fun process(image: InputImage): ReceiptScan {
        val text = recognizer.process(image).await()
        val lines = text.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
        return withContext(Dispatchers.Default) { ReceiptParser.parse(lines) }
    }
}

/** Minimal Task -> coroutine bridge (avoids pulling in kotlinx-coroutines-play-services). */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
