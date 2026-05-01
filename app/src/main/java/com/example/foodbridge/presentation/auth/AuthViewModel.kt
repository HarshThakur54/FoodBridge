package com.example.foodbridge.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodbridge.data.repository.AuthRepository
import com.example.foodbridge.data.repository.AuthResult
import com.example.foodbridge.domain.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val selectedRole: UserRole = UserRole.DONOR,
    val selectedSubRole: String = "hotel",
    val navigateTo: String = "",
    val currentUserRole: String = "",
    val currentUserSubRole: String = "",
    val currentUserId: String = ""
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val isLoggedIn get() = authRepository.isLoggedIn

    init {
        loadCurrentUser()
    }

    fun loadCurrentUser() {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection("users").document(uid).get().await()
                val role = doc.getString("role") ?: "DONOR"
                val subRole = doc.getString("subRole") ?: "hotel"
                _uiState.value = _uiState.value.copy(
                    currentUserRole = role,
                    currentUserSubRole = subRole,
                    currentUserId = uid
                )
            } catch (_: Exception) {}
        }
    }

    fun onRoleSelected(role: UserRole, subRole: String) {
        _uiState.value = _uiState.value.copy(
            selectedRole = role,
            selectedSubRole = subRole
        )
    }

    fun signUp(
        email: String,
        password: String,
        fullName: String,
        addressLine1: String,
        addressLine2: String,
        city: String,
        state: String,
        pincode: String,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = null
            )
            val result = authRepository.signUp(
                email = email,
                password = password,
                fullName = fullName,
                role = _uiState.value.selectedRole,
                subRole = _uiState.value.selectedSubRole,
                addressLine1 = addressLine1,
                addressLine2 = addressLine2,
                city = city,
                state = state,
                pincode = pincode,
                latitude = latitude,
                longitude = longitude
            )
            when (result) {
                is AuthResult.Success -> {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val route = if (_uiState.value.selectedRole == UserRole.RECEIVER)
                        "ngo_dashboard" else "home"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true,
                        navigateTo = route,
                        currentUserRole = _uiState.value.selectedRole.name,
                        currentUserSubRole = _uiState.value.selectedSubRole,
                        currentUserId = uid
                    )
                }
                is AuthResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false, errorMessage = result.message, statusMessage = null
                )
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = null
            )
            val result = authRepository.login(email, password)
            when (result) {
                is AuthResult.Success -> {
                    try {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        val doc = FirebaseFirestore.getInstance()
                            .collection("users").document(uid).get().await()
                        val role = doc.getString("role") ?: "DONOR"
                        val subRole = doc.getString("subRole") ?: "hotel"
                        val route = if (role == "RECEIVER") "ngo_dashboard" else "home"
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isSuccess = true,
                            navigateTo = route,
                            currentUserRole = role,
                            currentUserSubRole = subRole,
                            currentUserId = uid
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, isSuccess = true, navigateTo = "home"
                        )
                    }
                }
                is AuthResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false, errorMessage = result.message, statusMessage = null
                )
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Enter your email address first.",
                statusMessage = null
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                statusMessage = null
            )
            when (val result = authRepository.sendPasswordResetEmail(cleanEmail)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = null,
                        statusMessage = "Password reset link sent to $cleanEmail"
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message,
                        statusMessage = null
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, statusMessage = null)
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = AuthUiState()
    }
}
