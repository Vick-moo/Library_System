package com.example.librarysystem.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Tab("home", "Home", Icons.Filled.Home)
    data object Borrow : Tab("borrow", "Borrow", Icons.Filled.AutoStories)
    data object Seats : Tab("seats", "Seats", Icons.Filled.EventSeat)
    data object Profile : Tab("profile", "Profile", Icons.Filled.AccountCircle)
}

val studentTabs = listOf(Tab.Home, Tab.Borrow, Tab.Seats, Tab.Profile)

sealed class StaffTab(val route: String, val label: String, val icon: ImageVector) {
    data object Borrow : StaffTab("borrow-desk", "Borrow", Icons.Filled.SwapHoriz)
    data object Manage : StaffTab("manage", "Manage", Icons.Filled.MenuBook)
    data object Profile : StaffTab("profile", "Profile", Icons.Filled.AccountCircle)
}

val staffTabs = listOf(StaffTab.Borrow, StaffTab.Manage, StaffTab.Profile)
