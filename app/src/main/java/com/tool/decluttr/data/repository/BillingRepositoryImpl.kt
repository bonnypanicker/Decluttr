package com.tool.decluttr.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tool.decluttr.domain.model.EntitlementState
import com.tool.decluttr.domain.model.ProductUi
import com.tool.decluttr.domain.model.PurchaseState
import com.tool.decluttr.domain.repository.BillingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Provider
import kotlin.coroutines.resume

class BillingRepositoryImpl(
    @ApplicationContext private val app: Context,
    private val authProvider: Provider<FirebaseAuth>,
    private val firestoreProvider: Provider<FirebaseFirestore>,
    private val functionsProvider: Provider<FirebaseFunctions>
) : BillingRepository, PurchasesUpdatedListener {

    companion object {
        private const val TAG = "DecluttrBilling"
        private const val PRODUCT_ID = "decluttr_premium_upgrade"
        private const val LEGACY_PRODUCT_ID = "premium_unlimited_archiving"
        private const val ENTITLEMENT_SOURCE = "PLAY_BILLING"
        private const val PLAN_TYPE = "ONE_TIME"
        private const val VERIFY_FUNCTION_NAME = "verifyPremiumPurchase"
        private const val ENTITLEMENT_GRACE_MS = 24L * 60L * 60L * 1000L

        private const val PREFS_NAME = "decluttr_billing"
        private const val KEY_IS_PREMIUM = "isPremium"
        private const val KEY_LAST_VERIFIED = "lastVerifiedAt"
        private const val KEY_PRODUCT_ID = "productId"
        private const val KEY_SOURCE = "source"
        private const val KEY_PURCHASE_HASH = "purchaseTokenHash"

        private const val BILLING_ERROR_SIGN_IN_REQUIRED = 1001
        private const val BILLING_ERROR_PRODUCT_UNAVAILABLE = 1002
        private const val BILLING_ERROR_DISCONNECTED = 1003
        private const val BILLING_ERROR_NOT_OWNED = 1004
    }

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isBillingReady = AtomicBoolean(false)

    private val entitlementState = MutableStateFlow(loadCachedEntitlement())
    private val productState = MutableStateFlow(
        ProductUi(
            productId = PRODUCT_ID,
            title = "Decluttr Premium",
            description = "Unlock unlimited cloud archive",
            formattedPrice = "INR 99",
            isAvailable = false
        )
    )
    private val purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)

    @Volatile
    private var premiumProductDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        connectBillingIfNeeded()
        registerAuthListener()
        scope.launch {
            refreshEntitlement()
            queryProductDetails()
        }
    }

    override fun observeEntitlement(): Flow<EntitlementState> = entitlementState.asStateFlow()

    override fun observeProduct(): Flow<ProductUi> = productState.asStateFlow()

    override fun observePurchaseState(): Flow<PurchaseState> = purchaseState.asStateFlow()

    override fun currentEntitlement(): EntitlementState = entitlementState.value

    override suspend fun startPurchase(activity: Activity): Result<Unit> {
        recordBreadcrumb("start_purchase_requested")
        val auth = firebaseAuthOrNull()
        if (auth?.currentUser == null) {
            val error = PurchaseState.Error(
                code = BILLING_ERROR_SIGN_IN_REQUIRED,
                message = "Sign in required before purchase."
            )
            purchaseState.value = error
            return Result.failure(IllegalStateException(error.message))
        }

        purchaseState.value = PurchaseState.Loading
        val ready = ensureBillingReady()
        if (!ready) {
            val error = PurchaseState.Error(
                code = BILLING_ERROR_DISCONNECTED,
                message = "Unable to connect to Google Play Billing."
            )
            purchaseState.value = error
            return Result.failure(IllegalStateException(error.message))
        }

        val details = premiumProductDetails ?: queryProductDetails()
        if (details == null) {
            Log.w(TAG, "startPurchase: product details unavailable for productId=$PRODUCT_ID")
            val error = PurchaseState.Error(
                code = BILLING_ERROR_PRODUCT_UNAVAILABLE,
                message = "Premium product is currently unavailable."
            )
            purchaseState.value = error
            return Result.failure(IllegalStateException(error.message))
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        val result = billingClient.launchBillingFlow(activity, params)
        Log.d(
            TAG,
            "launchBillingFlow result: code=${result.responseCode}, msg=${result.debugMessage}"
        )
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                recordBreadcrumb("launch_billing_flow_ok")
                Result.success(Unit)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                recordBreadcrumb("launch_billing_flow_canceled")
                purchaseState.value = PurchaseState.Canceled
                Result.failure(IllegalStateException("Purchase canceled"))
            }
            else -> {
                recordBreadcrumb("launch_billing_flow_error_${result.responseCode}")
                val message = mapBillingErrorMessage(result)
                purchaseState.value = PurchaseState.Error(result.responseCode, message)
                Result.failure(IllegalStateException(message))
            }
        }
    }

    override suspend fun restorePurchases(): Result<Unit> {
        recordBreadcrumb("restore_purchases_requested")
        purchaseState.value = PurchaseState.Loading
        val ready = ensureBillingReady()
        if (!ready) {
            purchaseState.value = PurchaseState.Error(
                code = BILLING_ERROR_DISCONNECTED,
                message = "Unable to connect to Google Play Billing."
            )
            return Result.failure(IllegalStateException("Billing is not connected"))
        }

        return runCatching {
            val purchases = queryOwnedInAppPurchases()
            val purchasedItems = purchases.filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            val matchingItems = purchasedItems.filter { purchase ->
                purchase.products.any { productId ->
                    productId == PRODUCT_ID || productId == LEGACY_PRODUCT_ID
                }
            }

            Log.d(
                TAG,
                "restorePurchases: purchased=${purchasedItems.size}, matching=${matchingItems.size}, " +
                    "products=${purchasedItems.flatMap { it.products }.distinct()}"
            )

            if (matchingItems.isEmpty()) {
                refreshEntitlement()
                if (entitlementState.value.isPremium) {
                    purchaseState.value = PurchaseState.Success(
                        "Premium restored from your cloud account."
                    )
                    return@runCatching
                }
                purchaseState.value = PurchaseState.Error(
                    code = BILLING_ERROR_NOT_OWNED,
                    message = "No active premium purchase found for this Play account."
                )
                return@runCatching
            }

            matchingItems.forEach { purchase ->
                processPurchase(purchase)
            }
        }.onFailure {
            recordException(it)
            purchaseState.value = PurchaseState.Error(
                code = BillingClient.BillingResponseCode.ERROR,
                message = "Restore failed. Please try again."
            )
        }
    }

    override suspend fun refreshEntitlement() {
        recordBreadcrumb("refresh_entitlement_requested")
        val auth = firebaseAuthOrNull()
        val firestore = firestoreOrNull()
        val user = auth?.currentUser
        if (user == null || firestore == null) {
            val fallback = loadCachedEntitlement()
            entitlementState.value = fallback
            return
        }

        runCatching {
            val snapshot = firestore.collection("users")
                .document(user.uid)
                .collection("entitlements")
                .document("archive")
                .get()
                .await()

            if (!snapshot.exists()) {
                val fallback = loadCachedEntitlement()
                entitlementState.value = fallback.copy(
                    isPremium = fallback.hasFreshVerification(
                        nowMs = System.currentTimeMillis(),
                        graceWindowMs = ENTITLEMENT_GRACE_MS
                    )
                )
                return
            }

            val verifiedAt = snapshot.getLong("verifiedAt") ?: 0L
            val isPremium = snapshot.getBoolean("isPremium") == true
            val productId = snapshot.getString("productId") ?: PRODUCT_ID
            val source = snapshot.getString("source") ?: ENTITLEMENT_SOURCE
            val purchaseHash = snapshot.getString("purchaseTokenHash")

            val state = EntitlementState(
                isPremium = isPremium,
                source = source,
                lastVerifiedAt = verifiedAt,
                productId = productId,
                purchaseTokenHash = purchaseHash
            )
            entitlementState.value = state
            persistCachedEntitlement(state)
            recordBreadcrumb("refresh_entitlement_success_premium_${state.isPremium}")
        }.onFailure { error ->
            recordException(error)
            entitlementState.value = loadCachedEntitlement()
            recordBreadcrumb("refresh_entitlement_failed")
        }
    }

    override suspend fun syncEntitlementWithServer(
        purchaseToken: String,
        productId: String?
    ): Result<EntitlementState> {
        val auth = firebaseAuthOrNull()
        val currentUser = auth?.currentUser
            ?: return Result.failure(IllegalStateException("Sign in required"))
        val functions = functionsOrNull()
            ?: return Result.failure(IllegalStateException("Firebase Functions unavailable"))

        return runCatching {
            recordBreadcrumb("sync_entitlement_start")
            val payload = mapOf(
                "purchaseToken" to purchaseToken,
                "productId" to (productId ?: PRODUCT_ID),
                "packageName" to app.packageName,
                "uid" to currentUser.uid
            )
            val data = invokeVerifyCallableWithFallback(
                primaryFunctions = functions,
                payload = payload
            )
            val verified = data["verified"] as? Boolean ?: false
            if (!verified) {
                throw IllegalStateException(data["message"]?.toString() ?: "Purchase verification failed")
            }

            val verifiedAt = (data["verifiedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val state = EntitlementState(
                isPremium = true,
                source = ENTITLEMENT_SOURCE,
                lastVerifiedAt = verifiedAt,
                productId = (data["productId"] as? String) ?: (productId ?: PRODUCT_ID),
                purchaseTokenHash = hashPurchaseToken(purchaseToken)
            )
            entitlementState.value = state
            persistCachedEntitlement(state)
            recordBreadcrumb("sync_entitlement_verified")
            state
        }.onFailure { error ->
            recordException(error)
            recordBreadcrumb("sync_entitlement_failed")
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        Log.d(
            TAG,
            "onPurchasesUpdated: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}, purchases=${purchases?.size ?: 0}"
        )
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    purchaseState.value = PurchaseState.Error(
                        code = BillingClient.BillingResponseCode.ERROR,
                        message = "Purchase result was empty."
                    )
                    return
                }
                scope.launch {
                    purchases.forEach { purchase -> processPurchase(purchase) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                purchaseState.value = PurchaseState.Canceled
            }
            else -> {
                purchaseState.value = PurchaseState.Error(
                    code = billingResult.responseCode,
                    message = mapBillingErrorMessage(billingResult)
                )
            }
        }
    }

    private suspend fun processPurchase(purchase: Purchase) {
        val matchedProductId = purchase.products.firstOrNull { productId ->
            productId == PRODUCT_ID || productId == LEGACY_PRODUCT_ID
        } ?: return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        recordBreadcrumb("process_purchase_purchased")

        val token = purchase.purchaseToken
        if (!purchase.isAcknowledged) {
            acknowledgePurchase(token)
            recordBreadcrumb("purchase_acknowledged")
        }

        // Unlock locally from trusted Play purchase so premium UX/cap updates immediately.
        applyLocalPremiumEntitlement(
            purchaseToken = token,
            productId = matchedProductId
        )

        val syncResult = syncEntitlementWithServer(
            purchaseToken = token,
            productId = matchedProductId
        )

        if (syncResult.isFailure) {
            val errorMessage = syncResult.exceptionOrNull()?.message.orEmpty()
            Log.w(
                TAG,
                "processPurchase: cloud verify failed; keeping local premium active. error=$errorMessage"
            )
            purchaseState.value = PurchaseState.Success(
                "Premium active on this device. Cloud sync will retry automatically."
            )
            return
        }

        purchaseState.value = PurchaseState.Success("Premium unlocked successfully.")
    }

    private suspend fun invokeVerifyCallableWithFallback(
        primaryFunctions: FirebaseFunctions,
        payload: Map<String, Any?>
    ): Map<*, *> {
        val clients = linkedMapOf<String, FirebaseFunctions>()
        clients["primary"] = primaryFunctions
        runCatching { clients["asia-south1"] = FirebaseFunctions.getInstance("asia-south1") }
        runCatching { clients["us-central1"] = FirebaseFunctions.getInstance("us-central1") }
        runCatching { clients["default"] = FirebaseFunctions.getInstance() }

        var lastError: Throwable? = null
        for ((label, client) in clients) {
            try {
                recordBreadcrumb("verify_callable_attempt_$label")
                val result = client
                    .getHttpsCallable(VERIFY_FUNCTION_NAME)
                    .call(payload)
                    .await()
                val data = result.data as? Map<*, *> ?: emptyMap<String, Any?>()
                recordBreadcrumb("verify_callable_success_$label")
                return data
            } catch (error: Throwable) {
                lastError = error
                val notFound = isFunctionsNotFound(error)
                Log.w(
                    TAG,
                    "verify callable failed via $label notFound=$notFound message=${error.message}"
                )
                if (!notFound) throw error
            }
        }
        throw lastError ?: IllegalStateException("Purchase verification callable unavailable.")
    }

    private fun isFunctionsNotFound(error: Throwable): Boolean {
        val code = (error as? FirebaseFunctionsException)?.code
        if (code == FirebaseFunctionsException.Code.NOT_FOUND) return true
        return error.message?.contains("NOT_FOUND", ignoreCase = true) == true
    }

    private fun applyLocalPremiumEntitlement(
        purchaseToken: String,
        productId: String?
    ) {
        val now = System.currentTimeMillis()
        val state = EntitlementState(
            isPremium = true,
            source = ENTITLEMENT_SOURCE,
            lastVerifiedAt = now,
            productId = productId ?: PRODUCT_ID,
            purchaseTokenHash = hashPurchaseToken(purchaseToken)
        )
        entitlementState.value = state
        persistCachedEntitlement(state)
        recordBreadcrumb("local_entitlement_applied")
    }

    private suspend fun acknowledgePurchase(purchaseToken: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private suspend fun ensureBillingReady(): Boolean {
        if (isBillingReady.get() && billingClient.isReady) return true
        connectBillingIfNeeded()
        return suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    val ok = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    isBillingReady.set(ok)
                    if (ok) {
                        scope.launch { queryProductDetails() }
                    }
                    if (continuation.isActive) continuation.resume(ok)
                }

                override fun onBillingServiceDisconnected() {
                    isBillingReady.set(false)
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    private fun connectBillingIfNeeded() {
        if (isBillingReady.get() || billingClient.isReady) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val ok = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                isBillingReady.set(ok)
                if (ok) {
                    scope.launch { queryProductDetails() }
                }
            }

            override fun onBillingServiceDisconnected() {
                isBillingReady.set(false)
            }
        })
    }

    private suspend fun queryProductDetails(): ProductDetails? {
        val ready = ensureBillingReady()
        if (!ready) return null
        return suspendCancellableCoroutine { continuation ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()

            billingClient.queryProductDetailsAsync(params) { result, details ->
                Log.d(
                    TAG,
                    "queryProductDetailsAsync: code=${result.responseCode}, msg=${result.debugMessage}, count=${details?.size ?: 0}"
                )
                if (result.responseCode == BillingClient.BillingResponseCode.OK && !details.isNullOrEmpty()) {
                    val product = details.first()
                    premiumProductDetails = product
                    val oneTime = product.oneTimePurchaseOfferDetails
                    productState.value = ProductUi(
                        productId = PRODUCT_ID,
                        title = product.title,
                        description = product.description,
                        formattedPrice = oneTime?.formattedPrice ?: "INR 99",
                        isAvailable = true
                    )
                    if (continuation.isActive) continuation.resume(product)
                } else {
                    Log.w(
                        TAG,
                        "Product lookup failed/unavailable for productId=$PRODUCT_ID; code=${result.responseCode}"
                    )
                    productState.value = productState.value.copy(isAvailable = false)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private suspend fun queryOwnedInAppPurchases(): List<Purchase> {
        return suspendCancellableCoroutine { continuation ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                Log.d(
                    TAG,
                    "queryPurchasesAsync: code=${result.responseCode}, msg=${result.debugMessage}, count=${purchases?.size ?: 0}"
                )
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (continuation.isActive) {
                        continuation.resume(purchases.orEmpty())
                    }
                } else {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            }
        }
    }

    private fun mapBillingErrorMessage(result: BillingResult): String {
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.NETWORK_ERROR -> "Network error. Please try again."
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> "Google Play service unavailable."
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> "Billing unavailable on this device."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> "Premium already purchased. Use Restore."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> "Premium product is unavailable."
            BillingClient.BillingResponseCode.USER_CANCELED -> "Purchase canceled."
            else -> "Unable to complete purchase right now."
        }
    }

    private fun registerAuthListener() {
        firebaseAuthOrNull()?.addAuthStateListener { firebaseAuth ->
            scope.launch {
                if (firebaseAuth.currentUser == null) {
                    purchaseState.value = PurchaseState.Idle
                    entitlementState.value = loadCachedEntitlement().copy(isPremium = false)
                } else {
                    refreshEntitlement()
                    restorePurchases()
                }
            }
        }
    }

    private fun loadCachedEntitlement(): EntitlementState {
        val isPremium = prefs.getBoolean(KEY_IS_PREMIUM, false)
        val verifiedAt = prefs.getLong(KEY_LAST_VERIFIED, 0L)
        val productId = prefs.getString(KEY_PRODUCT_ID, PRODUCT_ID)
        val source = prefs.getString(KEY_SOURCE, "CACHE") ?: "CACHE"
        val hash = prefs.getString(KEY_PURCHASE_HASH, null)

        val now = System.currentTimeMillis()
        val premiumWithGrace = isPremium && (now - verifiedAt <= ENTITLEMENT_GRACE_MS)
        return EntitlementState(
            isPremium = premiumWithGrace,
            source = source,
            lastVerifiedAt = verifiedAt,
            productId = productId,
            purchaseTokenHash = hash
        )
    }

    private fun persistCachedEntitlement(state: EntitlementState) {
        prefs.edit()
            .putBoolean(KEY_IS_PREMIUM, state.isPremium)
            .putLong(KEY_LAST_VERIFIED, state.lastVerifiedAt)
            .putString(KEY_PRODUCT_ID, state.productId ?: PRODUCT_ID)
            .putString(KEY_SOURCE, state.source)
            .putString(KEY_PURCHASE_HASH, state.purchaseTokenHash)
            .apply()
    }

    private fun hashPurchaseToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }

    private fun firebaseAuthOrNull(): FirebaseAuth? {
        if (FirebaseApp.getApps(app).isEmpty()) return null
        return runCatching { authProvider.get() }.getOrNull()
    }

    private fun firestoreOrNull(): FirebaseFirestore? {
        if (FirebaseApp.getApps(app).isEmpty()) return null
        return runCatching { firestoreProvider.get() }.getOrNull()
    }

    private fun functionsOrNull(): FirebaseFunctions? {
        if (FirebaseApp.getApps(app).isEmpty()) return null
        return runCatching { functionsProvider.get() }.getOrNull()
    }

    private fun recordException(throwable: Throwable) {
        if (FirebaseApp.getApps(app).isNotEmpty()) {
            runCatching { FirebaseCrashlytics.getInstance().recordException(throwable) }
        }
    }

    private fun recordBreadcrumb(message: String) {
        if (FirebaseApp.getApps(app).isNotEmpty()) {
            runCatching { FirebaseCrashlytics.getInstance().log("billing:$message") }
        }
    }
}
