package com.example.librarysystem.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.librarysystem.ui.components.LibraryTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onLoggedIn: () -> Unit,
    onGoRegister: () -> Unit,
    onGuest: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(topBar = {
        LibraryTopAppBar(title = "Login", onBack = onBack)
    }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Welcome back", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = vm.email,
                onValueChange = vm::updateEmail,
                label = { Text("School email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.password,
                onValueChange = vm::updatePassword,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.login(onLoggedIn) },
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (vm.loading) CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp
                ) else Text("Login")
            }
            TextButton(onClick = onGoRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("No account? Register")
            }
            TextButton(onClick = onGuest, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Continue as guest")
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    vm: AuthViewModel,
    onVerify: () -> Unit,
    onBackToLogin: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(topBar = {
        LibraryTopAppBar(title = "Register", onBack = onBack)
    }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Create your account", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text("Use your school email on the whitelist.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = vm.email,
                onValueChange = vm::updateEmail,
                label = { Text("School email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.password,
                onValueChange = vm::updatePassword,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            vm.info?.let { Text(it) }
            Button(
                onClick = { vm.register(onVerify) },
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (vm.loading) CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp
                ) else Text("Send code")
            }
            TextButton(onClick = onBackToLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Already have an account? Login")
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(
    vm: AuthViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(topBar = {
        LibraryTopAppBar(title = "Verify email", onBack = onBack)
    }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Enter the 6-digit code", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text("Sent to ${vm.pendingEmail ?: vm.email}. Check spam folder too.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = vm.code,
                onValueChange = vm::updateCode,
                label = { Text("6-digit code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            vm.info?.let { Text(it) }
            Button(
                onClick = { vm.verify(onDone) },
                enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (vm.loading) CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp
                ) else Text("Verify")
            }
            TextButton(
                onClick = vm::resend,
                enabled = !vm.loading,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Resend code")
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
