package com.example.librarysystem.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.data.AuthRepository
import com.example.librarysystem.data.StudentSelfRepository
import com.example.librarysystem.data.model.StudentAlert
import com.example.librarysystem.ui.books.BooksViewModel
import com.example.librarysystem.ui.books.BooksUiState
import com.example.librarysystem.ui.components.BookFilter
import com.example.librarysystem.ui.components.CategoryRow
import com.example.librarysystem.ui.components.DueBanner
import com.example.librarysystem.ui.components.FilterPanel
import com.example.librarysystem.ui.components.GreetingBlock
import com.example.librarysystem.ui.components.MyLoansDialog
import com.example.librarysystem.ui.components.PromoBannerCarousel
import com.example.librarysystem.ui.components.SearchTopBar
import com.example.librarysystem.ui.components.applyBookFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: BooksViewModel = viewModel(),
    authKey: String = "guest",
    onOpenBook: (String) -> Unit = {},
    onSeeAll: (String) -> Unit = {}
) {
    var displayName by remember(authKey) { mutableStateOf<String?>(null) }
    var alert by remember(authKey) { mutableStateOf<StudentAlert?>(null) }
    var showLoans by remember { mutableStateOf(false) }
    LaunchedEffect(authKey) {
        displayName = try { AuthRepository.myFullName() } catch (_: Exception) { null }
        // Guests have no student row, so there is nothing to fetch (and RLS would return nothing).
        alert = if (authKey == "guest") null
        else try { StudentSelfRepository.myAlert() } catch (_: Exception) { null }
    }
    Scaffold(
        topBar = {
            SearchTopBar(
                query = vm.query,
                onQueryChange = { v ->
                    vm.onQueryChange(v)
                    vm.load()
                }
            )
        }
    ) { padding ->
        when (val s = vm.state) {
            is BooksUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is BooksUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        s.message,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.height(12.dp)
                    )
                    androidx.compose.material3.Button(onClick = { vm.load() }) {
                        Text("Try again")
                    }
                }
            }

            is BooksUiState.Ready -> {
                var filter by remember { mutableStateOf(BookFilter()) }
                val books = applyBookFilter(
                    s.books, vm.ratings, vm.availableIds, filter, vm.rankingIds
                )
                val categories = s.books.mapNotNull { it.category }.distinct().sorted()
                val languages = s.books.mapNotNull { it.language }.distinct().sorted()
                val groups = if (filter.ranking != "None") {
                    linkedMapOf(filter.ranking to books)
                } else {
                    books.groupBy { it.category ?: "Other" }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    item { GreetingBlock(displayName) }
                    item { PromoBannerCarousel() }
                    // Due reminder sits *below* the promo banner, not above it (user preference).
                    alert?.takeIf { !it.isEmpty }?.let { banner ->
                        item { DueBanner(banner) { showLoans = true } }
                    }
                    item {
                        FilterPanel(
                            categories = categories,
                            languages = languages,
                            filter = filter,
                            onFilterChange = { next ->
                                if (next.ranking != filter.ranking) {
                                    vm.loadRanking(next.ranking)
                                }
                                filter = next
                            }
                        )
                    }
                    groups.forEach { (cat, list) ->
                        item(key = "cat_$cat") {
                            CategoryRow(
                                title = cat,
                                books = list,
                                ratings = vm.ratings,
                                onSeeAll = { onSeeAll(cat) },
                                onBookClick = { onOpenBook(it.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLoans) {
        alert?.let { MyLoansDialog(it) { showLoans = false } }
    }
}
