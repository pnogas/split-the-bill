package dev.splitthebill.android.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.splitthebill.android.theme.SplitTheBillTheme
import dev.splitthebill.shared.BillOwner
import dev.splitthebill.shared.Person
import dev.splitthebill.shared.PersonId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPeopleScreen(
    merchant: String?,
    people: List<Person>,
    name: String,
    editingId: PersonId?,
    onName: (String) -> Unit,
    onSubmit: () -> Unit,
    onSelect: (PersonId) -> Unit,
    onRemove: (PersonId) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val canContinue = people.size >= 2 && people.all { it.name.isNotBlank() }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Add people") },
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
                enabled = canContinue,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = merchant ?: "Receipt",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Who is splitting this bill? Tap a name to rename it.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    modifier = Modifier.weight(1f),
                    label = { Text(if (editingId == null) "Name" else "Rename") },
                    singleLine = true,
                )
                Button(onClick = onSubmit, enabled = name.isNotBlank()) {
                    Text(if (editingId == null) "Add" else "Save")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                people.forEach { person ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = person.id == editingId,
                            onClick = { onSelect(person.id) },
                            label = { Text(person.name) },
                        )
                        if (person.id != BillOwner.id) {
                            IconButton(onClick = { onRemove(person.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove ${person.name}")
                            }
                        }
                    }
                }
            }
            if (!canContinue) {
                Text(
                    text = "Add at least one other person.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "Add people", widthDp = 390, heightDp = 844)
@Composable
private fun AddPeoplePreview() {
    SplitTheBillTheme {
        AddPeopleScreen(
            merchant = "Pho Lien",
            people = samplePeople(),
            name = "",
            editingId = null,
            onName = {},
            onSubmit = {},
            onSelect = {},
            onRemove = {},
            onBack = {},
            onContinue = {},
        )
    }
}

internal fun samplePeople(): List<Person> = listOf(
    BillOwner.person,
    Person(PersonId("person-1"), "Maya"),
)
