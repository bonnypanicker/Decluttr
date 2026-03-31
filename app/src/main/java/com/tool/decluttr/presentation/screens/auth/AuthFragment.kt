package com.tool.decluttr.presentation.screens.auth

import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tool.decluttr.R
import com.tool.decluttr.presentation.screens.settings.SettingsViewModel
import com.tool.decluttr.presentation.util.SimpleTextWatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthFragment : Fragment(R.layout.screen_auth) {

    private val viewModel: AuthViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnSkip = view.findViewById<ImageView>(R.id.btn_skip)
        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val tvError = view.findViewById<TextView>(R.id.tv_error)
        val btnPrimary = view.findViewById<TextView>(R.id.btn_primary_action)
        val progressLoading = view.findViewById<ProgressBar>(R.id.progress_loading)
        val dividerOr = view.findViewById<LinearLayout>(R.id.divider_or)
        val btnGoogle = view.findViewById<LinearLayout>(R.id.btn_google_signin)
        val tvModeToggle = view.findViewById<TextView>(R.id.tv_mode_toggle)
        val tvForgotPassword = view.findViewById<TextView>(R.id.tv_forgot_password)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                maxOf(systemBars.bottom, ime.bottom))
            insets
        }

        // Click handlers
        btnSkip.setOnClickListener { navigateToDashboard() }
        btnPrimary.setOnClickListener { viewModel.authenticate() }
        btnGoogle.setOnClickListener { /* TODO: Google Sign-In */ }
        tvModeToggle.setOnClickListener { viewModel.toggleMode() }
        tvForgotPassword.setOnClickListener { viewModel.sendPasswordReset() }

        // Text watchers
        etEmail.addTextChangedListener(SimpleTextWatcher { viewModel.onEmailChange(it) })
        etPassword.addTextChangedListener(SimpleTextWatcher { viewModel.onPasswordChange(it) })

        // Staggered entrance animation
        val animatableViews = listOf(etEmail, etPassword, btnPrimary, dividerOr, btnGoogle, tvModeToggle)
        animatableViews.forEachIndexed { index, v ->
            v.alpha = 0f
            v.translationY = 40f * resources.displayMetrics.density
            v.animate()
                .alpha(1f).translationY(0f)
                .setDuration(400)
                .setStartDelay(index * 50L + 100L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }

        // Observe ViewModel state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.email.collect { email ->
                        if (etEmail.text.toString() != email) {
                            etEmail.setText(email)
                            etEmail.setSelection(email.length)
                        }
                    }
                }
                launch {
                    viewModel.password.collect { password ->
                        if (etPassword.text.toString() != password) {
                            etPassword.setText(password)
                            etPassword.setSelection(password.length)
                        }
                    }
                }
                launch {
                    viewModel.isLoginMode.collect { isLogin ->
                        btnPrimary.text = if (isLogin) getString(R.string.auth_sign_in) else getString(R.string.auth_sign_up)
                        tvModeToggle.text = if (isLogin) getString(R.string.auth_toggle_to_signup) else getString(R.string.auth_toggle_to_signin)
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        btnPrimary.isEnabled = !loading
                        if (loading) {
                            btnPrimary.text = ""
                            progressLoading.visibility = View.VISIBLE
                        } else {
                            btnPrimary.text = if (viewModel.isLoginMode.value) getString(R.string.auth_sign_in) else getString(R.string.auth_sign_up)
                            progressLoading.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        tvError.visibility = if (error != null) View.VISIBLE else View.GONE
                        tvError.text = error
                    }
                }
                launch {
                    viewModel.infoMessage.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
                launch {
                    settingsViewModel.isLoggedIn.collect { loggedIn ->
                        if (loggedIn) navigateToDashboard()
                    }
                }
            }
        }
    }

    private fun navigateToDashboard() {
        findNavController().navigate(R.id.action_auth_to_dashboard)
    }
}
