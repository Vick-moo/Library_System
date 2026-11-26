package com.example.librarysystem.ui.damage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.model.DamageReport
import com.example.librarysystem.ui.components.LabeledField
import com.example.librarysystem.ui.components.LibraryTopAppBar

/**
 * Edits an existing report. The level and the description can change at any
 * time; the compensation percentage cannot, because the fine is already fixed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamageEditScreen(
    report: DamageReport,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: (String) -> Unit,
    vm: DamageEditViewModel = viewModel()
) {
    LaunchedEffect(report.reportId) { vm.load(report) }

    Scaffold(
        topBar = { LibraryTopAppBar(title = "Edit damage report", onBack = onBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        report.bookTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${report.copyNo} · ${report.bookAuthor}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Book price RM %.2f".format(report.bookPrice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (report.hasBorrower) {
                            "Borrower: ${report.studentName ?: report.studentNo}"
                        } else {
                            "On the shelf - no borrower, no fine"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (report.dateLabel.isNotBlank()) {
                        Text(
                            "Reported ${report.dateLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionTitle("Compensation (locked)")
            val amount = report.fineAmount
            if (report.fineId != null && amount != null) {
                Text(
                    "RM %.2f - %d%% of the book price".format(
                        amount, report.compensationPercent
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Fine status: ${report.fineStatus?.replaceFirstChar { it.uppercase() } ?: "unpaid"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (report.fineStatus == "unpaid") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "The amount cannot be edited. Delete the report if it is wrong.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "No fine was recorded for this report.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionTitle("Damage level")
            DamageLevelRow(
                label = "Lost",
                hint = "Book is gone",
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

            LabeledField(
                label = "Description",
                value = vm.description,
                onValueChange = vm::onDescriptionChange,
                placeholder = "e.g. pages were taped back",
                singleLine = false,
                maxLines = 5,
                maxLength = 300,
                supportingText = { Text("${vm.description.length} / 300") }
            )

            vm.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { vm.save(report.reportId, onSaved) },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.busy) "Saving…" else "Save changes")
            }

            OutlinedButton(
                onClick = { vm.requestDelete() },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove this damage report", color = MaterialTheme.colorScheme.error)
            }

            Text(
                "Remove it when the book has been repaired or replaced.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (vm.confirmingDelete) {
        val amount = report.fineAmount
        AlertDialog(
            onDismissRequest = vm::dismissDelete,
            title = { Text("Delete this damage report?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("\"${report.bookTitle}\" (${report.copyNo})")
                    if (report.fineId != null && amount != null) {
                        Text(
                            "This also deletes the fine of RM %.2f (%s)."
                                .format(
                                    amount,
                                    report.fineStatus?.replaceFirstChar { it.uppercase() }
                                        ?: "unpaid"
                                ),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        "The copy goes back into circulation when it is not on loan.",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "This cannot be undone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.delete(report.reportId, onDeleted) },
                    enabled = !vm.busy
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDelete) { Text("Cancel") }
            }
        )
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
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
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
