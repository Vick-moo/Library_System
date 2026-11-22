package com.example.librarysystem.ui.borrow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.model.ActiveLoan
import com.example.librarysystem.data.model.AvailableCopy
import com.example.librarysystem.ui.components.LabeledField
import com.example.librarysystem.ui.components.LibraryTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowDeskScreen(
    onOpenFines: () -> Unit,
    flashNotice: String? = null,
    onFlashConsumed: () -> Unit = {},
    vm: BorrowDeskViewModel = viewModel()
) {
    LaunchedEffect(flashNotice) {
        if (!flashNotice.isNullOrBlank()) {
            vm.onReturnedFromFines(flashNotice)
            onFlashConsumed()
        }
    }

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                title = "Borrow desk",
                actions = {
                    TextButton(onClick = onOpenFines) { Text("Fines") }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = vm.tab) {
                Tab(
                    selected = vm.tab == 0,
                    onClick = { vm.selectTab(0) },
                    text = { Text("Borrow") }
                )
                Tab(
                    selected = vm.tab == 1,
                    onClick = { vm.selectTab(1) },
                    text = { Text("Return") }
                )
            }

            vm.notice?.let {
                NoticeBanner(
                    text = it,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            vm.error?.let {
                NoticeBanner(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                if (vm.tab == 0) BorrowTab(vm) else ReturnTab(vm)
            }
        }
    }

    vm.confirmReturnLoan?.let { loan ->
        ReturnConfirmDialog(
            loan = loan,
            onConfirm = vm::confirmReturn,
            onDismiss = vm::dismissReturnConfirm
        )
    }

    vm.fineGate?.let { gate ->
        FineGateDialog(
            gate = gate,
            onGoFines = {
                vm.dismissFineGate()
                onOpenFines()
            },
            onDismiss = vm::dismissFineGate
        )
    }

    vm.damageTarget?.let { loan ->
        DamageDialog(
            loan = loan,
            onConfirm = vm::reportDamage,
            onDismiss = vm::dismissDamage
        )
    }
}

@Composable
private fun NoticeBanner(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

// -------------------------------------------------------------------- borrow

@Composable
private fun BorrowTab(vm: BorrowDeskViewModel) {
    val scroll = rememberScrollState()
    // Errors render above the list; snap back so a failed action is visible.
    LaunchedEffect(vm.error) {
        if (!vm.error.isNullOrBlank()) scroll.animateScrollTo(0)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            LabeledField(
                label = "Student number",
                value = vm.studentNoInput,
                onValueChange = vm::onStudentNoChange,
                placeholder = "e.g. 23WMR00001",
                maxLength = 20,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = vm::searchStudent,
                enabled = !vm.studentSearching,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(if (vm.studentSearching) "…" else "Search")
            }
        }

        vm.studentCard?.let { card ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        card.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        card.studentNo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    StudentStatRow("On loan", "${card.activeLoans} / 2")
                    StudentStatRow("Reserved", "${card.reservedCount} / 2")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (card.unpaidFines > 0) {
                            "Unpaid fines: RM %.2f".format(card.unpaidFines)
                        } else {
                            "No unpaid fines"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (card.unpaidFines > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (card.atLimit) {
                        Text(
                            "Loan + reservation limit reached (2). No further borrowing.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        LabeledField(
            label = "Find an available copy",
            value = vm.copyQuery,
            onValueChange = vm::onCopyQueryChange,
            placeholder = "copy no / title / ISBN",
            maxLength = 100
        )

        if (vm.copiesLoading) {
            Box(
                Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (vm.copies.isEmpty()) {
            Text(
                "No available copies match.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.copies.forEach { copy ->
                    CopyOption(
                        copy = copy,
                        selected = vm.selectedCopy?.copyId == copy.copyId,
                        onClick = { vm.selectCopy(copy) }
                    )
                }
            }
        }

        Button(
            onClick = vm::confirmBorrow,
            enabled = !vm.busy && vm.selectedCopy != null && vm.studentCard != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.busy) "Borrowing…" else "Borrow selected copy")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StudentStatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CopyOption(copy: AvailableCopy, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                copy.bookTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${copy.copyNo} · ${copy.bookAuthor}" +
                    (copy.shelf?.let { " · Shelf $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Text("✓", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// -------------------------------------------------------------------- return

@Composable
private fun ReturnTab(vm: BorrowDeskViewModel) {
    val visible = vm.visibleLoans  // computed once per recomposition
    val listState = rememberLazyListState()
    LaunchedEffect(vm.error) {
        if (!vm.error.isNullOrBlank()) listState.animateScrollToItem(0)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().imePadding()) {
        item {
            Column(Modifier.padding(16.dp)) {
                LabeledField(
                    label = "Search by student number",
                    value = vm.loanQuery,
                    onValueChange = vm::onLoanQueryChange,
                    placeholder = "e.g. 23WMR00002",
                    maxLength = 20
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (visible.size == 1) "1 book on loan" else "${visible.size} books on loan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (vm.loansLoading) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (visible.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (vm.loanQuery.isBlank()) "No books are on loan right now."
                        else "No loans for that student number.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(visible, key = { it.borrowId }) { loan ->
                LoanRow(
                    loan = loan,
                    busy = vm.busy,
                    onReturn = { vm.onReturnClick(loan) },
                    onDamage = { vm.openDamage(loan) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 1.dp
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LoanRow(
    loan: ActiveLoan,
    busy: Boolean,
    onReturn: () -> Unit,
    onDamage: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                loan.bookTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${loan.studentName} (${loan.studentNo}) · ${loan.copyNo}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (loan.isOverdue) "Due ${loan.dueDate} · ${loan.overdueDays} day(s) late"
                else "Due ${loan.dueDate}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (loan.isOverdue) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (loan.hasUnpaidFine) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Unpaid fine RM %.2f — settle before returning".format(loan.unpaidFine),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            OutlinedButton(onClick = onReturn, enabled = !busy) {
                Text("Return")
            }
            TextButton(onClick = onDamage, enabled = !busy) {
                Text("Damage", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ------------------------------------------------------------------- dialogs

@Composable
private fun ReturnConfirmDialog(
    loan: ActiveLoan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return this book?") },
        text = {
            Column {
                Text("\"${loan.bookTitle}\"")
                Spacer(Modifier.height(4.dp))
                Text(
                    "${loan.studentName} (${loan.studentNo}) · copy ${loan.copyNo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (loan.isOverdue) {
                        "Due ${loan.dueDate} · ${loan.overdueDays} day(s) late"
                    } else {
                        "Due ${loan.dueDate} · on time"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (loan.isOverdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Return book") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FineGateDialog(
    gate: FineGate,
    onGoFines: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fine must be settled first") },
        text = {
            Column {
                Text(
                    if (gate.overdueDays > 0) {
                        "\"${gate.loan.bookTitle}\" is ${gate.overdueDays} day(s) late."
                    } else {
                        "\"${gate.loan.bookTitle}\" still carries an unpaid fine."
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Collect RM %.2f at the counter, then mark it paid in Fines. "
                        .format(gate.amount) +
                        "The book can only be returned after the fine is cleared.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onGoFines) { Text("Go to fines") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DamageDialog(
    loan: ActiveLoan,
    onConfirm: (category: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf("minor") }
    var description by remember { mutableStateOf("") }

    val fraction = if (category == "minor") 0.5 else 1.0
    val estimate = loan.bookPrice * fraction

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report damage") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "\"${loan.bookTitle}\" · copy ${loan.copyNo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                DamageChoice(
                    label = "Minor damage",
                    hint = "50%% of book price · RM %.2f".format(loan.bookPrice * 0.5),
                    selected = category == "minor",
                    onClick = { category = "minor" }
                )
                DamageChoice(
                    label = "Major damage",
                    hint = "100%% of book price · RM %.2f".format(loan.bookPrice),
                    selected = category == "major",
                    onClick = { category = "major" }
                )
                DamageChoice(
                    label = "Lost",
                    hint = "100% of book price · loan closed as lost",
                    selected = category == "lost",
                    onClick = { category = "lost" }
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 300) description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("e.g. water damage on pages 10-40") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Charges RM %.2f and takes this copy out of circulation.".format(estimate),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(category, description) }) { Text("Record") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DamageChoice(
    label: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
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
