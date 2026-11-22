package com.example.librarysystem

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.ui.auth.AuthViewModel
import com.example.librarysystem.ui.auth.LoginScreen
import com.example.librarysystem.ui.auth.RegisterScreen
import com.example.librarysystem.ui.auth.VerifyScreen
import com.example.librarysystem.ui.books.BookDetailScreen
import com.example.librarysystem.ui.books.BooksScreen
import com.example.librarysystem.ui.books.CategoryScreen
import com.example.librarysystem.ui.manage.BookFormScreen
import com.example.librarysystem.ui.manage.BookManageScreen
import com.example.librarysystem.ui.manage.CopyEditScreen
import com.example.librarysystem.ui.manage.ManageScreen
import com.example.librarysystem.ui.borrow.BorrowDeskScreen
import com.example.librarysystem.ui.damage.DamageFormScreen
import com.example.librarysystem.ui.trends.TrendsScreen
import com.example.librarysystem.ui.home.HomeScreen
import com.example.librarysystem.ui.navigation.staffTabs
import com.example.librarysystem.ui.navigation.studentTabs
import com.example.librarysystem.ui.profile.ProfileScreen
import com.example.librarysystem.ui.profile.RequireLoginScreen
import com.example.librarysystem.ui.theme.LibrarySystemTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LibrarySystemTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot(authVm: AuthViewModel = viewModel()) {
    var stack by remember { mutableStateOf(listOf("home")) }
    var selectedBookId by remember { mutableStateOf<String?>(null) }
    var selectedCopyId by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    /** Copy being reported as damaged on the standalone damage form. */
    var selectedDamageCopy by remember { mutableStateOf<com.example.librarysystem.data.model.DamageCopy?>(null) }
    /** Bumped on every form entry so a fresh ViewModel is created (no stale draft). */
    var formSeq by remember { mutableStateOf(0) }
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    var staffMode by remember { mutableStateOf(false) }
    /** One-shot message shown when the staff lands back on the borrow desk. */
    var staffNotice by remember { mutableStateOf<String?>(null) }
    val current = stack.last()
    val isStudent = "student" in authVm.roles
    val isStaff = authVm.staffRole != null

    fun go(route: String) {
        stack = stack + route
    }
    fun openBook(id: String) {
        selectedBookId = id
        go("book")
    }
    fun goTab(route: String) {
        stack = listOf(route)
    }
    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    BackHandler(enabled = stack.size > 1) { back() }
    // Keep auth UI in sync with the real Supabase session on every screen change.
    LaunchedEffect(current) { authVm.refreshSession() }

    // Staff-only accounts go straight to the staff panel; dual-role accounts stay in student view.
    LaunchedEffect(authVm.loggedInEmail, isStaff, isStudent) {
        staffMode = isStaff && !isStudent
    }
    // If we just entered staff mode while sitting on a student tab, move to the borrow desk.
    LaunchedEffect(staffMode) {
        if (staffMode && current in setOf("home", "trends", "borrow", "seats")) {
            stack = listOf("borrow-desk")
        }
    }

    val inAuth = current == "login" || current == "register" || current == "verify"
    // Full-screen form pages hide the bottom bar so the keyboard never leaves a dead block above it.
    val hideBottomBar = inAuth || current in setOf(
        "manage-add", "manage-edit", "manage-copies",
        "manage-copy-edit", "manage-book", "category", "damage-form"
    )
    // Landscape phones have very little vertical room: icons only, no labels.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar {
                    if (staffMode) {
                        staffTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = { goTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = if (landscape) null else {
                                    { Text(tab.label) }
                                }
                            )
                        }
                    } else {
                        studentTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = {
                                    if (tab.route == "borrow" && authVm.loggedInEmail == null) {
                                        pendingRoute = "borrow"
                                        stack = listOf("login")
                                    } else {
                                        goTab(tab.route)
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = if (landscape) null else {
                                    { Text(tab.label) }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().padding(padding)
        ) {
            when (current) {
                "home" -> HomeScreen(
                    authKey = authVm.loggedInEmail ?: "guest",
                    onOpenBook = ::openBook,
                    onSeeAll = { cat ->
                        selectedCategory = cat
                        go("category")
                    }
                )
                "category" -> selectedCategory?.let { cat ->
                    CategoryScreen(
                        category = cat,
                        onBack = ::back,
                        onOpenBook = ::openBook
                    )
                }
                "login" -> LoginScreen(
                    vm = authVm,
                    onLoggedIn = {
                        val target = pendingRoute ?: "home"
                        pendingRoute = null
                        goTab(target)
                    },
                    onGoRegister = { go("register") },
                    onGuest = {
                        pendingRoute = null
                        goTab("home")
                    },
                    onBack = ::back
                )
                "register" -> RegisterScreen(
                    vm = authVm,
                    onVerify = { go("verify") },
                    onBackToLogin = { go("login") },
                    onBack = ::back
                )
                "verify" -> VerifyScreen(
                    vm = authVm,
                    onDone = { goTab("home") },
                    onBack = ::back
                )
                "trends" -> TrendsScreen(onOpenBook = ::openBook)
                "seats" -> BooksScreen("Seats", onOpenBook = ::openBook)
                "borrow" -> if (authVm.loggedInEmail == null) {
                    LaunchedEffect(Unit) { goTab("login") }
                } else {
                    BooksScreen("Borrow", onOpenBook = ::openBook)
                }
                "book" -> selectedBookId?.let { id ->
                    BookDetailScreen(
                        bookId = id,
                        authKey = authVm.loggedInEmail ?: "guest",
                        onBack = ::back,
                        onNeedLogin = { go("login") }
                    )
                }
                "borrow-desk" -> BorrowDeskScreen(
                    onOpenDamageForm = { copy ->
                        selectedDamageCopy = copy
                        go("damage-form")
                    },
                    flashNotice = staffNotice,
                    onFlashConsumed = { staffNotice = null }
                )
                "damage-form" -> selectedDamageCopy?.let { copy ->
                    DamageFormScreen(
                        copy = copy,
                        onBack = ::back,
                        onSaved = { message ->
                            staffNotice = message
                            back()
                        }
                    )
                }
                "manage" -> ManageScreen(
                    onAddBook = {
                        formSeq++
                        go("manage-add")
                    },
                    onOpenBook = { id ->
                        selectedBookId = id
                        go("manage-book")
                    }
                )
                "manage-book" -> selectedBookId?.let { id ->
                    BookManageScreen(
                        bookId = id,
                        onBack = ::back,
                        onEditBook = {
                            formSeq++
                            go("manage-edit")
                        },
                        onAddCopy = {
                            selectedCopyId = null
                            go("manage-copy-edit")
                        },
                        onEditCopy = { copyId ->
                            selectedCopyId = copyId
                            go("manage-copy-edit")
                        }
                    )
                }
                "manage-copy-edit" -> selectedBookId?.let { bookId ->
                    CopyEditScreen(
                        bookId = bookId,
                        copyId = selectedCopyId,
                        onBack = ::back,
                        onSaved = { back() }
                    )
                }
                "manage-add" -> BookFormScreen(
                    bookId = null,
                    formKey = formSeq,
                    onBack = ::back,
                    onSaved = { back() }
                )
                "manage-edit" -> selectedBookId?.let { id ->
                    BookFormScreen(
                        bookId = id,
                        formKey = formSeq,
                        onBack = ::back,
                        onSaved = { back() },
                        onDeleted = { goTab("manage") }
                    )
                }
                "profile" -> ProfileScreen(
                    loggedInEmail = authVm.loggedInEmail,
                    roles = authVm.roles,
                    staffRole = authVm.staffRole,
                    staffMode = staffMode,
                    onGoLogin = { go("login") },
                    onLogout = { authVm.logout() },
                    onSwitchMode = {
                        staffMode = !staffMode
                        goTab(if (staffMode) "borrow-desk" else "home")
                    }
                )
                else -> HomeScreen()
            }
        }
    }
}
