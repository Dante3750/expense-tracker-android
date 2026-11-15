package com.example.expensetracker.ui.scan

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.expensetracker.container
import com.example.expensetracker.data.ocr.ReceiptScan
import com.example.expensetracker.data.ocr.ReceiptTextRecognizer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val isProcessing: Boolean = false,
    /** Non-null once a photo has been read; drives the review sheet. */
    val scan: ReceiptScan? = null,
    /** The candidate the user currently has selected in the review sheet. */
    val selectedAmountMinor: Long? = null,
    val error: String? = null,
)

class ReceiptScanViewModel(private val recognizer: ReceiptTextRecognizer) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /** Called from the camera's background executor; StateFlow.update is thread-safe. */
    fun onPhotoCaptured(bitmap: Bitmap, rotationDegrees: Int) = recognize {
        recognizer.recognize(bitmap, rotationDegrees)
    }

    fun onImagePicked(uri: Uri) = recognize { recognizer.recognize(uri) }

    fun onCaptureStarted() = _uiState.update { it.copy(isProcessing = true, error = null) }

    fun onCaptureFailed(message: String) = _uiState.update { it.copy(isProcessing = false, error = message) }

    fun onCandidateSelected(amountMinor: Long) = _uiState.update { it.copy(selectedAmountMinor = amountMinor) }

    /** "Retake": drop the result and go back to the live preview. */
    fun reset() = _uiState.update { ScanUiState() }

    private fun recognize(block: suspend () -> ReceiptScan) {
        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            try {
                val scan = block()
                _uiState.update {
                    ScanUiState(scan = scan, selectedAmountMinor = scan.amountMinor)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { ScanUiState(error = "Couldn't read that image. Try again with better light.") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ReceiptScanViewModel(container().receiptTextRecognizer) }
        }
    }
}
