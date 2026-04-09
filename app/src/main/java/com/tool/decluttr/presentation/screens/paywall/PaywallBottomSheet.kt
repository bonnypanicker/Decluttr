package com.tool.decluttr.presentation.screens.paywall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tool.decluttr.R
import com.tool.decluttr.domain.repository.BillingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PaywallBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var billingRepository: BillingRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_paywall, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnPay = view.findViewById<Button>(R.id.btn_pay)
        val tvTerms = view.findViewById<TextView>(R.id.tv_terms)
        val tvPrivacy = view.findViewById<TextView>(R.id.tv_privacy)

        viewLifecycleOwner.lifecycleScope.launch {
            billingRepository.premiumPrice.collectLatest { price ->
                btnPay.text = "Upgrade for $price"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            billingRepository.isPremium.collectLatest { isPremium ->
                if (isPremium) {
                    dismiss()
                }
            }
        }

        btnPay.setOnClickListener {
            // Trigger purchase flow
            activity?.let {
                billingRepository.launchBillingFlow(it)
            }
        }

        tvTerms.setOnClickListener {
            openUrl("https://example.com/terms") // Replace with actual terms URL
        }

        tvPrivacy.setOnClickListener {
            openUrl("https://example.com/privacy") // Replace with actual privacy URL
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    companion object {
        const val TAG = "PaywallBottomSheet"
        fun newInstance() = PaywallBottomSheet()
    }
}
