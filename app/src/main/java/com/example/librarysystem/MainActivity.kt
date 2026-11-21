package com.example.librarysystem

import android.os.Bundle
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.librarysystem.ui.auth.AuthViewModel
import com.example.librarysystem.ui.auth.LoginScreen
import com.example.librarysystem.ui.auth.RegisterScreen
import com.example.librarysystem.ui.auth.VerifyScreen
import com.example.librarysystem.ui.books.BookDetailScreen
import com.example.librarysystem.ui.books.BooksScreen
import com.example.librarysystem.ui.trends.TrendsScreen
import com.example.librarysystem.ui.home.HomeScreen
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
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    val current = stack.last()
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
    val inAuth = current == "login" || current == "register" || current == "verify"
    Scaffold(
        bottomBar = {
            if (!inAuth) {
                NavigationBar {
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
                            label = { Text(tab.label) }
                        )
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
                    onOpenBook = ::openBook
                )
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
                "profile" -> ProfileScreen(
                    loggedInEmail = authVm.loggedInEmail,
                    roles = authVm.roles,
                    onGoLogin = { go("login") },
                    onLogout = { authVm.logout() }
                )
                else -> HomeScreen()
            }
        }
    }
}
