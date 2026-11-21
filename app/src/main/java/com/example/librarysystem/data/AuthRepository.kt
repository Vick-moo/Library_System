package com.example.librarysystem.data

import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class AccountIdRow(val account_id: String)

@Serializable
private data class StudentNoRow(val student_no: String)

@Serializable
private data class StaffNoRow(val staff_no: String)

@Serializable
private data class FullNameRow(@SerialName("full_name") val fullName: String?)

object AuthRepository {

    /** Whitelist check works for guests (SECURITY DEFINER, granted to anon). */
    suspend fun isWhitelisted(email: String): Boolean {
        val res = supabase.postgrest.rpc(
            "is_whitelisted",
            mapOf("p_email" to email.trim())
        )
        return res.decodeAs<Boolean>()
    }

    /** True if an auth account already exists (in that case no email is sent). */
    suspend fun accountExists(email: String): Boolean {
        val res = supabase.postgrest.rpc(
            "account_exists",
            mapOf("p_email" to email.trim())
        )
        return res.decodeAs<Boolean>()
    }

    suspend fun signUp(email: String, password: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun verifyCode(email: String, code: String) {
        supabase.auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL,
            email = email.trim(),
            token = code.trim()
        )
    }

    /** Creates account + student rows from the whitelist. Call after verify. Returns student_no. */
    suspend fun finalizeRegistration(): String {
        return supabase.postgrest.rpc("finalize_registration").decodeAs<String>()
    }

    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    fun currentEmail(): String? =
        supabase.auth.currentSessionOrNull()?.user?.email

    /** Full name of the logged-in user, or null for guests. */
    suspend fun myFullName(): String? {
        val email = currentEmail() ?: return null
        return supabase.from("account")
            .select(columns = Columns.list("full_name")) {
                filter { eq("email", email) }
            }
            .decodeSingleOrNull<FullNameRow>()?.fullName
    }

    /** student_no of the logged-in student, or null for guests / staff-only accounts. */
    suspend fun myStudentNo(): String? {
        val email = currentEmail() ?: return null
        val acc = supabase.from("account")
            .select(columns = Columns.list("account_id")) {
                filter { eq("email", email) }
            }
            .decodeSingleOrNull<AccountIdRow>() ?: return null
        return supabase.from("student")
            .select(columns = Columns.list("student_no")) {
                filter { eq("account_id", acc.account_id) }
            }
            .decodeSingleOrNull<StudentNoRow>()?.student_no
    }

    /** Roles of the current user: subset of {"student", "staff"}. */
    suspend fun myRoles(): Set<String> {
        val email = currentEmail() ?: return emptySet()
        val roles = mutableSetOf<String>()
        val acc = supabase.from("account")
            .select(columns = Columns.list("account_id")) {
                filter { eq("email", email) }
            }
            .decodeSingleOrNull<AccountIdRow>() ?: return roles
        val isStudent = supabase.from("student")
            .select(columns = Columns.list("student_no")) {
                filter { eq("account_id", acc.account_id) }
            }
            .decodeList<StudentNoRow>().isNotEmpty()
        if (isStudent) roles.add("student")
        val isStaff = supabase.from("staff")
            .select(columns = Columns.list("staff_no")) {
                filter { eq("account_id", acc.account_id) }
            }
            .decodeList<StaffNoRow>().isNotEmpty()
        if (isStaff) roles.add("staff")
        return roles
    }
}
