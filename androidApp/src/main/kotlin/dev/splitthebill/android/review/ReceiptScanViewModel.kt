package dev.splitthebill.android.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.splitthebill.android.data.BillGraph
import dev.splitthebill.android.ocr.MlKitOcrEngine
import dev.splitthebill.shared.BillDraft
import dev.splitthebill.shared.LineItemId
import dev.splitthebill.shared.OcrEngine
import dev.splitthebill.shared.PersonId
import dev.splitthebill.shared.TipChoice
import dev.splitthebill.shared.addPerson
import dev.splitthebill.shared.assignEveryone
import dev.splitthebill.shared.assignmentError
import dev.splitthebill.shared.parseReceipt
import dev.splitthebill.shared.removePerson
import dev.splitthebill.shared.renamePerson
import dev.splitthebill.shared.splitRemainingEvenly
import dev.splitthebill.shared.toRecorded
import dev.splitthebill.shared.toggleAssignment
import kotlin.time.Clock
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
    private var reviewTipSnapshot: String? = null

    var phase: ReceiptPhase by mutableStateOf(ReceiptPhase.Idle)
        private set

    var review: ReviewUiState? by mutableStateOf(null)
        private set

    var reviewNotice: String? by mutableStateOf(null)
        private set

    var draft: BillDraft? by mutableStateOf(null)
        private set

    var personName: String by mutableStateOf("")
        private set

    var editingPersonId: PersonId? by mutableStateOf(null)
        private set

    var customPercent: String by mutableStateOf("")
        private set

    var fixedTip: String by mutableStateOf("")
        private set

    fun reset() {
        readJob?.cancel()
        phase = ReceiptPhase.Idle
        review = null
        reviewNotice = null
        draft = null
        personName = ""
        editingPersonId = null
        customPercent = ""
        fixedTip = ""
        reviewTipSnapshot = null
    }

    fun read(bytes: ByteArray) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            phase = ReceiptPhase.Reading
            try {
                val parsed = parseReceipt(ocr.recognize(bytes))
                nextKey = parsed.items.size
                review = reviewFromParsed(parsed)
                reviewNotice = null
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
        reviewNotice = null
    }

    fun addItem() {
        val current = review ?: return
        val key = "item-${nextKey++}"
        review = current.copy(
            items = current.items + ReviewItemUi(key = key, name = "", price = "", quantity = "1"),
        )
        reviewNotice = null
    }

    fun onTax(value: String) {
        review = review?.copy(tax = value)
        reviewNotice = null
    }

    fun onTip(value: String) {
        review = review?.copy(tip = value)
        reviewNotice = null
    }

    fun openPeople(): Boolean {
        val current = review ?: run {
            reviewNotice = "Add at least one item with a name and price."
            return false
        }
        val keepTip = draft != null && current.tip == reviewTipSnapshot
        return when (val opened = openDraft(draft, current, Clock.System.now(), keepTip)) {
            is DraftOpen.Blocked -> {
                reviewNotice = opened.message
                false
            }
            is DraftOpen.Ready -> {
                draft = opened.draft
                reviewNotice = null
                reviewTipSnapshot = current.tip
                true
            }
        }
    }

    fun onPersonName(value: String) {
        personName = value.take(40)
    }

    fun submitPerson() {
        val current = draft ?: return
        val editing = editingPersonId
        draft = if (editing == null) addPerson(current, personName) else renamePerson(current, editing, personName)
        if (personName.isNotBlank()) {
            personName = ""
            editingPersonId = null
        }
    }

    fun selectPerson(id: PersonId) {
        val person = draft?.people?.find { it.id == id } ?: return
        editingPersonId = id
        personName = person.name
    }

    fun removePersonFromBill(id: PersonId) {
        draft = draft?.let { removePerson(it, id) }
        if (editingPersonId == id) {
            editingPersonId = null
            personName = ""
        }
    }

    fun togglePerson(itemId: LineItemId, personId: PersonId) {
        draft = draft?.let { toggleAssignment(it, itemId, personId) }
    }

    fun assignItemToEveryone(itemId: LineItemId) {
        draft = draft?.let { assignEveryone(it, itemId) }
    }

    fun splitRemaining() {
        draft = draft?.let { splitRemainingEvenly(it) }
    }

    fun prepareTipFields() {
        when (val choice = draft?.tipChoice) {
            is TipChoice.Percent -> {
                customPercent = if (choice.percent in listOf(15, 18, 20)) "" else choice.percent.toString()
                fixedTip = ""
            }
            is TipChoice.Fixed -> {
                customPercent = ""
                fixedTip = choice.amount.formatPlain()
            }
            else -> {
                customPercent = ""
                fixedTip = ""
            }
        }
    }

    fun choosePercent(percent: Int) {
        customPercent = ""
        fixedTip = ""
        draft = draft?.copy(tipChoice = TipChoice.Percent(percent))
    }

    fun onCustomPercent(value: String) {
        val digits = value.filter(Char::isDigit).take(3)
        customPercent = digits
        fixedTip = ""
        val percent = digits.toIntOrNull()
        draft = draft?.copy(
            tipChoice = if (percent != null && percent in 1..100) TipChoice.Percent(percent) else TipChoice.Unset,
        )
    }

    fun onFixedTip(value: String) {
        fixedTip = value.take(8)
        customPercent = ""
        val amount = parseDollars(value)
        draft = draft?.copy(
            tipChoice = when {
                value.isBlank() -> TipChoice.Unset
                amount == null || amount.amount < 0 -> TipChoice.Unset
                else -> TipChoice.Fixed(amount)
            },
        )
    }

    fun chooseNoTip() {
        customPercent = ""
        fixedTip = ""
        draft = draft?.copy(tipChoice = TipChoice.None)
    }

    fun saveBill() {
        val current = draft ?: return
        if (current.tipChoice == TipChoice.Unset || assignmentError(current) != null) return
        val recorded = current.toRecorded()
        viewModelScope.launch {
            BillGraph.store.save(recorded)
        }
    }

    private fun edit(key: String, update: (ReviewItemUi) -> ReviewItemUi) {
        val current = review ?: return
        review = current.copy(items = current.items.map { if (it.key == key) update(it) else it })
        reviewNotice = null
    }
}
