package com.tool.decluttr.presentation.screens.auth

// Tries native Credential Manager first; falls back to web-based OAuthProvider sign-in.

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.fragment.app.FragmentActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

object GoogleSignInHelper {

    sealed interface Result {
        data class NativeToken(val idToken: String, val rawNonce: String) : Result
        data object WebSignedIn : Result
        data object Canceled : Result
        data class Failed(val error: Throwable) : Result
    }

    suspend fun signIn(
        activity: FragmentActivity,
        credentialManager: CredentialManager,
        serverClientId: String
    ): Result {
        return when (val nativeResult = getNativeGoogleToken(activity, credentialManager, serverClientId)) {
            is Result.NativeToken -> nativeResult
            Result.Canceled -> Result.Canceled
            is Result.Failed -> signInWithWebFallback(activity)
            Result.WebSignedIn -> Result.WebSignedIn
        }
    }

    private suspend fun getNativeGoogleToken(
        activity: FragmentActivity,
        credentialManager: CredentialManager,
        serverClientId: String
    ): Result {
        return try {
            val rawNonce = UUID.randomUUID().toString()
            val hashedNonce = sha256(rawNonce)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = result.credential
            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.NativeToken(googleCredential.idToken, rawNonce)
            } else {
                Result.Failed(IllegalStateException("Unsupported Google credential type"))
            }
        } catch (_: GetCredentialCancellationException) {
            Result.Canceled
        } catch (e: GoogleIdTokenParsingException) {
            Result.Failed(e)
        } catch (e: Exception) {
            Result.Failed(e)
        }
    }

    private suspend fun signInWithWebFallback(activity: FragmentActivity): Result {
        return try {
            if (FirebaseApp.getApps(activity.applicationContext).isEmpty()) {
                return Result.Failed(IllegalStateException("Firebase Auth is not configured"))
            }
            val auth = FirebaseAuth.getInstance()
            val provider = OAuthProvider.newBuilder(GoogleAuthProvider.PROVIDER_ID)
                .setScopes(listOf("email", "profile"))
                .build()
            val pending = auth.pendingAuthResult
            if (pending != null) {
                pending.await()
            } else {
                auth.startActivityForSignInWithProvider(activity, provider).await()
            }
            Result.WebSignedIn
        } catch (e: Exception) {
            Result.Failed(e)
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
