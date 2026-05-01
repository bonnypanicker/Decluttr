package com.tool.decluttr.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tool.decluttr.R
import com.tool.decluttr.presentation.screens.billing.BillingViewModel
import com.tool.decluttr.presentation.screens.billing.PaywallBottomSheet
import com.tool.decluttr.presentation.screens.auth.AuthViewModel
import com.tool.decluttr.presentation.util.AppLinks
import com.tool.decluttr.presentation.util.ThemePreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.InputStreamReader

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val billingViewModel: BillingViewModel by activityViewModels()

    private var pendingExportData: String? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let {
            try {
                val data = pendingExportData
                if (data != null) {
                    val encryptedBytes = com.tool.decluttr.presentation.util.CryptoUtils.encrypt(data)
                    requireContext().contentResolver.openOutputStream(it)?.use { out ->
                        out.write(encryptedBytes)
                    }
                    Toast.makeText(context, "Archive exported securely!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error writing encrypted file", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportData = null
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                requireContext().contentResolver.openInputStream(it)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val jsonStr = com.tool.decluttr.presentation.util.CryptoUtils.decrypt(bytes)
                    viewModel.importData(jsonStr)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading or decrypting file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        billingViewModel.refreshBilling()

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val btnExport = view.findViewById<MaterialButton>(R.id.btn_export)
        val btnImport = view.findViewById<MaterialButton>(R.id.btn_import)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val tvEmail = view.findViewById<TextView>(R.id.tv_email)
        val btnSignin = view.findViewById<MaterialButton>(R.id.btn_signin)
        val btnSignout = view.findViewById<MaterialButton>(R.id.btn_signout)
        val btnManagePremium = view.findViewById<MaterialButton>(R.id.btn_manage_premium)
        val btnPrivacyPolicy = view.findViewById<MaterialButton>(R.id.btn_privacy_policy)
        val btnTerms = view.findViewById<MaterialButton>(R.id.btn_terms)
        val btnDeleteAccount = view.findViewById<MaterialButton>(R.id.btn_delete_account)
        val btnLicenses = view.findViewById<MaterialButton>(R.id.btn_licenses)
        val tvAppVersion = view.findViewById<TextView>(R.id.tv_app_version)
        val themeToggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.theme_toggle_group)
        val btnThemeSystem = view.findViewById<MaterialButton>(R.id.btn_theme_system)
        val btnThemeLight = view.findViewById<MaterialButton>(R.id.btn_theme_light)
        val btnThemeDark = view.findViewById<MaterialButton>(R.id.btn_theme_dark)
        val settingsScroll = view.findViewById<View>(R.id.settings_scroll)

        val rootStart = view.paddingStart
        val rootTop = view.paddingTop
        val rootEnd = view.paddingEnd
        val rootBottom = view.paddingBottom
        val toolbarStart = toolbar.paddingStart
        val toolbarTop = toolbar.paddingTop
        val toolbarEnd = toolbar.paddingEnd
        val toolbarBottom = toolbar.paddingBottom
        val scrollStart = settingsScroll.paddingStart
        val scrollTop = settingsScroll.paddingTop
        val scrollEnd = settingsScroll.paddingEnd
        val scrollBottom = settingsScroll.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = rootStart + systemBars.left,
                top = rootTop + systemBars.top,
                right = rootEnd + systemBars.right,
                bottom = rootBottom
            )
            toolbar.updatePadding(
                left = toolbarStart,
                top = toolbarTop,
                right = toolbarEnd,
                bottom = toolbarBottom
            )
            settingsScroll.updatePadding(
                left = scrollStart,
                top = scrollTop,
                right = scrollEnd,
                bottom = scrollBottom + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)

        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        btnExport.setOnClickListener {
            viewModel.exportData()
        }

        btnImport.setOnClickListener {
            // Allows selecting the .enc or .bak files (or any file, technically)
            importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }

        btnSignin.setOnClickListener {
            startGoogleSignIn()
        }

        btnSignout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign Out") { _, _ ->
                    viewModel.signOut()
                }
                .show()
        }
        btnManagePremium.setOnClickListener {
            showPaywall()
        }
        btnPrivacyPolicy.setOnClickListener {
            openUrl(AppLinks.PRIVACY_POLICY_URL)
        }
        btnTerms.setOnClickListener {
            openUrl(AppLinks.TERMS_URL)
        }
        btnDeleteAccount.setOnClickListener {
            openUrl(AppLinks.DELETE_ACCOUNT_URL)
        }
        btnLicenses.setOnClickListener {
            showLicensesDialog()
        }

        val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        tvAppVersion.text = "Version ${packageInfo.versionName} ($versionCode)"

        val initialThemeButton = when (ThemePreferences.getThemeMode(requireContext())) {
            AppCompatDelegate.MODE_NIGHT_YES -> btnThemeDark.id
            AppCompatDelegate.MODE_NIGHT_NO -> btnThemeLight.id
            else -> btnThemeSystem.id
        }
        themeToggleGroup.check(initialThemeButton)
        themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selectedMode = when (checkedId) {
                btnThemeDark.id -> AppCompatDelegate.MODE_NIGHT_YES
                btnThemeLight.id -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (selectedMode != ThemePreferences.getThemeMode(requireContext())) {
                ThemePreferences.setThemeMode(requireContext(), selectedMode)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentUserEmail.collect { email ->
                        if (email != null) {
                            tvEmail.text = "Logged in as: $email"
                            tvEmail.visibility = View.VISIBLE
                        } else {
                            tvEmail.text = "Not logged in"
                            tvEmail.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    viewModel.isLoggedIn.collect { loggedIn ->
                        btnSignout.visibility = if (loggedIn == true) View.VISIBLE else View.GONE
                        btnSignin.visibility = if (loggedIn != true) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    billingViewModel.entitlementState.collect { entitlement ->
                        btnManagePremium.text = if (entitlement.isPremium) {
                            "Premium Active"
                        } else {
                            "Upgrade to Premium"
                        }
                    }
                }
                launch {
                    viewModel.settingsState.collect { state ->
                        when(state) {
                            is SettingsState.Processing -> {
                                progress.visibility = View.VISIBLE
                                btnExport.isEnabled = false
                                btnImport.isEnabled = false
                            }
                            is SettingsState.Error -> {
                                progress.visibility = View.GONE
                                btnExport.isEnabled = true
                                btnImport.isEnabled = true
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                                if (state.message.contains("Archive limit reached", ignoreCase = true)) {
                                    showPaywall()
                                }
                                viewModel.resetState()
                            }
                            is SettingsState.ExportSuccess -> {
                                progress.visibility = View.GONE
                                btnExport.isEnabled = true
                                btnImport.isEnabled = true
                                
                                pendingExportData = state.jsonString
                                val timeStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                                exportLauncher.launch("DecluttrArchive_${timeStr}.dec")
                                viewModel.resetState()
                            }
                            is SettingsState.ImportSuccess -> {
                                progress.visibility = View.GONE
                                btnExport.isEnabled = true
                                btnImport.isEnabled = true
                                Toast.makeText(requireContext(), "Archive imported successfully!", Toast.LENGTH_SHORT).show()
                                viewModel.resetState()
                            }
                            is SettingsState.Idle -> {
                                progress.visibility = View.GONE
                                btnExport.isEnabled = true
                                btnImport.isEnabled = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLicensesDialog() {
        val licensesText = """
            AndroidX libraries (Apache 2.0)
            Material Components (Apache 2.0)
            Kotlin Coroutines (Apache 2.0)
            Room (Apache 2.0)
            Coil (Apache 2.0)
            Firebase SDKs (Apache 2.0)
        """.trimIndent()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Open-source Licenses")
            .setMessage(licensesText)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPaywall() {
        val tag = "PaywallBottomSheet"
        if (parentFragmentManager.findFragmentByTag(tag) != null) return
        val credits = billingViewModel.archiveCreditsUi.value
        val usedArg = credits.used.takeIf { it >= 0 }
        val limitArg = if (credits.isPremium) null else credits.limit
        PaywallBottomSheet.newInstance(
            reason = "settings_manage_premium",
            used = usedArg,
            limit = limitArg
        )
            .show(parentFragmentManager, tag)
    }

    private fun startGoogleSignIn() {
        val credentialManager = androidx.credentials.CredentialManager.create(requireContext())
        val serverClientId = runCatching { getString(R.string.default_web_client_id) }.getOrNull()
        if (serverClientId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Google Sign-In is not configured for this build.", Toast.LENGTH_LONG).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val rawNonce = java.util.UUID.randomUUID().toString()
                val bytes = rawNonce.toByteArray()
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val digest = md.digest(bytes)
                val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

                val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setNonce(hashedNonce)
                    .build()

                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = requireActivity(),
                    request = request
                )

                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    authViewModel.authenticateWithGoogleIdToken(googleCredential.idToken, rawNonce)
                } else {
                    Toast.makeText(requireContext(), "Unable to read Google credential.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.localizedMessage ?: "Google sign-in failed.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
