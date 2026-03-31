package com.tool.decluttr.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tool.decluttr.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _isLoginMode = MutableStateFlow(true)
    val isLoginMode = _isLoginMode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _infoMessage = MutableSharedFlow<String>()
    val infoMessage = _infoMessage.asSharedFlow()

    fun onEmailChange(newEmail: String) {
        _email.value = newEmail
        _errorMessage.value = null
    }

    fun onPasswordChange(newPassword: String) {
        _password.value = newPassword
        _errorMessage.value = null
    }

    fun toggleMode() {
        _isLoginMode.value = !_isLoginMode.value
        _errorMessage.value = null
    }

    fun authenticate() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val currentEmail = email.value.trim()
            val currentPassword = password.value

            if (currentEmail.isEmpty() || currentPassword.isEmpty()) {
                _errorMessage.value = "Email and password cannot be empty"
                _isLoading.value = false
                return@launch
            }

            val result = if (isLoginMode.value) {
                authRepository.signIn(currentEmail, currentPassword)
            } else {
                authRepository.signUp(currentEmail, currentPassword)
            }

            result.onFailure {
                _errorMessage.value = it.localizedMessage ?: "Authentication failed"
            }

            _isLoading.value = false
        }
    }

    fun sendPasswordReset() {
        viewModelScope.launch {
            val currentEmail = email.value.trim()
            if (currentEmail.isEmpty()) {
                _errorMessage.value = "Enter your email first"
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null

            authRepository.sendPasswordResetEmail(currentEmail)
                .onSuccess {
                    _infoMessage.emit("Reset email sent to $currentEmail")
                }
                .onFailure {
                    _errorMessage.value = it.localizedMessage ?: "Failed to send reset email"
                }

            _isLoading.value = false
        }
    }
}
