package dev.splitthebill.android.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.splitthebill.android.home.formatDollars
import dev.splitthebill.android.theme.SplitTheBillTheme
import dev.splitthebill.shared.Bill
import dev.splitthebill.shared.BillId
import dev.splitthebill.shared.BillOwner
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.LineItem
import dev.splitthebill.shared.LineItemId
import dev.splitthebill.shared.Person
import dev.splitthebill.shared.PersonId
import dev.splitthebill.shared.PersonShare
import dev.splitthebill.shared.SplitResult
import dev.splitthebill.shared.sharesAddUp
import dev.splitthebill.shared.split
import kotlin.time.Instant

private val CardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalsScreen(
    bill: Bill,
    shares: List<PersonShare>,
    addsUp: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDone: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Totals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share")
                }
                if (onDone != null) {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Done")
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = bill.merchant ?: "Receipt",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Subtotal ${bill.subtotal.formatDollars()} · Tax ${bill.tax.formatDollars()} · Tip ${bill.tip.formatDollars()}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = "Bill total ${bill.total.formatDollars()}",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            shares.forEach { share ->
                ShareCard(share)
            }
            Text(
                text = if (addsUp) {
                    "These add up to ${bill.total.formatDollars()}."
                } else {
                    "These do not add up to ${bill.total.formatDollars()}."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (addsUp) scheme.primary else scheme.error,
            )
        }
    }
}

@Composable
private fun ShareCard(share: PersonShare) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = share.person.name,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
            )
            ShareLine("Items", share.itemsSubtotal)
            ShareLine("Tax", share.tax)
            ShareLine("Tip", share.tip)
            Text(
                text = "Total ${share.total.formatDollars()}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurface,
            )
        }
    }
}

@Composable
private fun ShareLine(label: String, amount: Cents) {
    Text(
        text = "$label ${amount.formatDollars()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(name = "Totals", widthDp = 390, heightDp = 844)
@Composable
private fun TotalsPreview() {
    val bill = sampleTotalsBill()
    val shares = (split(bill) as SplitResult.Success).shares
    SplitTheBillTheme {
        TotalsScreen(
            bill = bill,
            shares = shares,
            addsUp = sharesAddUp(bill, shares),
            onBack = {},
            onShare = {},
            onDone = {},
        )
    }
}

internal fun sampleTotalsBill(): Bill {
    val maya = PersonId("person-1")
    val everyone = setOf(BillOwner.id, maya)
    return Bill(
        id = BillId("bill-1"),
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
        merchant = "Pho Lien",
        people = listOf(BillOwner.person, Person(maya, "Maya")),
        items = listOf(
            LineItem(LineItemId("pho"), "Rare Beef Pho", Cents(1_650), 1, everyone),
            LineItem(LineItemId("rolls"), "Spring Rolls", Cents(1_100), 1, everyone),
        ),
        tax = Cents(358),
        tip = Cents(495),
    )
}
