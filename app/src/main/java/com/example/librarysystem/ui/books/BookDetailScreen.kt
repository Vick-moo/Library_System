package com.example.librarysystem.ui.books

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.librarysystem.data.model.Book
import com.example.librarysystem.data.model.BookReviewRow
import com.example.librarysystem.data.model.Reservation
import com.example.librarysystem.data.model.Review
import com.example.librarysystem.ui.components.LibraryTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    authKey: String,
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
    vm: BookDetailViewModel = viewModel(key = "$bookId-$authKey") { BookDetailViewModel(bookId) }
) {
    var showReviewDialog by remember { mutableStateOf(false) }
    var showReserveDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Re-entering the same book reuses the cached ViewModel, so refresh silently.
    LaunchedEffect(Unit) {
        if (vm.state is BookDetailUiState.Ready) vm.refresh()
    }

    // Show every action result as a toast so it is visible even after scrolling.
    LaunchedEffect(vm.notice) {
        vm.notice?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            LibraryTopAppBar(title = "Book details", onBack = onBack)
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when (val s = vm.state) {
            is BookDetailUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is BookDetailUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
            }

            is BookDetailUiState.Ready -> {
                PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                    item { DetailHeader(s.book, s.reviews) }
                    item { AvailabilityCard(s.availableCopies, s.totalCopies, s.shelves) }
                    s.myReservation?.let { res ->
                        item {
                            ReservationCard(
                                res = res,
                                queuePosition = s.queuePosition
                            )
                        }
                    }
                    item {
                        ActionBlock(
                            loggedIn = vm.studentNo != null,
                            reserved = s.myReservation != null,
                            hasReview = s.myReview != null,
                            availableCopies = s.availableCopies,
                            canReserve = s.canReserve,
                            activeBooks = s.activeBooks,
                            busy = vm.actionLoading,
                            onLogin = onNeedLogin,
                            onReserve = { showReserveDialog = true },
                            onCancelReservation = vm::cancelReservation,
                            onWriteReview = { showReviewDialog = true }
                        )
                    }
                    vm.notice?.let { note ->
                        item {
                            Text(
                                note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    item { DescriptionBlock(s.book) }
                    item { ReviewsHeader(s.reviews.size) }
                    if (s.reviews.isEmpty()) {
                        item {
                            Text(
                                "No reviews yet. Be the first to review this book.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        items(s.reviews, key = { it.id }) { r ->
                            ReviewRow(
                                review = r,
                                busy = vm.actionLoading,
                                reported = r.id in vm.reportedIds,
                                onDelete = vm::deleteReview,
                                onReport = { vm.reportReview(r.id) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                }

                if (showReviewDialog) {
                    ReviewDialog(
                        existing = s.myReview,
                        busy = vm.actionLoading,
                        onDismiss = { showReviewDialog = false },
                        onSubmit = { rating, comment ->
                            vm.submitReview(rating, comment) { showReviewDialog = false }
                        }
                    )
                }

                if (showReserveDialog) {
                    ReserveDialog(
                        window = s.pickupWindow,
                        busy = vm.actionLoading,
                        onDismiss = { showReserveDialog = false },
                        onSubmit = { note ->
                            showReserveDialog = false
                            vm.reserve(note)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(book: Book, reviews: List<BookReviewRow>) {
    Row(Modifier.fillMaxWidth()) {
        val cover = book.coverUrl
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(110.dp)
                    .height(165.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(165.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    book.title.take(1).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(book.author, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (reviews.isNotEmpty()) {
                val avg = reviews.map { it.rating }.average()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "%.1f".format(avg),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${reviews.size} reviews",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "No ratings yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            val meta = listOfNotNull(
                book.category,
                book.language,
                book.pages?.let { "$it pages" }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AvailabilityCard(available: Int, total: Int, shelves: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (available > 0) "$available of $total copies available"
                else "All copies are out",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (available > 0) "Ask at the counter to borrow one."
                else "Reserve it and we will hold a copy for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (available > 0 && shelves.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Shelf: " + shelves.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ReservationCard(
    res: Reservation,
    queuePosition: Int?
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (res.status == "ready") "Ready for pickup"
                else "Reserved — waiting for a copy",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (res.status == "waiting" && queuePosition != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "You are #$queuePosition in the queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            res.preferredPickupDate?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Planning to pick up: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val start = res.pickupStartDate
            val end = res.pickupEndDate
            if (start != null && end != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pick up between $start and $end",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            res.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your note: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActionBlock(
    loggedIn: Boolean,
    reserved: Boolean,
    hasReview: Boolean,
    availableCopies: Int,
    canReserve: Boolean,
    activeBooks: Int,
    busy: Boolean,
    onLogin: () -> Unit,
    onReserve: () -> Unit,
    onCancelReservation: () -> Unit,
    onWriteReview: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!loggedIn) {
            if (availableCopies > 0) {
                Text(
                    "$availableCopies ${if (availableCopies == 1) "copy" else "copies"} on the shelf. " +
                        "Login to borrow at the counter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reserve this book")
                }
            }
            OutlinedButton(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Write a review")
            }
        } else {
            if (reserved) {
                OutlinedButton(
                    onClick = onCancelReservation,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel reservation", color = MaterialTheme.colorScheme.error)
                }
            } else if (availableCopies > 0) {
                Text(
                    "$availableCopies ${if (availableCopies == 1) "copy" else "copies"} on the shelf. " +
                        "Borrow it at the counter — no reservation needed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (!canReserve) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Limit reached (2 books)")
                }
                Text(
                    "You already have $activeBooks books borrowed or reserved. " +
                        "Return one before reserving another.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Button(
                    onClick = onReserve,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reserve this book")
                }
            }
            OutlinedButton(
                onClick = onWriteReview,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (hasReview) "Edit review" else "Write a review")
            }
        }
    }
}

@Composable
private fun DescriptionBlock(book: Book) {
    Column {
        Text("About this book", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            book.description?.takeIf { it.isNotBlank() } ?: "No description for this book yet.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "ISBN ${book.isbn}" +
                (book.publisher?.let { " · $it" } ?: "") +
                (book.publishYear?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReviewsHeader(count: Int) {
    Column {
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text("Reviews ($count)", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Layout:
 *   row 1: student name (left) + Delete (own) / Report (others) on the right
 *   row 2: date
 *   row 3: stars + "4 star"
 *   row 4: comment
 *   divider
 */
@Composable
private fun ReviewRow(
    review: BookReviewRow,
    busy: Boolean,
    reported: Boolean,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                review.fullName + if (review.isMine) " (You)" else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (review.isMine) {
                TextButton(onClick = onDelete, enabled = !busy) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onReport, enabled = !busy && !reported) {
                    Text(if (reported) "Reported" else "Report")
                }
            }
        }
        Text(
            review.createdAt?.take(10) ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { i ->
                Icon(
                    if (i < review.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "${review.rating} star",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        review.comment?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ReserveDialog(
    window: Pair<String, String>?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reserve this book") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Pickup window", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (window != null) "${window.first} to ${window.second}"
                    else "We will tell you when a copy is ready.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "The window starts the day after the previous borrower returns the book, " +
                        "and you get 3 days to collect it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 200) note = it },
                    label = { Text("Message for the librarian (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(note) },
                enabled = !busy
            ) { Text("Reserve") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReviewDialog(
    existing: Review?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var rating by remember { mutableIntStateOf(existing?.rating ?: 5) }
    var comment by remember { mutableStateOf(existing?.comment.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Write a review" else "Edit review") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    repeat(5) { i ->
                        Icon(
                            if (i < rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "${i + 1} star",
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { rating = i + 1 },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 500) comment = it },
                    label = { Text("Your thoughts (optional)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(rating, comment) }, enabled = !busy) {
                Text(if (existing == null) "Post" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}
