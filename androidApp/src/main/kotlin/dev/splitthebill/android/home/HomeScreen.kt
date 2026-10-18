package dev.splitthebill.android.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.splitthebill.android.R
import dev.splitthebill.android.data.ExampleBillRepository
import dev.splitthebill.android.theme.SplitTheBillTheme
import dev.splitthebill.shared.BillId

private const val RecentBillLimit = 10
private val CardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onMenu: () -> Unit,
    onProfile: () -> Unit,
    onBill: (BillId) -> Unit,
    onNewBill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Split the Bill")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onProfile,
                        modifier = Modifier.clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = "Profile"
                            onClick {
                                onProfile()
                                true
                            }
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(scheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = state.initials,
                                color = scheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surface,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface,
                    actionIconContentColor = scheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewBill,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New bill") },
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                shape = CardShape,
            )
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { innerPadding ->
        if (state.bills.isEmpty()) {
            EmptyHome(Modifier.padding(innerPadding))
        } else {
            BillList(
                state = state,
                onBill = onBill,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun BillList(
    state: HomeUiState,
    onBill: (BillId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visible = if (showAll) state.bills else state.bills.take(RecentBillLimit)
    val hidden = state.bills.size - visible.size
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp),
    ) {
        state.balance?.let { balance ->
            BalanceCard(
                balance = balance,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Recent bills",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.exampleData) {
                ExampleChip()
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.forEach { row ->
                BillRow(row = row, onClick = { onBill(row.id) })
            }
        }
        // PROPOSED: after about ten rows, offer the rest. Hidden for the sample list.
        if (hidden > 0) {
            TextButton(
                onClick = { showAll = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("See all")
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: HomeBalance, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = CardShape,
        color = scheme.primary,
        contentColor = scheme.onPrimary,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            when (balance) {
                is HomeBalance.Owed -> BalanceFigures(
                    title = "You are owed",
                    amount = balance.amountLabel,
                    detail = balance.detail,
                )
                is HomeBalance.Owes -> BalanceFigures(
                    title = "You owe",
                    amount = balance.amountLabel,
                    detail = balance.detail,
                )
                HomeBalance.SettledUp -> Text(
                    text = "All settled up",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BalanceFigures(title: String, amount: String, detail: String) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = onPrimary.copy(alpha = 0.85f),
    )
    Text(
        text = amount,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
    Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = onPrimary.copy(alpha = 0.8f),
    )
}

@Composable
private fun ExampleChip() {
    Text(
        text = "EXAMPLE",
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun BillRow(row: HomeBillRow, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val statusColor = when (row.status) {
        is HomeBillStatus.Owed, HomeBillStatus.Settled -> scheme.primary
        is HomeBillStatus.Owes -> scheme.error
        is HomeBillStatus.Unresolved -> scheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = row.contentDescription
                onClick {
                    onClick()
                    true
                }
            },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restaurant,
                    contentDescription = null,
                    tint = scheme.onSecondaryContainer,
                )
            }
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = row.merchant,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "${row.dateLabel} · ${row.peopleLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = row.status.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** PROPOSED empty state. Not in the approved mockup; easy to restyle. */
@Composable
private fun EmptyHome(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = "No bills yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Snap a receipt to split your first bill.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Home", widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    SplitTheBillTheme {
        HomeScreen(
            state = homeUiState(ExampleBillRepository()),
            onMenu = {},
            onProfile = {},
            onBill = {},
            onNewBill = {},
        )
    }
}

@Preview(name = "Empty", widthDp = 390, heightDp = 844)
@Composable
private fun HomeEmptyPreview() {
    SplitTheBillTheme {
        HomeScreen(
            state = homeUiState(
                viewer = ExampleBillRepository().viewer,
                bills = emptyList(),
                exampleData = false,
            ),
            onMenu = {},
            onProfile = {},
            onBill = {},
            onNewBill = {},
        )
    }
}

@Preview(name = "Dark", widthDp = 390, heightDp = 844)
@Composable
private fun HomeDarkPreview() {
    SplitTheBillTheme(darkTheme = true) {
        HomeScreen(
            state = homeUiState(ExampleBillRepository()),
            onMenu = {},
            onProfile = {},
            onBill = {},
            onNewBill = {},
        )
    }
}

@Preview(name = "Large text", widthDp = 390, heightDp = 900, fontScale = 1.6f)
@Composable
private fun HomeLargeTextPreview() {
    SplitTheBillTheme {
        HomeScreen(
            state = homeUiState(ExampleBillRepository()),
            onMenu = {},
            onProfile = {},
            onBill = {},
            onNewBill = {},
        )
    }
}
