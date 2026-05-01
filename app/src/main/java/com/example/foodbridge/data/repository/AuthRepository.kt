package com.example.foodbridge.data.repository

import com.example.foodbridge.domain.model.User
import com.example.foodbridge.domain.model.UserRole
import com.example.foodbridge.util.buildAddress
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val currentUser get() = auth.currentUser
    val isLoggedIn  get() = auth.currentUser != null

    suspend fun signUp(
        email: String, password: String,
        fullName: String,
        role: UserRole,
        subRole: String,
        addressLine1: String,
        addressLine2: String,
        city: String,
        state: String,
        pincode: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid    = result.user?.uid ?: return AuthResult.Error("UID is null")
            val user = User(
                uid = uid,
                fullName = fullName,
                email = email,
                role = role,
                subRole = subRole,
                addressLine1 = addressLine1,
                addressLine2 = addressLine2,
                city = city,
                state = state,
                pincode = pincode,
                latitude = latitude ?: 0.0,
                longitude = longitude ?: 0.0,
                address = buildAddress(addressLine1, addressLine2, city, state, pincode)
            )
            firestore.collection("users").document(uid).set(user).await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Signup failed")
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            AuthResult.Success
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            val message = when (e.errorCode) {
                "ERROR_WRONG_PASSWORD",
                "ERROR_INVALID_CREDENTIAL" -> "Incorrect password"
                "ERROR_INVALID_EMAIL" -> "Invalid email address"
                else -> "Incorrect email or password"
            }
            AuthResult.Error(message)
        } catch (e: FirebaseAuthInvalidUserException) {
            val message = when (e.errorCode) {
                "ERROR_USER_NOT_FOUND" -> "No account found with this email"
                "ERROR_USER_DISABLED" -> "This account has been disabled"
                else -> "Unable to log in with this account"
            }
            AuthResult.Error(message)
        } catch (e: Exception) {
            AuthResult.Error("Login failed. Please try again.")
        }
    }

    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Password reset failed")
        }
    }

    fun logout() = auth.signOut()
}
