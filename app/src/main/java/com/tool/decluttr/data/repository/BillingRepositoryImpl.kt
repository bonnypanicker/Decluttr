package com.tool.decluttr.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.tool.decluttr.domain.repository.BillingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : BillingRepository, PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _premiumPrice = MutableStateFlow("₹99.00")
    override val premiumPrice: StateFlow<String> = _premiumPrice.asStateFlow()

    private val productId = "premium_unlimited_archiving"
    private var productDetails: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        startBillingConnection()
        checkUserFirestoreState()
    }

    private fun checkUserFirestoreState() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                scope.launch {
                    try {
                        val snapshot = firestore.collection("users").document(user.uid).get().await()
                        val isUserPremium = snapshot.getBoolean("isPremium") == true
                        _isPremium.value = isUserPremium
                        
                        // If they are not premium in firestore, query billing to be sure
                        if (!isUserPremium) {
                            queryPurchases()
                        }
                    } catch (e: Exception) {
                        Log.e("BillingRepo", "Failed to get user premium state: ${e.message}")
                    }
                }
            } else {
                _isPremium.value = false
            }
        }
    }

    override fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAvailableProducts()
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                // Try to restart the connection loosely
            }
        })
    }

    private fun queryAvailableProducts() {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !productDetailsList.isNullOrEmpty()) {
                val detail = productDetailsList.firstOrNull { it.productId == productId }
                detail?.let {
                    productDetails = it
                    _premiumPrice.value = it.oneTimePurchaseOfferDetails?.formattedPrice ?: "₹99.00"
                }
            }
        }
    }

    private fun queryPurchases() {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(queryPurchasesParams) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchasesList) {
                    handlePurchase(purchase)
                }
            }
        }
    }

    override fun launchBillingFlow(activity: Activity) {
        val details = productDetails ?: return
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i("BillingRepo", "User cancelled purchase")
        } else {
            Log.e("BillingRepo", "Purchase error: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (purchase.products.contains(productId)) {
                // Verify with backend Cloud Function
                verifyPurchaseWithBackend(purchase)
            }
        }
    }

    private fun verifyPurchaseWithBackend(purchase: Purchase) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // Unauthenticated fallback: acknowledge directly, but rely on device only
            grantPremiumAccessAndAcknowledge(purchase)
            return
        }

        val data = hashMapOf(
            "purchaseToken" to purchase.purchaseToken,
            "productId" to productId,
            "packageName" to context.packageName
        )

        functions.getHttpsCallable("verifyGooglePlayPurchase")
            .call(data)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val result = task.result?.data as? Map<String, Any>
                    val isValid = result?.get("isValid") as? Boolean == true
                    
                    if (isValid) {
                        grantPremiumAccessAndAcknowledge(purchase)
                    } else {
                        Log.e("BillingRepo", "Purchase verification failed on backend")
                    }
                } else {
                    Log.e("BillingRepo", "Cloud Function call failed: ${task.exception?.message}")
                    // If network failed, we might still want to grant locally and acknowledge to not lose the transaction
                    grantPremiumAccessAndAcknowledge(purchase)
                }
            }
    }

    private fun grantPremiumAccessAndAcknowledge(purchase: Purchase) {
        // Unlock premium UI
        _isPremium.value = true

        // Update Firestore for cross-device sync
        val uid = auth.currentUser?.uid
        if (uid != null) {
            scope.launch {
                try {
                    firestore.collection("users").document(uid)
                        .update("isPremium", true).await()
                } catch (e: Exception) {
                    Log.e("BillingRepo", "Failed to update Firestore premium state: ${e.message}")
                }
            }
        }

        // Acknowledge the purchase required by Google Play within 3 days
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i("BillingRepo", "Purchase formally acknowledged")
                }
            }
        }
    }

    override fun endConnection() {
        billingClient.endConnection()
    }
}
