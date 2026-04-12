package com.tool.decluttr.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val isUserLoggedIn: Flow<Boolean>
    val currentUserEmail: Flow<String?>

    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit>
    suspend fun signUp(email: String, password: String): Result<Unit>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun signOut()
}
