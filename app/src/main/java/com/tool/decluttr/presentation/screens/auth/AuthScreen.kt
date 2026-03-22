package com.tool.decluttr.presentation.screens.auth

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tool.decluttr.R

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onSkip: () -> Unit,
    onGoogleSignIn: (() -> Unit)? = null
) {
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val isLoginMode by viewModel.isLoginMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val rootView = LayoutInflater.from(context)
                .inflate(R.layout.screen_auth, null, false)

            val btnSkip = rootView.findViewById<TextView>(R.id.btn_skip)
            val imgLogo = rootView.findViewById<ImageView>(R.id.img_logo)
            val tvAppName = rootView.findViewById<TextView>(R.id.tv_app_name)
            val tvTagline = rootView.findViewById<TextView>(R.id.tv_tagline)
            val tvModeTitle = rootView.findViewById<TextView>(R.id.tv_mode_title)
            val tilEmail = rootView.findViewById<TextInputLayout>(R.id.til_email)
            val etEmail = rootView.findViewById<TextInputEditText>(R.id.et_email)
            val tilPassword = rootView.findViewById<TextInputLayout>(R.id.til_password)
            val etPassword = rootView.findViewById<TextInputEditText>(R.id.et_password)
            val tvError = rootView.findViewById<TextView>(R.id.tv_error)
            val btnPrimary = rootView.findViewById<MaterialButton>(R.id.btn_primary_action)
            val progressLoading = rootView.findViewById<CircularProgressIndicator>(R.id.progress_loading)
            val dividerOr = rootView.findViewById<LinearLayout>(R.id.divider_or)
            val btnGoogle = rootView.findViewById<MaterialButton>(R.id.btn_google_signin)
            val tvModeToggle = rootView.findViewById<TextView>(R.id.tv_mode_toggle)

            data class AuthCallbacks(
                var onEmailChange: ((String) -> Unit)? = null,
                var onPasswordChange: ((String) -> Unit)? = null,
                var onAuthenticate: (() -> Unit)? = null,
                var onToggleMode: (() -> Unit)? = null,
                var onSkipClick: (() -> Unit)? = null,
                var onGoogleClick: (() -> Unit)? = null
            )
            val callbacks = AuthCallbacks()
            rootView.setTag(R.id.auth_callback, callbacks)

            btnSkip.setOnClickListener { callbacks.onSkipClick?.invoke() }
            btnPrimary.setOnClickListener { callbacks.onAuthenticate?.invoke() }
            btnGoogle.setOnClickListener { callbacks.onGoogleClick?.invoke() }
            tvModeToggle.setOnClickListener { callbacks.onToggleMode?.invoke() }

            etEmail.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    callbacks.onEmailChange?.invoke(s.toString())
                }
            })

            etPassword.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    callbacks.onPasswordChange?.invoke(s.toString())
                }
            })

            // Staggered entrance animation
            val animatableViews = listOf(imgLogo, tvAppName, tvTagline, tvModeTitle, tilEmail, tilPassword, btnPrimary, dividerOr, btnGoogle, tvModeToggle)
            animatableViews.forEachIndexed { index, view ->
                view.alpha = 0f
                view.translationY = 40f * view.context.resources.displayMetrics.density
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setStartDelay(index * 50L + 100L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                    .start()
            }

            // Handle edge-to-edge insets
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    maxOf(systemBars.bottom, ime.bottom)
                )
                insets
            }

            rootView
        },
        update = { rootView ->
            val callbacks = rootView.getTag(R.id.auth_callback) as AuthCallbacks

            callbacks.onEmailChange = viewModel::onEmailChange
            callbacks.onPasswordChange = viewModel::onPasswordChange
            callbacks.onAuthenticate = { viewModel.authenticate() }
            callbacks.onToggleMode = { viewModel.toggleMode() }
            callbacks.onSkipClick = onSkip
            callbacks.onGoogleClick = onGoogleSignIn ?: {}

            val tvModeTitle = rootView.findViewById<TextView>(R.id.tv_mode_title)
            val etEmail = rootView.findViewById<TextInputEditText>(R.id.et_email)
            val etPassword = rootView.findViewById<TextInputEditText>(R.id.et_password)
            val tvError = rootView.findViewById<TextView>(R.id.tv_error)
            val btnPrimary = rootView.findViewById<MaterialButton>(R.id.btn_primary_action)
            val progressLoading = rootView.findViewById<CircularProgressIndicator>(R.id.progress_loading)
            val tvModeToggle = rootView.findViewById<TextView>(R.id.tv_mode_toggle)

            val signInText = rootView.context.getString(R.string.auth_sign_in)
            val signUpText = rootView.context.getString(R.string.auth_sign_up)
            val createAccountText = rootView.context.getString(R.string.auth_create_account)

            tvModeTitle.text = if (isLoginMode) signInText else createAccountText

            if (etEmail.text.toString() != email) {
                etEmail.setText(email)
                etEmail.setSelection(email.length)
            }

            if (etPassword.text.toString() != password) {
                etPassword.setText(password)
                etPassword.setSelection(password.length)
            }

            if (errorMessage != null) {
                tvError.visibility = View.VISIBLE
                tvError.text = errorMessage
            } else {
                tvError.visibility = View.GONE
            }

            btnPrimary.isEnabled = !isLoading
            if (isLoading) {
                btnPrimary.text = ""
                progressLoading.visibility = View.VISIBLE
            } else {
                btnPrimary.text = if (isLoginMode) signInText else signUpText
                progressLoading.visibility = View.GONE
            }

            tvModeToggle.text = if (isLoginMode)
                rootView.context.getString(R.string.auth_toggle_to_signup)
            else
                rootView.context.getString(R.string.auth_toggle_to_signin)
        }
    )
}
