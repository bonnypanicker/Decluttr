package com.tool.decluttr.presentation.screens.billing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.PurchaseState
import com.tool.decluttr.presentation.util.AppLinks
import kotlinx.coroutines.launch

class PaywallBottomSheet : BottomSheetDialogFragment(R.layout.bottom_sheet_paywall) {

    companion object {
        private const val TAG = "PaywallBottomSheet"
        private const val ARG_REASON = "reason"
        private const val ARG_USED = "used"
        private const val ARG_LIMIT = "limit"

        fun newInstance(
            reason: String,
            used: Int? = null,
            limit: Int? = null
        ): PaywallBottomSheet {
            return PaywallBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_REASON, reason)
                    if (used != null) putInt(ARG_USED, used)
                    if (limit != null) putInt(ARG_LIMIT, limit)
                }
            }
        }
    }

    private val billingViewModel: BillingViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tvTitle = view.findViewById<TextView>(R.id.tv_paywall_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_paywall_subtitle)
        val tvPrice = view.findViewById<TextView>(R.id.tv_paywall_price)
        val tvStatus = view.findViewById<TextView>(R.id.tv_paywall_status)
        val tvMessage = view.findViewById<TextView>(R.id.tv_paywall_message)
        val tvTerms = view.findViewById<TextView>(R.id.tv_paywall_terms)
        val tvPrivacy = view.findViewById<TextView>(R.id.tv_paywall_privacy)
        val btnBuy = view.findViewById<MaterialButton>(R.id.btn_paywall_buy)
        val btnRestore = view.findViewById<MaterialButton>(R.id.btn_paywall_restore)

        val reason = arguments?.getString(ARG_REASON).orEmpty()
        val blockedUsed = arguments?.getInt(ARG_USED, -1) ?: -1
        val blockedLimit = arguments?.getInt(ARG_LIMIT, -1) ?: -1
        if (reason.contains("limit", ignoreCase = true)) {
            tvTitle.text = "Free Limit Reached"
            val subtitle = if (blockedUsed >= 0 && blockedLimit > 0) {
                "You have used $blockedUsed/$blockedLimit archive credits."
            } else {
                "Upgrade to continue archiving to cloud without limits."
            }
            tvSubtitle.text = subtitle
        }

        tvTerms.text = HtmlCompat.fromHtml("<u>Terms</u>", HtmlCompat.FROM_HTML_MODE_LEGACY)
        tvPrivacy.text = HtmlCompat.fromHtml("<u>Privacy Policy</u>", HtmlCompat.FROM_HTML_MODE_LEGACY)

        tvTerms.setOnClickListener { openUrl(AppLinks.TERMS_URL) }
        tvPrivacy.setOnClickListener { openUrl(AppLinks.PRIVACY_POLICY_URL) }

        btnBuy.setOnClickListener {
            if (!billingViewModel.isLoggedIn.value) {
                Toast.makeText(
                    requireContext(),
                    "Sign in required before purchase. Open Settings > Account.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            billingViewModel.startPremiumPurchase(requireActivity())
        }

        btnRestore.setOnClickListener {
            if (!billingViewModel.isLoggedIn.value) {
                Toast.makeText(
                    requireContext(),
                    "Sign in required to restore purchases.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            billingViewModel.restorePurchases()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    billingViewModel.productUi.collect { product ->
                        val priceText = if (product.isAvailable) {
                            "${product.formattedPrice} one-time"
                        } else {
                            "INR 99 one-time"
                        }
                        tvPrice.text = priceText
                        btnBuy.text = if (product.isAvailable) {
                            "Unlock Unlimited for ${product.formattedPrice}"
                        } else {
                            "Unlock Unlimited"
                        }
                    }
                }

                launch {
                    billingViewModel.archiveCreditsUi.collect { credits ->
                        tvStatus.text = if (credits.isPremium) {
                            "Premium active: Unlimited archive credits"
                        } else {
                            "Free plan: ${credits.used}/${credits.limit} archive credits used"
                        }
                    }
                }

                launch {
                    billingViewModel.purchaseState.collect { state ->
                        when (state) {
                            is PurchaseState.Loading -> {
                                btnBuy.isEnabled = false
                                btnRestore.isEnabled = false
                                tvMessage.text = "Connecting to Google Play..."
                            }
                            is PurchaseState.Success -> {
                                btnBuy.isEnabled = true
                                btnRestore.isEnabled = true
                                tvMessage.text = state.message
                                dismissAllowingStateLoss()
                            }
                            is PurchaseState.Error -> {
                                btnBuy.isEnabled = true
                                btnRestore.isEnabled = true
                                tvMessage.text = state.message
                            }
                            is PurchaseState.Canceled -> {
                                btnBuy.isEnabled = true
                                btnRestore.isEnabled = true
                                tvMessage.text = "Purchase canceled."
                            }
                            PurchaseState.Idle -> {
                                btnBuy.isEnabled = true
                                btnRestore.isEnabled = true
                                tvMessage.text =
                                    "Purchases are handled by Google Play. Refunds follow Play policies."
                            }
                        }
                    }
                }
            }
        }

        billingViewModel.refreshBilling()
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getTheme(): Int {
        return com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog
    }
}
