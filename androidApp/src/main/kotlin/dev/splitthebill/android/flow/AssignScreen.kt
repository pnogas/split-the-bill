package dev.splitthebill.android.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.splitthebill.android.home.formatDollars
import dev.splitthebill.android.theme.SplitTheBillTheme
import dev.splitthebill.shared.BillOwner
import dev.splitthebill.shared.Cents
import dev.splitthebill.shared.LineItem
import dev.splitthebill.shared.LineItemId
import dev.splitthebill.shared.Person
import dev.splitthebill.shared.PersonId

private val CardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignScreen(
    merchant: String?,
    people: List<Person>,
    items: List<LineItem>,
    message: String?,
    onToggle: (LineItemId, PersonId) -> Unit,
    onEveryone: (LineItemId) -> Unit,
    onSplitRemaining: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val anyUnassigned = items.any { it.sharedBy.isEmpty() }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Assign") },
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
                enabled = message == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("Continue")
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
                text = merchant ?: "Receipt",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Tap the people who shared an item. More than one person splits it evenly.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (message != null) {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = scheme.errorContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onErrorContainer,
                    )
                }
            }
            if (anyUnassigned) {
                OutlinedButton(
                    onClick = onSplitRemaining,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Split remaining evenly")
                }
            }
            items.forEach { item ->
                ItemAssignment(
                    item = item,
                    people = people,
                    onToggle = { onToggle(item.id, it) },
                    onEveryone = { onEveryone(item.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemAssignment(
    item: LineItem,
    people: List<Person>,
    onToggle: (PersonId) -> Unit,
    onEveryone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val unassigned = item.sharedBy.isEmpty()
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (unassigned) scheme.errorContainer else scheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (unassigned) scheme.onErrorContainer else scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.lineTotal.formatDollars(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (unassigned) scheme.onErrorContainer else scheme.onSurface,
                )
            }
            if (unassigned) {
                Text(
                    text = "Unassigned",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.error,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                people.forEach { person ->
                    FilterChip(
                        selected = person.id in item.sharedBy,
                        onClick = { onToggle(person.id) },
                        label = { Text(person.name) },
                    )
                }
            }
            TextButton(onClick = onEveryone) {
                Text("Everyone")
            }
        }
    }
}

@Preview(name = "Assign unassigned", widthDp = 390, heightDp = 844)
@Composable
private fun AssignPreview() {
    SplitTheBillTheme {
        val items = sampleAssignItems()
        AssignScreen(
            merchant = "Pho Lien",
            people = samplePeople(),
            items = items,
            message = "Assign every item. Still open: Spring Rolls.",
            onToggle = { _, _ -> },
            onEveryone = {},
            onSplitRemaining = {},
            onBack = {},
            onContinue = {},
        )
    }
}

internal fun sampleAssignItems(): List<LineItem> = listOf(
    LineItem(LineItemId("pho"), "Rare Beef Pho", Cents(1_650), 1, setOf(BillOwner.id)),
    LineItem(LineItemId("rolls"), "Spring Rolls", Cents(1_100), 1, emptySet()),
)
