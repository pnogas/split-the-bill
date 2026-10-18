package dev.splitthebill.android.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.splitthebill.android.home.formatDollars
import dev.splitthebill.android.review.parseDollars
import dev.splitthebill.android.theme.SplitTheBillTheme
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.TipChoice
import dev.splitthebill.shared.tipAmount

private val Presets = listOf(15, 18, 20)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipScreen(
    subtotal: Cents,
    parsedTip: Cents?,
    choice: TipChoice,
    customPercent: String,
    fixedAmount: String,
    onPercent: (Int) -> Unit,
    onCustomPercent: (String) -> Unit,
    onFixedAmount: (String) -> Unit,
    onNoTip: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val computed = tipAmount(subtotal, choice)
    val customNumber = customPercent.toIntOrNull()
    val customInvalid = customPercent.isNotEmpty() && (customNumber == null || customNumber !in 1..100)
    val fixedInvalid = fixedAmount.isNotBlank() && parseDollars(fixedAmount)?.let { it.amount >= 0 } != true
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Tip") },
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
                onClick = onContinue,
                enabled = choice != TipChoice.Unset,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("See totals")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Item subtotal ${subtotal.formatDollars()}",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (parsedTip != null && parsedTip.amount > 0) {
                Text(
                    text = "The receipt included a tip of ${parsedTip.formatDollars()}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "This receipt has no tip yet. Add a percent or a fixed amount.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Presets.forEach { percent ->
                    FilterChip(
                        selected = choice is TipChoice.Percent && choice.percent == percent,
                        onClick = { onPercent(percent) },
                        label = { Text("$percent%") },
                    )
                }
                FilterChip(
                    selected = choice == TipChoice.None,
                    onClick = onNoTip,
                    label = { Text("No tip") },
                )
            }
            OutlinedTextField(
                value = customPercent,
                onValueChange = onCustomPercent,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom percent") },
                suffix = { Text("%") },
                singleLine = true,
                isError = customInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = fixedAmount,
                onValueChange = onFixedAmount,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Fixed amount") },
                prefix = { Text("$") },
                singleLine = true,
                isError = fixedInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Text(
                text = when (choice) {
                    TipChoice.Unset -> "Choose a tip to continue."
                    TipChoice.None -> "No tip."
                    is TipChoice.Percent ->
                        "${choice.percent}% of ${subtotal.formatDollars()} is ${computed.formatDollars()}."
                    is TipChoice.Fixed -> "Tip ${computed.formatDollars()}."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (choice == TipChoice.Unset) scheme.onSurfaceVariant else scheme.primary,
            )
        }
    }
}

@Preview(name = "Tip", widthDp = 390, heightDp = 844)
@Composable
private fun TipPreview() {
    SplitTheBillTheme {
        TipScreen(
            subtotal = Cents(2_750),
            parsedTip = null,
            choice = TipChoice.Percent(18),
            customPercent = "",
            fixedAmount = "",
            onPercent = {},
            onCustomPercent = {},
            onFixedAmount = {},
            onNoTip = {},
            onBack = {},
            onContinue = {},
        )
    }
}
