package dev.splitthebill.android

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.splitthebill.android.flow.AddPeopleScreen
import dev.splitthebill.android.flow.AssignScreen
import dev.splitthebill.android.flow.TipScreen
import dev.splitthebill.android.flow.TotalsScreen
import dev.splitthebill.android.flow.shareBill
import dev.splitthebill.android.home.HomeScreen
import dev.splitthebill.android.home.HomeViewModel
import dev.splitthebill.android.review.ReceiptPhase
import dev.splitthebill.android.review.ReceiptScanViewModel
import dev.splitthebill.android.review.ReviewScreen
import dev.splitthebill.android.scan.ScanRoute
import dev.splitthebill.android.stub.StubScreen
import dev.splitthebill.shared.BillId
import dev.splitthebill.shared.SplitResult
import dev.splitthebill.shared.assignmentMessage
import dev.splitthebill.shared.shareSummary
import dev.splitthebill.shared.sharesAddUp
import dev.splitthebill.shared.split
import dev.splitthebill.shared.toBill
import kotlinx.coroutines.launch

private const val Home = "home"
private const val Scan = "scan"
private const val Profile = "profile"
private const val Friends = "friends"
private const val Settings = "settings"
private const val Summary = "summary"
private const val Review = "review"
private const val AddPeople = "add-people"
private const val Assign = "assign"
private const val Tip = "tip"
private const val Totals = "totals"

@Composable
fun SplitTheBillApp() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf(Home) }
    var summaryBillId by rememberSaveable { mutableStateOf<String?>(null) }
    val scanViewModel: ReceiptScanViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()

    fun go(next: String) {
        route = next
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(scanViewModel.phase) {
        if (scanViewModel.phase is ReceiptPhase.Ready && route == Scan) {
            go(Review)
        }
    }

    val billRoutes = setOf(Home, Scan, Review, AddPeople, Assign, Tip, Totals, Summary)

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
                    selected = route in billRoutes,
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
            Scan -> ScanRoute(
                reading = scanViewModel.phase is ReceiptPhase.Reading,
                errorMessage = (scanViewModel.phase as? ReceiptPhase.Failed)?.message,
                onBack = { go(Home) },
                onPhoto = scanViewModel::read,
            )
            Review -> {
                val state = scanViewModel.review
                if (state == null) {
                    StubScreen(
                        title = "Review",
                        body = "Scan a receipt to review its items.",
                        onBack = { go(Scan) },
                    )
                } else {
                    ReviewScreen(
                        state = state,
                        onBack = { go(Scan) },
                        onName = scanViewModel::onName,
                        onPrice = scanViewModel::onPrice,
                        onQuantity = scanViewModel::onQuantity,
                        onDelete = scanViewModel::delete,
                        onAdd = scanViewModel::addItem,
                        onTax = scanViewModel::onTax,
                        onTip = scanViewModel::onTip,
                        onAddPeople = {
                            if (scanViewModel.openPeople()) go(AddPeople)
                        },
                        notice = scanViewModel.reviewNotice,
                    )
                }
            }
            AddPeople -> {
                val draft = scanViewModel.draft
                if (draft == null) {
                    StubScreen(
                        title = "Add people",
                        body = "Review a receipt before adding people.",
                        onBack = { go(Review) },
                    )
                } else {
                    AddPeopleScreen(
                        merchant = draft.merchant,
                        people = draft.people,
                        name = scanViewModel.personName,
                        editingId = scanViewModel.editingPersonId,
                        onName = scanViewModel::onPersonName,
                        onSubmit = scanViewModel::submitPerson,
                        onSelect = scanViewModel::selectPerson,
                        onRemove = scanViewModel::removePersonFromBill,
                        onBack = { go(Review) },
                        onContinue = { go(Assign) },
                    )
                }
            }
            Assign -> {
                val draft = scanViewModel.draft
                if (draft == null) {
                    StubScreen(
                        title = "Assign",
                        body = "Add people before assigning items.",
                        onBack = { go(AddPeople) },
                    )
                } else {
                    AssignScreen(
                        merchant = draft.merchant,
                        people = draft.people,
                        items = draft.items,
                        message = assignmentMessage(draft),
                        onToggle = scanViewModel::togglePerson,
                        onEveryone = scanViewModel::assignItemToEveryone,
                        onSplitRemaining = scanViewModel::splitRemaining,
                        onBack = { go(AddPeople) },
                        onContinue = {
                            scanViewModel.prepareTipFields()
                            go(Tip)
                        },
                    )
                }
            }
            Tip -> {
                val draft = scanViewModel.draft
                if (draft == null) {
                    StubScreen(
                        title = "Tip",
                        body = "Assign every item before choosing a tip.",
                        onBack = { go(Assign) },
                    )
                } else {
                    TipScreen(
                        subtotal = draft.itemsSubtotal,
                        parsedTip = draft.parsedTip,
                        choice = draft.tipChoice,
                        customPercent = scanViewModel.customPercent,
                        fixedAmount = scanViewModel.fixedTip,
                        onPercent = scanViewModel::choosePercent,
                        onCustomPercent = scanViewModel::onCustomPercent,
                        onFixedAmount = scanViewModel::onFixedTip,
                        onNoTip = scanViewModel::chooseNoTip,
                        onBack = { go(Assign) },
                        onContinue = {
                            scanViewModel.saveBill()
                            go(Totals)
                        },
                    )
                }
            }
            Totals -> {
                val draft = scanViewModel.draft
                val result = draft?.let { split(it.toBill()) }
                if (draft == null || result !is SplitResult.Success) {
                    StubScreen(
                        title = "Totals",
                        body = "Finish assigning items and a tip to see the totals.",
                        onBack = { go(Tip) },
                    )
                } else {
                    val bill = draft.toBill()
                    TotalsScreen(
                        bill = bill,
                        shares = result.shares,
                        addsUp = sharesAddUp(bill, result.shares),
                        onBack = { go(Tip) },
                        onShare = { shareBill(context, shareSummary(bill, result.shares)) },
                        onDone = {
                            scanViewModel.reset()
                            go(Home)
                        },
                    )
                }
            }
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
                val recorded = summaryBillId?.let { homeViewModel.recorded(it) }
                val result = recorded?.let { split(it.bill) }
                if (recorded == null || result !is SplitResult.Success) {
                    StubScreen(
                        title = recorded?.bill?.merchant ?: "This bill",
                        body = if (result is SplitResult.Failure) result.error.message else "That bill is no longer saved.",
                        onBack = { go(Home) },
                    )
                } else {
                    TotalsScreen(
                        bill = recorded.bill,
                        shares = result.shares,
                        addsUp = sharesAddUp(recorded.bill, result.shares),
                        onBack = { go(Home) },
                        onShare = { shareBill(context, shareSummary(recorded.bill, result.shares)) },
                        onDone = null,
                    )
                }
            }
            else -> {
                HomeScreen(
                    state = homeViewModel.uiState,
                    onMenu = { scope.launch { drawerState.open() } },
                    onProfile = { go(Profile) },
                    onBill = { id: BillId ->
                        summaryBillId = id.value
                        go(Summary)
                    },
                    onNewBill = {
                        scanViewModel.reset()
                        go(Scan)
                    },
                )
            }
        }
    }
}
