package dev.splitthebill.android.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.splitthebill.android.theme.SplitTheBillTheme
import dev.splitthebill.shared.Cents

private val CardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onBack: () -> Unit,
    onName: (String, String) -> Unit,
    onPrice: (String, String) -> Unit,
    onQuantity: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    onTax: (String) -> Unit,
    onTip: (String) -> Unit,
    onAddPeople: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Review") },
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
            Button(
                onClick = onAddPeople,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Add people")
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
                text = state.merchant ?: "Receipt",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Check the items, tax, and tip before adding people.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (notice != null) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
            }
            if (state.items.isEmpty()) {
                Text(
                    text = "No items were found. Add what was on the receipt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            state.items.forEach { item ->
                ItemEditor(
                    item = item,
                    onName = { onName(item.key, it) },
                    onPrice = { onPrice(item.key, it) },
                    onQuantity = { onQuantity(item.key, it) },
                    onDelete = { onDelete(item.key) },
                )
            }
            TextButton(onClick = onAdd) {
                Text("Add an item")
            }
            SubtotalNote(subtotalCheck(state))
            MoneyField(label = "Tax", value = state.tax, onValue = onTax)
            MoneyField(label = "Tip", value = state.tip, onValue = onTip)
            state.receiptTotal?.let { total ->
                Text(
                    text = "Receipt total ${total.signedDollars()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ItemEditor(
    item: ReviewItemUi,
    onName: (String) -> Unit,
    onPrice: (String) -> Unit,
    onQuantity: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val price = parseDollars(item.price)
    val quantity = item.quantity.toIntOrNull()
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.name,
                    onValueChange = onName,
                    modifier = Modifier.weight(1f),
                    label = { Text("Item") },
                    singleLine = true,
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${item.name.ifBlank { "item" }}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = item.price,
                    onValueChange = onPrice,
                    modifier = Modifier.weight(1f),
                    label = { Text("Price") },
                    prefix = { Text("$") },
                    singleLine = true,
                    isError = item.price.isNotBlank() && price == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = item.quantity,
                    onValueChange = onQuantity,
                    modifier = Modifier.width(88.dp),
                    label = { Text("Qty") },
                    singleLine = true,
                    isError = quantity == null || quantity <= 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (price != null && quantity != null && quantity > 0) {
                Text(
                    text = "Line total ${(price * quantity).signedDollars()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoneyField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = { Text("$") },
        singleLine = true,
        isError = value.isNotBlank() && parseDollars(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun SubtotalNote(check: SubtotalCheck) {
    val scheme = MaterialTheme.colorScheme
    val (text, color) = when (check) {
        is SubtotalCheck.Matches ->
            "Items sum to ${check.sum.signedDollars()}, matching the receipt subtotal." to scheme.primary
        is SubtotalCheck.Differs ->
            "Items sum to ${check.sum.signedDollars()}. The receipt subtotal is ${check.receipt.signedDollars()}." to scheme.error
        is SubtotalCheck.Unknown ->
            "Items sum to ${check.sum.signedDollars()}. No subtotal was printed on the receipt." to scheme.onSurfaceVariant
        SubtotalCheck.NeedsPrices ->
            "Enter a price and quantity for each item." to scheme.error
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Preview(name = "Review", widthDp = 390, heightDp = 844)
@Composable
private fun ReviewPreview() {
    SplitTheBillTheme {
        ReviewScreen(
            state = sampleReview(),
            onBack = {},
            onName = { _, _ -> },
            onPrice = { _, _ -> },
            onQuantity = { _, _ -> },
            onDelete = {},
            onAdd = {},
            onTax = {},
            onTip = {},
            onAddPeople = {},
        )
    }
}

@Preview(name = "Review mismatch", widthDp = 390, heightDp = 900)
@Composable
private fun ReviewMismatchPreview() {
    SplitTheBillTheme {
        ReviewScreen(
            state = sampleReview().copy(receiptSubtotal = Cents(4_500)),
            onBack = {},
            onName = { _, _ -> },
            onPrice = { _, _ -> },
            onQuantity = { _, _ -> },
            onDelete = {},
            onAdd = {},
            onTax = {},
            onTip = {},
            onAddPeople = {},
        )
    }
}

internal fun sampleReview(): ReviewUiState = ReviewUiState(
    merchant = "Pho Lien",
    items = listOf(
        ReviewItemUi("pho", "Rare Beef Pho", "16.50", "1"),
        ReviewItemUi("rolls", "Spring Rolls", "5.50", "2"),
        ReviewItemUi("banh", "Banh Mi", "9.00", "1"),
    ),
    tax = "4.55",
    tip = "6.00",
    receiptSubtotal = Cents(3_650),
    receiptTotal = Cents(4_705),
)
