package com.example.librarysystem.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.librarysystem.ui.components.LibraryTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    loggedInEmail: String?,
    roles: Set<String>,
    onGoLogin: () -> Unit,
    onLogout: () -> Unit,
    staffRole: String? = null,
    staffMode: Boolean = false,
    onSwitchMode: () -> Unit = {}
) {
    val isStudent = "student" in roles
    val canSwitch = staffRole != null && isStudent

    Scaffold(
        topBar = {
            LibraryTopAppBar(title = if (staffMode) "Staff profile" else "Profile")
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loggedInEmail == null) {
                Text(
                    "You are browsing as guest",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Login to borrow, reserve and review.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onGoLogin, modifier = Modifier.fillMaxWidth()) {
                    Text("Login / Register")
                }
            } else {
                Text(
                    if (staffMode) "Signed in as staff" else "Signed in",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(loggedInEmail, style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (staffRole != null) {
                        AssistChip(
                            onClick = {},
                            label = { Text("staff · $staffRole") }
                        )
                    }
                    if (isStudent) {
                        AssistChip(onClick = {}, label = { Text("student") })
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (canSwitch) {
                    OutlinedButton(
                        onClick = onSwitchMode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (staffMode) "Switch to student view"
                            else "Switch to staff panel"
                        )
                    }
                }

                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("Logout")
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequireLoginScreen(onGoLogin: () -> Unit) {
    Scaffold(
        topBar = {
            LibraryTopAppBar(title = "Login required")
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Please login to continue.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onGoLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Login")
            }
        }
    }
}
