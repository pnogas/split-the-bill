package dev.splitthebill.android.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.splitthebill.android.data.BillGraph
import dev.splitthebill.android.data.Viewer
import dev.splitthebill.shared.BillOwner
import dev.splitthebill.shared.RecordedBill
import kotlinx.coroutines.launch

/** The person who paid. Matches [BillOwner], which is on every saved bill. */
val OwnerViewer = Viewer(
    id = BillOwner.id,
    name = BillOwner.name,
    initials = "ME",
)

class HomeViewModel : ViewModel() {
    var uiState: HomeUiState by mutableStateOf(
        homeUiState(OwnerViewer, BillGraph.store.bills.value, exampleData = false),
    )
        private set

    init {
        viewModelScope.launch {
            BillGraph.store.bills.collect { bills ->
                uiState = homeUiState(OwnerViewer, bills, exampleData = false)
            }
        }
    }

    fun recorded(id: String): RecordedBill? = BillGraph.store.bills.value.find { it.bill.id.value == id }
}
