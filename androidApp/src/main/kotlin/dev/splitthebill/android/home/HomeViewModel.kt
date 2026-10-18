package dev.splitthebill.android.home

import androidx.lifecycle.ViewModel
import dev.splitthebill.android.data.BillRepository
import dev.splitthebill.android.data.ExampleBillRepository

class HomeViewModel : ViewModel() {
    private val repository: BillRepository = ExampleBillRepository()
    val uiState: HomeUiState = homeUiState(repository)
}
