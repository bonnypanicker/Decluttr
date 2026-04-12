package com.tool.decluttr.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.GoogleAuthProvider
import com.tool.decluttr.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider

class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authProvider: Provider<FirebaseAuth>
) : AuthRepository {

    override val isUserLoggedIn: Flow<Boolean> = callbackFlow {
        val auth = firebaseAuthOrNull()
        if (auth == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        // Initial state
        trySend(auth.currentUser != null)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUserEmail: Flow<String?> = callbackFlow {
        val auth = firebaseAuthOrNull()
        if (auth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.email)
        }
        auth.addAuthStateListener(listener)
        // Initial state
        trySend(auth.currentUser?.email)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            val auth = firebaseAuthOrNull()
                ?: return Result.failure(IllegalStateException("Firebase Auth is not configured"))
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> {
        return try {
            val auth = firebaseAuthOrNull()
                ?: return Result.failure(IllegalStateException("Firebase Auth is not configured"))
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> {
        return try {
            val auth = firebaseAuthOrNull()
                ?: return Result.failure(IllegalStateException("Firebase Auth is not configured"))
            auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            val auth = firebaseAuthOrNull()
                ?: return Result.failure(IllegalStateException("Firebase Auth is not configured"))
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        firebaseAuthOrNull()?.signOut()
    }

    private fun firebaseAuthOrNull(): FirebaseAuth? {
        if (FirebaseApp.getApps(context).isEmpty()) {
            return null
        }
        return runCatching { authProvider.get() }.getOrNull()
    }
}
