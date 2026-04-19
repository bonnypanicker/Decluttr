package com.tool.decluttr.presentation.screens.auth

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
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
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import java.security.MessageDigest
import java.util.UUID
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import androidx.viewpager2.widget.ViewPager2
import com.tool.decluttr.R
import com.tool.decluttr.presentation.screens.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthFragment : Fragment(R.layout.screen_auth) {

    private val viewModel: AuthViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private var pagerCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvError = view.findViewById<TextView>(R.id.tv_error)
        val btnGoogle = view.findViewById<MaterialButton>(R.id.btn_google_signin)
        val progressLoading = view.findViewById<ProgressBar>(R.id.progress_loading)
        val onboardingPager = view.findViewById<ViewPager2>(R.id.onboarding_pager)
        val pagerIndicator = view.findViewById<LinearLayout>(R.id.pager_indicator)
        val credentialManager = CredentialManager.create(requireContext())
        val panels = buildPanels()
        onboardingPager.adapter = OnboardingPanelAdapter(panels)
        renderPagerDots(
            indicator = pagerIndicator,
            selectedIndex = onboardingPager.currentItem,
            total = panels.size
        )
        pagerCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                renderPagerDots(
                    indicator = pagerIndicator,
                    selectedIndex = position,
                    total = panels.size
                )
            }
        }.also { onboardingPager.registerOnPageChangeCallback(it) }

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                maxOf(systemBars.bottom, ime.bottom))
            insets
        }

        // Click handlers
        btnGoogle.setOnClickListener { startGoogleSignIn(credentialManager) }

        // Staggered entrance animation
        val animatableViews = listOf(onboardingPager, pagerIndicator, btnGoogle)
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
                    viewModel.isLoading.collect { loading ->
                        btnGoogle.isEnabled = !loading
                        btnGoogle.alpha = if (loading) 0.85f else 1f
                        onboardingPager.isUserInputEnabled = !loading
                        if (loading) {
                            btnGoogle.text = ""
                            btnGoogle.icon = null
                            progressLoading.visibility = View.VISIBLE
                        } else {
                            btnGoogle.text = getString(R.string.auth_google_signin)
                            btnGoogle.setIconResource(R.drawable.ic_google_logo)
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
                        if (loggedIn == true) navigateToDashboard()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        view?.findViewById<ViewPager2>(R.id.onboarding_pager)?.let { pager ->
            pagerCallback?.let { pager.unregisterOnPageChangeCallback(it) }
        }
        pagerCallback = null
        super.onDestroyView()
    }

    private fun navigateToDashboard() {
        findNavController().navigate(R.id.action_auth_to_dashboard)
    }

    private fun startGoogleSignIn(credentialManager: CredentialManager) {
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

    private fun buildPanels(): List<OnboardingPanel> {
        return listOf(
            OnboardingPanel(
                iconRes = R.drawable.ic_cleaning_services,
                accentColor = Color.parseColor("#FF6B35"),
                tag = getString(R.string.auth_panel_1_tag),
                title = getString(R.string.auth_panel_1_title),
                body = getString(R.string.auth_panel_1_body)
            ),
            OnboardingPanel(
                iconRes = R.drawable.ic_archive_outlined,
                accentColor = Color.parseColor("#4ECDC4"),
                tag = getString(R.string.auth_panel_2_tag),
                title = getString(R.string.auth_panel_2_title),
                body = getString(R.string.auth_panel_2_body)
            ),
            OnboardingPanel(
                iconRes = R.drawable.ic_storage_outlined,
                accentColor = Color.parseColor("#A78BFA"),
                tag = getString(R.string.auth_panel_3_tag),
                title = getString(R.string.auth_panel_3_title),
                body = getString(R.string.auth_panel_3_body)
            ),
            OnboardingPanel(
                iconRes = R.drawable.ic_list,
                accentColor = Color.parseColor("#F59E0B"),
                tag = getString(R.string.auth_panel_4_tag),
                title = getString(R.string.auth_panel_4_title),
                body = getString(R.string.auth_panel_4_body),
                supportText = getString(R.string.auth_panel_4_support)
            ),
            OnboardingPanel(
                iconRes = R.drawable.ic_search,
                accentColor = Color.parseColor("#34D399"),
                tag = getString(R.string.auth_panel_5_tag),
                title = getString(R.string.auth_panel_5_title),
                body = getString(R.string.auth_panel_5_body)
            ),
            OnboardingPanel(
                iconRes = R.drawable.ic_play_store,
                accentColor = Color.parseColor("#60A5FA"),
                tag = getString(R.string.auth_panel_6_tag),
                title = getString(R.string.auth_panel_6_title),
                body = getString(R.string.auth_panel_6_body),
                supportText = getString(R.string.auth_panel_6_support)
            )
        )
    }

    private fun renderPagerDots(
        indicator: LinearLayout,
        selectedIndex: Int,
        total: Int
    ) {
        if (total <= 0) return
        if (indicator.childCount != total) {
            indicator.removeAllViews()
            repeat(total) {
                val dot = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                        marginStart = dp(4)
                        marginEnd = dp(4)
                    }
                }
                indicator.addView(dot)
            }
        }

        val selectedColor = MaterialColors.getColor(
            indicator,
            com.google.android.material.R.attr.colorPrimary
        )
        val unselectedColor = MaterialColors.getColor(
            indicator,
            com.google.android.material.R.attr.colorOutlineVariant
        )

        for (i in 0 until indicator.childCount) {
            val dot = indicator.getChildAt(i)
            val active = i == selectedIndex
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(99).toFloat()
                setColor(if (active) selectedColor else unselectedColor)
            }
            dot.background = shape
            dot.alpha = if (active) 1f else 0.5f
            dot.updateLayoutParams<LinearLayout.LayoutParams> {
                width = if (active) dp(20) else dp(8)
                height = dp(8)
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
