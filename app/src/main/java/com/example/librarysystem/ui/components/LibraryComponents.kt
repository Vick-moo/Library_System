package com.example.librarysystem.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.librarysystem.data.model.Book
import kotlin.math.roundToInt

/** Fixed warm-orange banner: readable on both light and dark theme. */
private val BannerOrange = Color(0xFF9E5100)

data class BookFilter(
    val category: String = "All",
    val language: String = "All",
    val pages: String = "All",
    val minRating: Double = 0.0,
    val onlyAvailable: Boolean = false,
    val sort: String = "Title A–Z"
)

fun applyBookFilter(
    books: List<Book>,
    ratings: Map<String, Pair<Double, Int>>,
    availableIds: Set<String>,
    filter: BookFilter
): List<Book> {
    var out = books
    if (filter.category != "All") out = out.filter { it.category == filter.category }
    if (filter.language != "All") out = out.filter { it.language == filter.language }
    out = when (filter.pages) {
        "< 200" -> out.filter { (it.pages ?: Int.MAX_VALUE) < 200 }
        "200–400" -> out.filter { (it.pages ?: -1) in 200..400 }
        "400+" -> out.filter { (it.pages ?: -1) > 400 }
        else -> out
    }
    if (filter.minRating > 0 && ratings.isNotEmpty()) {
        out = out.filter { (ratings[it.id]?.first ?: 0.0) >= filter.minRating }
    }
    if (filter.onlyAvailable && availableIds.isNotEmpty()) {
        out = out.filter { it.id in availableIds }
    }
    out = when (filter.sort) {
        "Newest" -> out.sortedByDescending { it.createdAt ?: "" }
        else -> out.sortedBy { it.title.lowercase() }
    }
    return out
}

@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.inverseSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "S",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text("Search books, authors, ISBN...", maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            singleLine = true,
            maxLines = 1,
            modifier = Modifier.weight(1f).height(56.dp)
        )
    }
}

@Composable
fun GreetingBlock(name: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Good morning,", style = MaterialTheme.typography.bodyMedium)
        Text(name ?: "Guest", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
    }
}

private data class BannerItem(val kicker: String, val title: String, val sub: String)

@Composable
fun PromoBannerCarousel() {
    val banners = remember {
        listOf(
            BannerItem("Announcement", "Hot Books This Week", "See what everyone is reading"),
            BannerItem("New Arrivals", "Fresh on the Shelves", "Check out this month's picks"),
            BannerItem("Reminder", "Return on Time", "Avoid fines: renew in 2 taps")
        )
    }
    val pagerState = rememberPagerState(pageCount = { banners.size })
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) { page ->
            Card(
                colors = CardDefaults.cardColors(containerColor = BannerOrange)
            ) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(banners[page].kicker,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium)
                    Text(banners[page].title, color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(banners[page].sub,
                            color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(banners.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                        .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                )
            }
        }
    }
}

@Composable
fun DueBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "2 books due in 3 days",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(
    categories: List<String>,
    languages: List<String>,
    filter: BookFilter,
    onFilterChange: (BookFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Button(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.FilterList, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Filter")
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterSection(
                        title = "Category",
                        options = listOf("All") + categories,
                        selected = filter.category,
                        onSelect = { onFilterChange(filter.copy(category = it)) }
                    )
                    FilterSection(
                        title = "Language",
                        options = listOf("All") + languages,
                        selected = filter.language,
                        onSelect = { onFilterChange(filter.copy(language = it)) }
                    )
                    FilterSection(
                        title = "Pages",
                        options = listOf("All", "< 200", "200–400", "400+"),
                        selected = filter.pages,
                        onSelect = { onFilterChange(filter.copy(pages = it)) }
                    )
                    RatingSlider(
                        value = filter.minRating,
                        onChange = { onFilterChange(filter.copy(minRating = it)) }
                    )
                    Column {
                        Text("Availability", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = filter.onlyAvailable,
                                onCheckedChange = {
                                    onFilterChange(filter.copy(onlyAvailable = it))
                                }
                            )
                            Text("Available now",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    FilterSection(
                        title = "Sort by",
                        options = listOf("Title A–Z", "Newest"),
                        selected = filter.sort,
                        onSelect = { onFilterChange(filter.copy(sort = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingSlider(
    value: Double,
    onChange: (Double) -> Unit
) {
    var slider by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val stepped = (slider * 2).roundToInt() / 2f
    Column {
        Text("Rating", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = slider,
                onValueChange = {
                    slider = it
                    onChange(((it * 2).roundToInt() / 2f).toDouble())
                },
                valueRange = 0f..5f,
                steps = 9,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (stepped <= 0f) "Any" else "%.1f+ ★".format(stepped),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = selected == opt,
                    onClick = { onSelect(opt) },
                    label = { Text(opt) }
                )
            }
        }
    }
}

@Composable
fun CategoryRow(
    title: String,
    books: List<Book>,
    ratings: Map<String, Pair<Double, Int>> = emptyMap(),
    onSeeAll: () -> Unit = {},
    onBookClick: (Book) -> Unit = {}
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.clickable { onSeeAll() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "See all",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            items(books.size) { idx ->
                val b = books[idx]
                val r = ratings[b.id]
                MiniBookCard(b, r?.first, r?.second ?: 0, onClick = { onBookClick(b) })
            }
        }
    }
}

@Composable
fun MiniBookCard(
    book: Book,
    rating: Double? = null,
    reviewCount: Int = 0,
    onClick: () -> Unit = {}
) {
    Card(modifier = Modifier.width(110.dp).clickable { onClick() }) {
        Column(Modifier.padding(8.dp)) {
            val cover = book.coverUrl
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        book.title.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold)
            Text(book.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall)
            Text(
                if (reviewCount > 0) "%.1f ★ %d reviews".format(rating ?: 0.0, reviewCount)
                else "No ratings yet",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
