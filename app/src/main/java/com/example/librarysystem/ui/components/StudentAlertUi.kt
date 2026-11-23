package com.example.librarysystem.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.librarysystem.data.model.StudentAlert

/** Opened from [DueBanner]: the student's active loans and unpaid fines in one place. */
@Composable
fun MyLoansDialog(alert: StudentAlert, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("My loans") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (alert.loans.isEmpty()) {
                    Text("No books on loan right now.", style = MaterialTheme.typography.bodyMedium)
                }
                alert.loans.forEach { loan ->
                    Column {
                        Text(
                            loan.bookTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Copy ${loan.copyNo} · ${loan.dueLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (loan.isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (alert.unpaidTotal > 0.0) {
                    HorizontalDivider()
                    Text(
                        "Unpaid fines: RM %.2f".format(alert.unpaidTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Settle unpaid fines at the counter before your next return.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}
