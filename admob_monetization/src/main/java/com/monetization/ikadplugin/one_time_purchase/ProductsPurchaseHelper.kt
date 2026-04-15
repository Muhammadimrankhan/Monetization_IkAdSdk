package com.monetization.ikadplugin.one_time_purchase

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.monetization.ikadplugin.internetController.InternetController
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.subscription.SubscriptionConstant.PRODUCT_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PurchasePriceModel(val price: String = "")

class ProductsPurchaseHelper : PurchasesUpdatedListener {
    private lateinit var appContext: Context
    companion object {
        @Volatile
        private var INSTANCE: ProductsPurchaseHelper? = null

        fun getInstance(): ProductsPurchaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProductsPurchaseHelper().also {
                    INSTANCE = it
                }
            }
        }
    }
    fun initBilling(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            connectBilling()
        }
    }

    private fun requireContext(): Context {
        check(::appContext.isInitialized) {
            "ProductsPurchaseHelper is not initialized. Call ProductsPurchaseHelper.getInstance().init(context) first."
        }
        return appContext
    }
    private val _productPriceFlow = MutableStateFlow(PurchasePriceModel())
    val productPriceFlow = _productPriceFlow.asStateFlow()

    private val _appPurchased: Channel<Boolean> = Channel()
    val appPurchased = _appPurchased.receiveAsFlow()

    private var purchaseSku: ProductDetails? = null

    private lateinit var billingClient: BillingClient
    private var isBillingReady: Boolean = false
    private val isBillingClientDead: Boolean
        get() = !::billingClient.isInitialized
    val isBillingClientReady: Boolean
        get() = if (isBillingClientDead) {
            false
        } else billingClient.isReady

    private fun queryProductSkuForPurchase() {
        val context = requireContext()
        if (!InternetController.getInstance(context).isInternetConnected) {
            return
        }
        if (isBillingClientDead) {
            return
        }
        try {
            val list: MutableList<QueryProductDetailsParams.Product> = ArrayList()
            list.add(
                QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP).build()
            )
            val queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder().setProductList(list).build()
            billingClient.queryProductDetailsAsync(
                queryProductDetailsParams
            ) { p0, p1 ->
                try {
                    if (p0.responseCode == BillingClient.BillingResponseCode.OK && p1.productDetailsList.isNotEmpty()) {
                        for (productDetails in p1.productDetailsList) {
                            if (productDetails.productId == PRODUCT_ID) {
                                purchaseSku = productDetails
                                _productPriceFlow.update {
                                    it.copy(
                                        price = productDetails.oneTimePurchaseOfferDetails?.formattedPrice
                                            ?: ""
                                    )
                                }
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    /*val queryProductDetailsParams =
        QueryProductDetailsParams.newBuilder()
            .setProductList(
                ImmutableList.of(
                    Product.newBuilder()
                        .setProductId("product_id_example")
                        .setProductType(ProductType.SUBS)
                        .build()))
            .build()

    billingClient.queryProductDetailsAsync(queryProductDetailsParams) {
        billingResult,
        queryProductDetailsResult ->
          if (billingResult.getResponseCode() == BillingResponseCode.OK) {
                   for (ProductDetails productDetails : queryProductDetailsResult.getProductDetailsList()) {
                     // Process successfully retrieved product details here.
                   }

                   for (UnfetchedProduct unfetchedProduct : queryproductDetailsResult.getUnfetchedProductList()) {
                     // Handle any unfetched products as appropriate.
                   }
                }
    }*/
    fun purchaseProduct(context: Activity) {
        if (!InternetController.getInstance(context).isInternetConnected) {
            Toast.makeText(context, "Internet not available", Toast.LENGTH_SHORT).show()
            return
        }
        if (purchaseSku == null) {
            Toast.makeText(context, "Internet not available", Toast.LENGTH_SHORT).show()
            return
        }
        if (isBillingClientDead) {
            return
        }
        purchaseSku?.let {
            val list: MutableList<BillingFlowParams.ProductDetailsParams> = ArrayList()
            list.add(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(it).build()
            )
            val billingFlowParams =
                BillingFlowParams.newBuilder().setProductDetailsParamsList(list).build()
            billingClient.launchBillingFlow(context, billingFlowParams)
        }
    }

    fun checkHistoryIfSkuNull(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            initBilling(context)
            if (purchaseSku == null) {
                checkProductPurchaseHistory()
            }
        }
    }

    private fun checkProductPurchaseHistory() {
        val context = requireContext()
        if (!InternetController.getInstance(context).isInternetConnected) {
            return
        }
        if (isBillingClientDead) {
            return
        }
        try {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP)
                    .build(), object : PurchasesResponseListener {
                    override fun onQueryPurchasesResponse(
                        p0: BillingResult, p1: MutableList<Purchase>
                    ) {
                        if (p0.responseCode == BillingClient.BillingResponseCode.OK) {
                            if (isProductPurchase(p1)) {
                                return
                            }
                        }
                        appNotPurchased()
                        queryProductSkuForPurchase()
                    }

                })
        } catch (_: Exception) {
        }
    }

    private fun appNotPurchased() {
        val context = requireContext()
        AdSharedPreference.getInstance(context).appAdPurchased = false
        CoroutineScope(Dispatchers.IO).launch {
            _appPurchased.send(false)
        }
    }

    private fun appPurchased() {
        val context = requireContext()
        AdSharedPreference.getInstance(context).appAdPurchased = true
        CoroutineScope(Dispatchers.IO).launch {
            _appPurchased.send(true)
        }
    }

    private fun isProductPurchase(purchaseList: List<Purchase>?): Boolean {
        if (!purchaseList.isNullOrEmpty()) {
            for (purchase in purchaseList) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && purchase.products.contains(
                        PRODUCT_ID
                    )
                ) {
                    if (purchase.isAcknowledged) {
                        appPurchased()
                    } else {
                        acknowledgedPurchase(purchase)
                    }
                    return true
                }
            }
        }
        return false
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, list: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            isProductPurchase(list)
        }
    }

    private fun acknowledgedPurchase(purchase: Purchase) {
        try {
            val acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken)
                    .build()
            if (isBillingClientDead) {
                return
            }
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { p0 ->
                if (p0.responseCode == BillingClient.BillingResponseCode.OK) {
                    appPurchased()
                }
            }
        } catch (_: Exception) {
        }
    }

   /* init {
        connectBilling()
    }*/

    private fun connectBilling() {
        try {
            val context = requireContext()
            if (!::billingClient.isInitialized) {
                billingClient =
                    BillingClient.newBuilder(context).setListener(this).enablePendingPurchases(
                        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                    ).build()
            }
            setupConnection()
        } catch (_: Exception) {
        }
    }
//    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
//    ..enableAutoServiceReconnection()


    private fun setupConnection() {
        try {
            if (!isBillingReady) {
                billingClient.apply {
                    if (!this.isReady) {
                        this.startConnection(object : BillingClientStateListener {
                            override fun onBillingServiceDisconnected() {
                                isBillingReady = false
                            }

                            override fun onBillingSetupFinished(p0: BillingResult) {
                                if (p0.responseCode == BillingClient.BillingResponseCode.OK) {
                                    isBillingReady = true
                                    checkProductPurchaseHistory()
                                }
                            }
                        })
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}