package dev.splitthebill.android.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.splitthebill.android.ocr.MlKitOcrEngine
import dev.splitthebill.shared.OcrEngine
import dev.splitthebill.shared.parseReceipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface ReceiptPhase {
    data object Idle : ReceiptPhase

    data object Reading : ReceiptPhase

    data object Ready : ReceiptPhase

    data class Failed(val message: String) : ReceiptPhase
}

class ReceiptScanViewModel : ViewModel() {
    private val ocr: OcrEngine = MlKitOcrEngine()
    private var nextKey = 0
    private var readJob: Job? = null

    var phase: ReceiptPhase by mutableStateOf(ReceiptPhase.Idle)
        private set

    var review: ReviewUiState? by mutableStateOf(null)
        private set

    fun reset() {
        readJob?.cancel()
        phase = ReceiptPhase.Idle
        review = null
    }

    fun read(bytes: ByteArray) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            phase = ReceiptPhase.Reading
            try {
                val parsed = parseReceipt(ocr.recognize(bytes))
                nextKey = parsed.items.size
                review = reviewFromParsed(parsed)
                phase = ReceiptPhase.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                phase = ReceiptPhase.Failed("Couldn't read that photo. Try again, or choose a different one.")
            }
        }
    }

    fun onName(key: String, value: String) = edit(key) { it.copy(name = value) }

    fun onPrice(key: String, value: String) = edit(key) { it.copy(price = value) }

    fun onQuantity(key: String, value: String) = edit(key) { it.copy(quantity = value.filter(Char::isDigit).take(2)) }

    fun delete(key: String) {
        val current = review ?: return
        review = current.copy(items = current.items.filterNot { it.key == key })
    }

    fun addItem() {
        val current = review ?: return
        val key = "item-${nextKey++}"
        review = current.copy(
            items = current.items + ReviewItemUi(key = key, name = "", price = "", quantity = "1"),
        )
    }

    fun onTax(value: String) {
        review = review?.copy(tax = value)
    }

    fun onTip(value: String) {
        review = review?.copy(tip = value)
    }

    private fun edit(key: String, update: (ReviewItemUi) -> ReviewItemUi) {
        val current = review ?: return
        review = current.copy(items = current.items.map { if (it.key == key) update(it) else it })
    }
}
