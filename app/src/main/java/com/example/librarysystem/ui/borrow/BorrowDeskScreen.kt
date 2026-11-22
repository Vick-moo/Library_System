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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.model.ActiveLoan
import com.example.librarysystem.data.model.AvailableCopy
import com.example.librarysystem.data.model.DamageCopy
import com.example.librarysystem.data.model.FineRow
import com.example.librarysystem.ui.components.LabeledField
import com.example.librarysystem.ui.components.LibraryTopAppBar
import com.example.librarysystem.ui.damage.DamageTab
import com.example.librarysystem.ui.fines.FineTab
import android.content.res.Configuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowDeskScreen(
    onOpenDamageForm: (DamageCopy) -> Unit,
    flashNotice: String? = null,
    onFlashConsumed: () -> Unit = {},
    vm: BorrowDeskViewModel = viewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.snackbar) {
        vm.snackbar?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            vm.consumeSnackbar()
        }
    }

    LaunchedEffect(flashNotice) {
        if (!flashNotice.isNullOrBlank()) {
            vm.showMessage(flashNotice)
            onFlashConsumed()
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            if (!landscape) LibraryTopAppBar(title = "Borrow desk")
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                Tab(
                    selected = vm.tab == 2,
                    onClick = { vm.selectTab(2) },
                    text = { Text("Fines") }
                )
                Tab(
                    selected = vm.tab == 3,
                    onClick = { vm.selectTab(3) },
                    text = { Text("Damage") }
                )
            }

            when (vm.tab) {
                0 -> BorrowTab(vm)
                1 -> ReturnTab(vm)
                2 -> FineTab(
                    onCleared = { fine ->
                        vm.onFineCleared(
                            "Fine RM %.2f cleared for %s. Switch to Return to finish the return."
                                .format(fine.amount, fine.studentName.ifBlank { fine.studentNo })
                        )
                    }
                )
                else -> DamageTab(onOpenForm = onOpenDamageForm)
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
            onGoFines = vm::goToFines,
            onDismiss = vm::dismissFineGate
        )
    }
}

// -------------------------------------------------------------------- borrow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BorrowTab(vm: BorrowDeskViewModel) {
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().imePadding()) {
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp
                )
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                        color = if (card.unpaidFines > 0)
                                            MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (card.atLimit) {
                                        Text(
                                            "Loan + reservation limit reached (2). " +
                                                "No further borrowing.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        LabeledField(
                            label = "Copy number or ISBN",
                            value = vm.copyQuery,
                            onValueChange = vm::onCopyQueryChange,
                            placeholder = "e.g. C1 or 9781234567890",
                            maxLength = 100
                        )

                        if (vm.copyQuery.isBlank()) {
                            Text(
                                "Type or scan a copy number / ISBN to find a copy. " +
                                    "The full catalogue lives under Manage.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (vm.copiesLoading) {
                            Box(
                                Modifier.fillMaxWidth().height(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (vm.copies.isEmpty()) {
                            Text(
                                "No available copy matches.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(vm.copies, key = { it.copyId }) { copy ->
                    Spacer(Modifier.height(6.dp))
                    CopyOption(
                        copy = copy,
                        selected = vm.selectedCopy?.copyId == copy.copyId,
                        onClick = { vm.selectCopy(copy) }
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        BorrowActionBar(vm)
    }
}

@Composable
private fun BorrowActionBar(vm: BorrowDeskViewModel) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (landscape) 6.dp else 16.dp)
        ) {
            if (!landscape) {
                val selected = vm.selectedCopy
                if (selected != null) {
                    Text(
                        "${selected.bookTitle} · ${selected.copyNo}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            Button(
                onClick = vm::confirmBorrow,
                enabled = !vm.busy && vm.selectedCopy != null && vm.studentCard != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.busy) "Borrowing…" else "Borrow selected copy")
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReturnTab(vm: BorrowDeskViewModel) {
    val visible = vm.visibleLoans
    val listState = rememberLazyListState()

    PullToRefreshBox(
        isRefreshing = vm.refreshing,
        onRefresh = vm::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
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
                        if (visible.size == 1) "1 book on loan"
                        else "${visible.size} books on loan",
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
                        onDamage = { vm.selectTab(3) }
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
