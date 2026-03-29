package com.tool.decluttr.presentation.screens.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.tool.decluttr.R
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
                        if (!loggedIn) {
                            findNavController().navigate(
                                R.id.authFragment,
                                null,
                                androidx.navigation.NavOptions.Builder()
                                    .setPopUpTo(R.id.nav_graph, true)
                                    .build()
                            )
                        } else {
                            btnSignout.visibility = View.VISIBLE
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
}
