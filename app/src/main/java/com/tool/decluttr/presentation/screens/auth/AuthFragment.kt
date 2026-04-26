package com.tool.decluttr.presentation.screens.auth

import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.tool.decluttr.R
import com.tool.decluttr.presentation.screens.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

@AndroidEntryPoint
class AuthFragment : Fragment(R.layout.screen_auth) {

    private val viewModel: AuthViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private lateinit var credentialManager: CredentialManager
    private var onboardingWebView: WebView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        credentialManager = CredentialManager.create(requireContext())
        val webView = view.findViewById<WebView>(R.id.onboarding_webview)
        onboardingWebView = webView

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Let onboarding content handle top safe-area in WebView so it can use the notch area.
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        configureWebView(webView)
        loadExactOnboarding(webView)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoading.collect { loading ->
                        onboardingWebView?.evaluateJavascript(
                            "window.setAuthLoading && window.setAuthLoading(${if (loading) "true" else "false"});",
                            null
                        )
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        if (!error.isNullOrBlank()) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                launch {
                    viewModel.infoMessage.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
                launch {
                    settingsViewModel.isLoggedIn.collect { loggedIn ->
                        if (loggedIn == true) navigateToDashboard()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        onboardingWebView?.apply {
            stopLoading()
            removeJavascriptInterface("AndroidAuth")
            webChromeClient = null
            destroy()
        }
        onboardingWebView = null
        super.onDestroyView()
    }

    private fun configureWebView(webView: WebView) {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(AuthBridge(), "AndroidAuth")
    }

    private fun loadExactOnboarding(webView: WebView) {
        // Show an instant background color while loading
        webView.setBackgroundColor(0xFF0A0B0F.toInt())
        webView.loadUrl("file:///android_asset/onboarding.html")
    }

    private inner class AuthBridge {
        @JavascriptInterface
        fun startGoogleSignIn() {
            activity?.runOnUiThread {
                this@AuthFragment.startGoogleSignIn()
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            activity?.runOnUiThread {
                try {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToDashboard() {
        runCatching {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
            com.google.firebase.analytics.FirebaseAnalytics.getInstance(requireContext()).setAnalyticsCollectionEnabled(true)
        }
        findNavController().navigate(R.id.action_auth_to_dashboard)
    }

    private fun startGoogleSignIn() {
        val serverClientId = runCatching { getString(R.string.default_web_client_id) }.getOrNull()
        if (serverClientId.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                "Google Sign-In is not configured for this build.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rawNonce = UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

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
                    context = requireActivity(),
                    request = request
                )

                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.authenticateWithGoogleIdToken(googleCredential.idToken, rawNonce)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Unable to read Google credential.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (_: GetCredentialException) {
                Toast.makeText(
                    requireContext(),
                    "Google sign-in was canceled or unavailable.",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: GoogleIdTokenParsingException) {
                Toast.makeText(
                    requireContext(),
                    "Google token parsing failed.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.localizedMessage ?: "Google sign-in failed.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
