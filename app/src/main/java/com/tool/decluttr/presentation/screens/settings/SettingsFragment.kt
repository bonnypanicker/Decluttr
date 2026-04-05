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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.tool.decluttr.R
import com.tool.decluttr.presentation.util.ThemePreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.InputStreamReader

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                if (inputStream != null) {
                    val reader = InputStreamReader(inputStream)
                    val jsonStr = reader.readText()
                    reader.close()
                    viewModel.importData(jsonStr)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val btnExport = view.findViewById<MaterialButton>(R.id.btn_export)
        val btnImport = view.findViewById<MaterialButton>(R.id.btn_import)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val tvEmail = view.findViewById<TextView>(R.id.tv_email)
        val btnSignout = view.findViewById<MaterialButton>(R.id.btn_signout)
        val btnPrivacyPolicy = view.findViewById<MaterialButton>(R.id.btn_privacy_policy)
        val btnTerms = view.findViewById<MaterialButton>(R.id.btn_terms)
        val btnLicenses = view.findViewById<MaterialButton>(R.id.btn_licenses)
        val tvAppVersion = view.findViewById<TextView>(R.id.tv_app_version)
        val themeToggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.theme_toggle_group)
        val btnThemeSystem = view.findViewById<MaterialButton>(R.id.btn_theme_system)
        val btnThemeLight = view.findViewById<MaterialButton>(R.id.btn_theme_light)
        val btnThemeDark = view.findViewById<MaterialButton>(R.id.btn_theme_dark)
        val settingsScroll = view.findViewById<View>(R.id.settings_scroll)

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
            toolbar.updatePadding(
                left = toolbarStart + systemBars.left,
                top = toolbarTop + systemBars.top,
                right = toolbarEnd + systemBars.right,
                bottom = toolbarBottom
            )
            settingsScroll.updatePadding(
                left = scrollStart + systemBars.left,
                top = scrollTop,
                right = scrollEnd + systemBars.right,
                bottom = scrollBottom + systemBars.bottom
            )
            insets
        }

        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        btnExport.setOnClickListener {
            viewModel.exportData()
        }

        btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        btnSignout.setOnClickListener {
            viewModel.signOut()
        }
        btnPrivacyPolicy.setOnClickListener {
            openUrl("https://github.com/bonnypanicker/Decluttr/edit/main/PRIVACY_POLICY.md")
        }
        btnTerms.setOnClickListener {
            openUrl("https://github.com/bonnypanicker/Decluttr/blob/main/TERMS_AND_CONDITIONS.md")
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
                                viewModel.resetState()
                            }
                            is SettingsState.ExportSuccess -> {
                                progress.visibility = View.GONE
                                btnExport.isEnabled = true
                                btnImport.isEnabled = true
                                
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, state.jsonString)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Save or Share Decluttr Archive")
                                requireContext().startActivity(shareIntent)
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
}
