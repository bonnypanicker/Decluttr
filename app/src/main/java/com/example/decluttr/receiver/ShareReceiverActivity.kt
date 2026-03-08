package com.example.decluttr.receiver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.decluttr.domain.usecase.CaptureAppUseCase
import com.example.decluttr.presentation.share.ShareStatus
import com.example.decluttr.presentation.share.ShareViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.shareStatus.collectLatest { status ->
                    when (status) {
                        is ShareStatus.Error -> {
                            Toast.makeText(this@ShareReceiverActivity, "Decluttr Error: ${status.message}", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        ShareStatus.Idle -> {}
                        ShareStatus.Processing -> {}
                        is ShareStatus.Success -> {
                            Toast.makeText(this@ShareReceiverActivity, "App Archived: ${status.packageId}", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            viewModel.handleSharedText(sharedText)
        } else {
            finish()
        }
    }
}
