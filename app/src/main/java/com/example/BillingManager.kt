package com.example

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BillingManager(
    private val context: Context,
    private val onCoinsPurchased: (productId: String, coinAmount: Int) -> Unit,
    private val onPurchaseFailed: (productId: String, errorMsg: String) -> Unit,
    private val onPurchaseCancelled: (productId: String) -> Unit
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Product IDs
    companion object {
        const val STARTER_PACK = "coin_pack_starter"
        const val VALUE_PACK = "coin_pack_value"
        const val PRO_PACK = "coin_pack_pro"
        const val MEGA_PACK = "coin_pack_mega"
        const val GALAXY_PACK = "coin_pack_galaxy"

        val PRODUCT_IDS = listOf(
            STARTER_PACK,
            VALUE_PACK,
            PRO_PACK,
            MEGA_PACK,
            GALAXY_PACK
        )

        fun getCoinAmountForProduct(productId: String): Int {
            return when (productId) {
                STARTER_PACK -> 2000
                VALUE_PACK -> 10000
                PRO_PACK -> 25000
                MEGA_PACK -> 70000
                GALAXY_PACK -> 150000
                else -> 0
            }
        }

        fun getPriceLabelForProduct(productId: String): String {
            return when (productId) {
                STARTER_PACK -> "₹29"
                VALUE_PACK -> "₹99"
                PRO_PACK -> "₹199"
                MEGA_PACK -> "₹499"
                GALAXY_PACK -> "₹999"
                else -> "N/A"
            }
        }

        fun getNameForProduct(productId: String): String {
            return when (productId) {
                STARTER_PACK -> "Starter Pack"
                VALUE_PACK -> "Value Pack"
                PRO_PACK -> "Pro Pack"
                MEGA_PACK -> "Mega Pack"
                GALAXY_PACK -> "Galaxy Pack"
                else -> "Coin Pack"
            }
        }
    }

    // Live Product Details from Play Store
    private val _productDetailsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetailsMap: StateFlow<Map<String, ProductDetails>> = _productDetailsMap

    private val _isBillingReady = MutableStateFlow(false)
    val isBillingReady: StateFlow<Boolean> = _isBillingReady

    init {
        initializeBillingClient()
    }

    private fun initializeBillingClient() {
        Log.d("BillingManager", "Initializing BillingClient")
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                val responseCode = billingResult.responseCode
                Log.d("BillingManager", "Billing connection setup finished with code: $responseCode")
                if (responseCode == BillingClient.BillingResponseCode.OK) {
                    _isBillingReady.value = true
                    queryProductDetails()
                    queryAndConsumeUnconsumedPurchases() // Best-practice restore & cleanup
                } else {
                    Log.w("BillingManager", "Billing Setup Failed: ${billingResult.debugMessage}")
                    _isBillingReady.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "Billing service disconnected. Reconnecting...")
                _isBillingReady.value = false
                // Implement simple reconnect logic
                coroutineScope.launch {
                    kotlinx.coroutines.delay(5000)
                    startConnection()
                }
            }
        })
    }

    private fun queryProductDetails() {
        val client = billingClient ?: return
        if (!_isBillingReady.value) return

        val productList = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val map = productDetailsList.associateBy { it.productId }
                _productDetailsMap.value = map
                Log.d("BillingManager", "Successfully loaded ${map.size} products from Play Store")
            } else {
                Log.e("BillingManager", "Query Product Details failed: ${billingResult.debugMessage} (Code: ${billingResult.responseCode})")
            }
        }
    }

    /**
     * Launch purchase flow for a product.
     */
    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val client = billingClient
        if (client == null || !_isBillingReady.value) {
            onPurchaseFailed(productId, "Google Play Billing is not ready. Please try again.")
            return
        }

        val details = _productDetailsMap.value[productId]
        if (details == null) {
            // If the product is not fetched from Play Store (could happen if product IDs are not added to Play Console),
            // we will still try to query specific details to be sure, or gracefully fail.
            Log.w("BillingManager", "ProductDetails for $productId not pre-loaded. Attempting instant single-query...")
            val singleProductList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(singleProductList)
                .build()

            client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                    val singleDetails = productDetailsList.first()
                    // Update our cache
                    val currentMap = _productDetailsMap.value.toMutableMap()
                    currentMap[productId] = singleDetails
                    _productDetailsMap.value = currentMap

                    launchBillingFlowWithDetails(activity, singleDetails)
                } else {
                    val errorMsg = "Product details not available in Google Play Store. Verify in Play Console."
                    Log.e("BillingManager", "Failed to resolve product: $productId. ${billingResult.debugMessage}")
                    onPurchaseFailed(productId, errorMsg)
                }
            }
            return
        }

        launchBillingFlowWithDetails(activity, details)
    }

    private fun launchBillingFlowWithDetails(activity: Activity, productDetails: ProductDetails) {
        val client = billingClient ?: return
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = client.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e("BillingManager", "Failed to launch billing flow: ${billingResult.debugMessage}")
            onPurchaseFailed(productDetails.productId, "Billing error: ${billingResult.debugMessage}")
        }
    }

    /**
     * Standard Billing Listener callback for purchase updates.
     */
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        val responseCode = billingResult.responseCode
        Log.d("BillingManager", "onPurchasesUpdated callback with code: $responseCode")

        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d("BillingManager", "Purchase cancelled by user")
                // Try to find which pack was cancelled, fallback to empty string
                onPurchaseCancelled("")
            }
            else -> {
                Log.e("BillingManager", "Purchase failed: ${billingResult.debugMessage}")
                onPurchaseFailed("", "Purchase failed: ${billingResult.debugMessage} (Code: $responseCode)")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Since our coins are consumable, we must consume the purchase to grant coins and allow buying again
            consumePurchase(purchase)
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d("BillingManager", "Purchase is pending. Waiting for cash/bank approval.")
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.consumeAsync(consumeParams) { billingResult, purchaseToken ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Granted!
                val productIds = purchase.products
                if (productIds.isNotEmpty()) {
                    val productId = productIds.first()
                    val coinsToGrant = getCoinAmountForProduct(productId)
                    Log.i("BillingManager", "Successfully consumed purchase for $productId. Granting $coinsToGrant coins.")
                    
                    // Run on Main Thread to trigger UI and SharedPreferences updates
                    coroutineScope.launch {
                        onCoinsPurchased(productId, coinsToGrant)
                    }
                }
            } else {
                Log.e("BillingManager", "Failed to consume purchase: ${billingResult.debugMessage}. Product was not consumed.")
                coroutineScope.launch {
                    onPurchaseFailed(
                        purchase.products.firstOrNull() ?: "",
                        "Failed to consume purchase. Please contact support."
                    )
                }
            }
        }
    }

    /**
     * Restore/re-verify purchases. 
     * Queries active purchases from Google Play. For any purchased items, we complete consumption 
     * in case the app was closed or crashed before consumption could finish.
     */
    fun queryAndConsumeUnconsumedPurchases() {
        val client = billingClient ?: return
        if (!_isBillingReady.value) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("BillingManager", "Restore: Found ${purchasesList.size} active purchases")
                for (purchase in purchasesList) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        Log.d("BillingManager", "Restore: Found unconsumed purchased product: ${purchase.products}")
                        consumePurchase(purchase)
                    }
                }
            } else {
                Log.e("BillingManager", "queryPurchasesAsync failed: ${billingResult.debugMessage}")
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}
