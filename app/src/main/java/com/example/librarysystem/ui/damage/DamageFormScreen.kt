package com.example.librarysystem.ui.damage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.model.DamageCopy
import com.example.librarysystem.ui.components.LibraryTopAppBar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamageFormScreen(
    copy: DamageCopy,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    vm: DamageFormViewModel = viewModel()
) {
    // A fresh form every time the staff opens a different copy.
    LaunchedEffect(copy.copyId) { vm.reset() }

    val estimated = copy.bookPrice * vm.percent / 100.0

    Scaffold(
        topBar = { LibraryTopAppBar(title = "Report damage", onBack = onBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        copy.bookTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${copy.copyNo} · ${copy.bookAuthor}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Book price RM %.2f".format(copy.bookPrice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (copy.isOnLoan) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "On loan to ${copy.borrowerName ?: copy.borrowerNo ?: "student"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            SectionTitle("Damage level")
            DamageLevelRow(
                label = "Lost",
                hint = "Book is gone; loan is closed as lost",
                selected = vm.category == "lost",
                onClick = { vm.chooseCategory("lost") }
            )
            DamageLevelRow(
                label = "Minor damage",
                hint = "Small tear, stain or worn cover",
                selected = vm.category == "minor",
                onClick = { vm.chooseCategory("minor") }
            )
            DamageLevelRow(
                label = "Major damage",
                hint = "Broken spine, missing pages, unreadable",
                selected = vm.category == "major",
                onClick = { vm.chooseCategory("major") }
            )

            SectionTitle("Compensation")
            Text(
                "${vm.percent}% of book price",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = vm.percent.toFloat(),
                onValueChange = { vm.choosePercent(it.roundToInt()) },
                valueRange = 0f..100f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(0, 25, 50, 75, 100).forEach { p ->
                    Text(
                        "$p%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (p == vm.percent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (p == vm.percent) FontWeight.Bold
                        else FontWeight.Normal
                    )
                }
            }
            Text(
                if (vm.percent == 0) {
                    "No compensation charged."
                } else {
                    "Compensation: RM %.2f".format(estimated)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (vm.percent == 0) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )

            OutlinedTextField(
                value = vm.description,
                onValueChange = vm::onDescriptionChange,
                label = { Text("Description (optional)") },
                placeholder = { Text("e.g. water damage on pages 10-40") },
                minLines = 3,
                maxLines = 5,
                supportingText = { Text("${vm.description.length} / 300") },
                modifier = Modifier.fillMaxWidth()
            )

            vm.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { vm.submit(copy, onSaved) },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.busy) "Saving…" else "Record damage")
            }

            Text(
                "This copy will be marked unavailable and taken out of circulation.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DamageLevelRow(
    label: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
