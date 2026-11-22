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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.model.ActiveLoan
import com.example.librarysystem.data.model.AvailableCopy
import com.example.librarysystem.ui.components.LibraryTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowDeskScreen(
    vm: BorrowDeskViewModel = viewModel()
) {
    Scaffold(
        topBar = { LibraryTopAppBar(title = "Borrow desk") },
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
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            vm.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (vm.tab == 0) {
                BorrowTab(vm)
            } else {
                ReturnTab(vm)
            }
        }
    }
}

@Composable
private fun BorrowTab(vm: BorrowDeskViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.studentNoInput,
                onValueChange = vm::onStudentNoChange,
                label = { Text("Student number") },
                placeholder = { Text("e.g. 23WMR00001") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = vm::searchStudent,
                enabled = !vm.studentSearching
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "On loan: ${card.activeLoans} / 2",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (card.unpaidFines > 0) "Unpaid fines: RM %.2f".format(card.unpaidFines)
                        else "No unpaid fines",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (card.unpaidFines > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedTextField(
            value = vm.copyQuery,
            onValueChange = vm::onCopyQueryChange,
            label = { Text("Find an available copy") },
            placeholder = { Text("copy no / title / ISBN") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (vm.copiesLoading) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
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
            onClick = { vm.confirmBorrow {} },
            enabled = !vm.busy && vm.selectedCopy != null && vm.studentCard != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.busy) "Borrowing…" else "Borrow selected copy")
        }
        Spacer(Modifier.height(16.dp))
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

@Composable
private fun ReturnTab(vm: BorrowDeskViewModel) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.loanQuery,
            onValueChange = vm::onLoanQueryChange,
            label = { Text("Search loans") },
            placeholder = { Text("student no / copy no / title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        if (vm.loansLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (vm.loans.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No active loans match.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.loans, key = { it.borrowId }) { loan ->
                    LoanRow(loan, busy = vm.busy) { vm.returnLoan(loan) }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun LoanRow(loan: ActiveLoan, busy: Boolean, onReturn: () -> Unit) {
    val overdue = loan.overdueDays > 0
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
                if (overdue) "Due ${loan.dueDate} · ${loan.overdueDays} day(s) late"
                else "Due ${loan.dueDate}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (overdue) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onReturn, enabled = !busy) {
            Text("Return")
        }
    }
}
