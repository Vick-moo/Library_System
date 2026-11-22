package com.example.librarysystem.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.librarysystem.data.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var code by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var info by mutableStateOf<String?>(null)
        private set

    /** Email awaiting verification (set after signUp). */
    var pendingEmail by mutableStateOf<String?>(null)
        private set

    /** True only if a code was actually sent for the pending email. */
    var codeSent by mutableStateOf(false)
        private set

    var loggedInEmail by mutableStateOf<String?>(null)
        private set
    var roles by mutableStateOf(setOf<String>())
        private set

    /** admin / librarian / assistant, or null for students/guests. */
    var staffRole by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            try {
                val em = AuthRepository.currentEmail()
                if (em != null) {
                    loggedInEmail = em
                    roles = AuthRepository.myRoles()
                    staffRole = AuthRepository.myStaffRole()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun updateEmail(v: String) {
        email = v
        error = null
    }

    fun updatePassword(v: String) {
        password = v
        error = null
    }

    fun updateCode(v: String) {
        if (v.length <= 6) code = v.filter { it.isDigit() }
        error = null
    }

    /** Clear inputs when a different auth screen is opened, so data never leaks across pages. */
    fun clearInputs() {
        email = ""
        password = ""
        code = ""
        error = null
        info = null
    }

    /** Verify screen keeps pendingEmail, but must not inherit a stale code or error. */
    fun clearVerifyInput() {
        code = ""
        error = null
    }

    private fun validEmail(): Boolean {
        if (!email.trim().contains("@")) {
            error = "Please enter a valid email"
            return false
        }
        return true
    }

    private fun validPassword(): Boolean {
        val p = password
        if (p.length < 8 || !p.any { it.isLetter() } || !p.any { it.isDigit() }) {
            error = "Password must be 8+ chars with letters and numbers"
            return false
        }
        return true
    }

    /** Never show raw SDK errors (they leak URLs and keys). */
    private fun friendlyError(e: Exception, fallback: String): String {
        val m = (e.message ?: "").lowercase()
        return when {
            "invalid login credentials" in m || "invalid_credentials" in m ->
                "Wrong email or password. Try again."
            "user already registered" in m ->
                "This email is already registered. Please login."
            "not confirmed" in m || "email not confirmed" in m ->
                "Please verify your email first. Check your inbox for the code."
            "rate limit" in m || "only request this after" in m || "too many" in m ->
                "Too many attempts. Please wait a minute and try again."
            "network" in m || "unable to resolve host" in m || "timeout" in m ->
                "Network error. Check your connection and try again."
            "email" in m && "invalid" in m ->
                "That email address does not look right."
            else -> fallback
        }
    }

    fun login(onDone: () -> Unit) {
        if (!validEmail() || !validPassword()) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                AuthRepository.signIn(email, password)
                loggedInEmail = AuthRepository.currentEmail()
                roles = AuthRepository.myRoles()
                onDone()
            } catch (e: Exception) {
                error = friendlyError(e, "Login failed")
            } finally {
                loading = false
            }
        }
    }

    fun register(onVerify: () -> Unit) {
        if (!validEmail() || !validPassword()) return
        loading = true
        error = null
        info = null
        viewModelScope.launch {
            try {
                if (AuthRepository.accountExists(email)) {
                    error = "This email is already registered. Please login."
                    return@launch
                }
                codeSent = false
                if (!AuthRepository.isWhitelisted(email)) {
                    info = "This email is not in the whitelist. Contact library staff."
                    pendingEmail = email.trim()
                    code = ""
                    onVerify()
                    return@launch
                }
                AuthRepository.signUp(email, password)
                pendingEmail = email.trim()
                code = ""
                codeSent = true
                onVerify()
            } catch (e: Exception) {
                error = friendlyError(e, "Registration failed")
            } finally {
                loading = false
            }
        }
    }

    fun verify(onDone: () -> Unit) {
        val em = pendingEmail ?: email
        if (code.length != 6) {
            error = "Enter the 6-digit code from your email"
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            try {
                AuthRepository.verifyCode(em, code)
                AuthRepository.finalizeRegistration()
                loggedInEmail = AuthRepository.currentEmail()
                roles = AuthRepository.myRoles()
                pendingEmail = null
                code = ""
                onDone()
            } catch (e: Exception) {
                error = friendlyError(e, "Verification failed")
            } finally {
                loading = false
            }
        }
    }

    fun resend() {
        val em = pendingEmail ?: email
        if (!em.contains("@")) {
            error = "Enter your email first"
            return
        }
        if (!codeSent) {
            info = "No code was sent for this email."
            return
        }
        loading = true
        error = null
        viewModelScope.launch {
            try {
                AuthRepository.signUp(em, password.ifEmpty { "TempPass123" })
                info = "Code resent. Check inbox and spam folder."
            } catch (e: Exception) {
                error = friendlyError(e, "Resend failed")
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                AuthRepository.signOut()
            } catch (_: Exception) {
            } finally {
                loggedInEmail = null
                roles = emptySet()
                staffRole = null
                pendingEmail = null
                codeSent = false
            }
            // If signOut failed, the session is still alive - show the truth instead of a fake guest state.
            refreshSession()
        }
    }

    /** Re-read the real Supabase session (fixes stale guest/logged-in state). */
    fun refreshSession() {
        viewModelScope.launch {
            try {
                val em = AuthRepository.currentEmail()
                loggedInEmail = em
                roles = if (em != null) {
                    try { AuthRepository.myRoles() } catch (_: Exception) { emptySet() }
                } else {
                    emptySet()
                }
                staffRole = if (em != null) {
                    try { AuthRepository.myStaffRole() } catch (_: Exception) { null }
                } else {
                    null
                }
            } catch (_: Exception) {
            }
        }
    }
}
