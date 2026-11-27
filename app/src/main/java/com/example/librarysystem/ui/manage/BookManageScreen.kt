package com.example.librarysystem.ui.manage

import com.example.librarysystem.data.LabelItem

import com.example.librarysystem.data.LabelCart

import android.widget.Toast

import androidx.compose.ui.platform.LocalContext

import androidx.compose.material.icons.filled.AddShoppingCart

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.librarysystem.data.BookRepository
import com.example.librarysystem.data.model.Book
import com.example.librarysystem.data.model.BookCopy
import com.example.librarysystem.ui.components.LibraryTopAppBar
import kotlinx.coroutines.launch

class BookManageViewModel(private val bookId: String) : ViewModel() {
    var book by mutableStateOf<Book?>(null)
        private set
    var copies by mutableStateOf<List<BookCopy>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    init {
        load()
    }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                book = BookRepository.bookById(bookId)
                copies = BookRepository.copiesForStaff(bookId)
                error = null
            } catch (e: Exception) {
                error = "Could not load this book."
            } finally {
                refreshing = false
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                book = BookRepository.bookById(bookId)
                copies = BookRepository.copiesForStaff(bookId)
            } catch (e: Exception) {
                error = "Could not load this book."
            } finally {
                loading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookManageScreen(
    bookId: String,
    onBack: () -> Unit,
    onEditBook: () -> Unit,
    onAddCopy: () -> Unit,
    onEditCopy: (String) -> Unit,
    vm: BookManageViewModel = viewModel(key = "bookmanage-$bookId") { BookManageViewModel(bookId) }
) {
    var qrCopy by remember { mutableStateOf<BookCopy?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                title = "Manage book",
                onBack = onBack,
                actions = {
                    TextButton(onClick = onEditBook) { Text("Edit") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCopy) {
                Icon(Icons.Filled.Add, contentDescription = "Add copy")
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        when {
            vm.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            vm.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(vm.error ?: "") }

            else -> {
                val book = vm.book ?: return@Scaffold
                PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { BookHeader(book) }
                    item {
                        Text(
                            "Copies (${vm.copies.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (vm.copies.isEmpty()) {
                        item {
                            Text(
                                "No copies yet. Tap + to add one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(vm.copies, key = { it.id }) { copy ->
                            CopyRow(
                                copy = copy,
                                isbn = book.isbn,
                                onClick = { onEditCopy(copy.id) },
                                onQr = { qrCopy = copy },
                                onAdd = {
                                    val added = LabelCart.add(
                                        LabelItem(
                                            isbn = book.isbn,
                                            copyNo = copy.copyNo,
                                            bookTitle = book.title,
                                            coverUrl = book.coverUrl
                                        )
                                    )
                                    Toast.makeText(
                                        context,
                                        if (added) "Added to label cart"
                                        else "Already in the label cart",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                            HorizontalDivider(thickness = 1.dp)
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
                }
            }
        }
    }

    qrCopy?.let { copy ->
        vm.book?.let { book ->
            QrLabelDialog(
                title = book.title,
                subtitle = "Copy ${copy.copyNo}",
                isbn = book.isbn,
                copyNo = copy.copyNo,
                onDismiss = { qrCopy = null }
            )
        }
    }
}

@Composable
private fun BookHeader(book: Book) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(126.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val cover = book.coverUrl
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    book.title.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(book.author, style = MaterialTheme.typography.bodySmall)
            Text(
                "ISBN ${book.isbn}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${book.category} · ${book.language} · ${book.pages} pages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "RM %.2f".format(book.price ?: 0.0),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CopyRow(
    copy: BookCopy,
    isbn: String?,
    onClick: () -> Unit,
    onQr: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                copy.copyNo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${copy.condition} · ${copy.status} · Shelf ${copy.shelfLocation ?: "-"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            copy.notes?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            if (copy.isDeleted) "removed" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
        if (!copy.isDeleted) {
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Filled.AddShoppingCart,
                    contentDescription = "Add label to cart",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onQr) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = "QR label for ${copy.copyNo}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
