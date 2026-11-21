package com.example.librarysystem.ui.trends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.BookRepository
import com.example.librarysystem.data.model.TrendingBook
import kotlinx.coroutines.launch

enum class TrendMetric(val api: String, val label: String) {
    RATED("rated", "Highest Rated"),
    BORROWED("borrowed", "Most Borrowed"),
    REVIEWED("reviewed", "Most Reviewed")
}

data class TrendFilter(
    /** null = All Time, 7 = This Week, 30 = This Month */
    val days: Int? = null,
    val category: String? = null,
    val language: String? = null
) {
    val timeLabel: String
        get() = when (days) {
            7 -> "This Week"
            30 -> "This Month"
            else -> "All Time"
        }
}

class TrendsViewModel : ViewModel() {

    var metric by mutableStateOf(TrendMetric.RATED)
        private set

    var filter by mutableStateOf(TrendFilter())
        private set

    var items by mutableStateOf<List<TrendingBook>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var categories by mutableStateOf<List<String>>(emptyList())
        private set

    var languages by mutableStateOf<List<String>>(emptyList())
        private set

    init {
        loadOptions()
        load()
    }

    fun selectMetric(value: TrendMetric) {
        if (metric == value) return
        metric = value
        load()
    }

    /** Apply metric + filter in one reload (used by the filter sheet). */
    fun apply(metric: TrendMetric, filter: TrendFilter) {
        this.metric = metric
        this.filter = filter
        load()
    }

    fun applyFilter(value: TrendFilter) {
        filter = value
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                items = BookRepository.trendingBooks(
                    metric = metric.api,
                    days = filter.days,
                    category = filter.category,
                    language = filter.language
                )
            } catch (e: Exception) {
                android.util.Log.e("TrendsVM", "trending_books failed: " + e.message, e)
                error = "Could not load the rankings. Pull to refresh."
                items = emptyList()
            } finally {
                loading = false
            }
        }
    }

    private fun loadOptions() {
        viewModelScope.launch {
            try {
                val books = BookRepository.listBooks()
                categories = books.mapNotNull { it.category }.distinct().sorted()
                languages = books.mapNotNull { it.language }.distinct().sorted()
            } catch (_: Exception) {
                // keep empty; the sheet still offers All
            }
        }
    }
}
