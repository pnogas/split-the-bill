package dev.splitthebill.android

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.splitthebill.android.data.ExampleBillRepository
import dev.splitthebill.android.home.HomeScreen
import dev.splitthebill.android.home.HomeViewModel
import dev.splitthebill.android.stub.StubScreen
import dev.splitthebill.shared.BillId
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.DrawerValue

private const val Home = "home"
private const val Scan = "scan"
private const val Profile = "profile"
private const val Friends = "friends"
private const val Settings = "settings"
private const val Summary = "summary"

@Composable
fun SplitTheBillApp() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var route by rememberSaveable { mutableStateOf(Home) }
    var summaryBillId by rememberSaveable { mutableStateOf<String?>(null) }
    val repository = ExampleBillRepository()

    fun go(next: String) {
        route = next
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = route == Home,
        drawerContent = {
            // PROPOSED contents. Not yet approved.
            ModalDrawerSheet {
                Text(
                    text = "Split the Bill",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                NavigationDrawerItem(
                    label = { Text("Bills") },
                    selected = route == Home || route == Scan || route == Summary,
                    onClick = { go(Home) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Friends") },
                    selected = route == Friends,
                    onClick = { go(Friends) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = route == Settings,
                    onClick = { go(Settings) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        when (route) {
            Scan -> StubScreen(
                title = "Scan",
                body = "Snap a receipt to start. Next you'll review the items, add people, assign what they had, and see the totals.",
                onBack = { go(Home) },
            )
            Profile -> StubScreen(
                title = "Profile",
                body = "Your profile and settings will live here.",
                onBack = { go(Home) },
            )
            Friends -> StubScreen(
                title = "Friends",
                body = "People you split with will show up here.",
                onBack = { go(Home) },
            )
            Settings -> StubScreen(
                title = "Settings",
                body = "App settings will live here.",
                onBack = { go(Home) },
            )
            Summary -> {
                val recorded = repository.bills().find { it.bill.id.value == summaryBillId }
                val merchant = recorded?.bill?.merchant ?: "This bill"
                StubScreen(
                    title = "Summary",
                    body = "$merchant. Totals for this bill will show up here.",
                    onBack = { go(Home) },
                )
            }
            else -> {
                val viewModel: HomeViewModel = viewModel()
                HomeScreen(
                    state = viewModel.uiState,
                    onMenu = { scope.launch { drawerState.open() } },
                    onProfile = { go(Profile) },
                    onBill = { id: BillId ->
                        summaryBillId = id.value
                        go(Summary)
                    },
                    onNewBill = { go(Scan) },
                )
            }
        }
    }
}
