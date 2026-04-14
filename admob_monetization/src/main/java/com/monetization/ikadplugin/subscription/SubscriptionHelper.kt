package com.monetization.ikadplugin.subscription

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import androidx.core.net.toUri
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
import com.monetization.ikadplugin.pref.AdSharedPreference
import com.monetization.ikadplugin.subscription.SubscriptionConstant.MONTHLY_SUBSCRIPTION_ID
import com.monetization.ikadplugin.subscription.SubscriptionConstant.SUBSCRIBED_PRODUCT_ID
import com.monetization.ikadplugin.subscription.SubscriptionConstant.WEEKLY_SUBSCRIPTION_ID
import com.monetization.ikadplugin.subscription.SubscriptionConstant.YEARLY_SUBSCRIPTION_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubscriptionModel(val productsList: Map<String, ProductDetails> = emptyMap())

class SubscriptionHelper(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : PurchasesUpdatedListener {
    private var isBillingReady: Boolean = false
    private lateinit var subscriptionClient: BillingClient
    private var subscribeProductToken = ""

    private val _productListFlow = MutableStateFlow(SubscriptionModel())
    val productListFlow = _productListFlow.asStateFlow()

    private val _appSubscribed: Channel<Boolean> = Channel()
    val appSubscribed = _appSubscribed.receiveAsFlow()

    private val isBillingClientDead: Boolean get() = !::subscriptionClient.isInitialized
    val isBillingClientReady: Boolean
        get() = if (isBillingClientDead) {
            false
        } else subscriptionClient.isReady

    fun purchaseProduct(mActivity: Activity, skuDetails: ProductDetails) {
        try {
            if (isBillingClientDead) {
                return
            }
            val offerToken = skuDetails.subscriptionOfferDetails?.get(0)!!.offerToken
            val responseCode = subscriptionClient.launchBillingFlow(
                mActivity, BillingFlowParams.newBuilder().setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(skuDetails).setOfferToken(offerToken).build()
                    )
                ).build()
            ).responseCode
            if (responseCode == BillingClient.BillingResponseCode.OK) {
//                setAnalytics(mActivity, "subscription_plan_response ok")
                Log.d("Sub", "purchaseProduct: Subscribe_now_clicked")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun changeSubscriptionPlan(mActivity: Activity, skuDetails: ProductDetails) {
        try {
            if (isBillingClientDead) {
                return
            }
            val offerToken = skuDetails.subscriptionOfferDetails?.get(0)!!.offerToken
            val list: MutableList<BillingFlowParams.ProductDetailsParams> = ArrayList()
            list.add(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(skuDetails)
                    .setOfferToken(offerToken).build()
            )
            val flowParams = BillingFlowParams.newBuilder().setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(subscribeProductToken)
                    .setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION)
                    .build()
            ).setProductDetailsParamsList(list).build()
            if (subscriptionClient.launchBillingFlow(
                    mActivity, flowParams
                ).responseCode == BillingClient.BillingResponseCode.OK
            ) {
//                setAnalytics(mActivity, "subscription_plan_update_response ok")
                Log.d("Sub", "purchaseProduct: Subscribe_update_clicked")
            }
        } catch (_: Exception) {
        }
    }

    fun querySubscriptionProducts() {
        if (isBillingClientDead) {
            return
        }
        if (isSubscriptionSupported) {
            val list: MutableList<QueryProductDetailsParams.Product> =
                ArrayList<QueryProductDetailsParams.Product>().apply {
                    add(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(WEEKLY_SUBSCRIPTION_ID)
                            .setProductType(BillingClient.ProductType.SUBS).build()
                    )
                    add(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(MONTHLY_SUBSCRIPTION_ID)
                            .setProductType(BillingClient.ProductType.SUBS).build()
                    )
                    add(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(YEARLY_SUBSCRIPTION_ID)
                            .setProductType(BillingClient.ProductType.SUBS).build()
                    )
                }

            val queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder().setProductList(list).build()
            if (isBillingClientDead) {
                return
            }
            subscriptionClient.queryProductDetailsAsync(queryProductDetailsParams) { p0, p1 ->
                if (p0.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (p1.productDetailsList.isNotEmpty()) {
                        _productListFlow.update {
                            it.copy(
                                productsList = getSkuFromList(p1.productDetailsList)
                            )
                        }
                        querySubscriptionHistory()
                    }
                }
            }
        }
    }

    private fun getSkuFromList(list: MutableList<ProductDetails>): Map<String, ProductDetails> {
        val skuDetailList: MutableMap<String, ProductDetails> = HashMap()
        list.forEach {
            it.productId.let { sku ->
                if (!TextUtils.isEmpty(sku)) {
                    skuDetailList[sku] = it
                }
            }
        }
        return skuDetailList
    }

    private val isSubscriptionSupported: Boolean
        get() {
            if (isBillingClientDead) {
                return false
            }
            return if (!subscriptionClient.isReady) {
                false
            } else subscriptionClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).responseCode == BillingClient.BillingResponseCode.OK
        }
    val isSubscriptionUpdateSupported: Boolean
        get() {
            if (isBillingClientDead) {
                return false
            }
            return if (!subscriptionClient.isReady) {
                false
            } else subscriptionClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS_UPDATE).responseCode == BillingClient.BillingResponseCode.OK
        }

    private fun resetAllPurchases() {
        subscribeProductToken = ""
        SUBSCRIBED_PRODUCT_ID = ""
        AdSharedPreference.getInstance(context).appAdPurchased = false
    }

    private fun getSku(skuList: MutableList<String>): String {
        return if (skuList.isNotEmpty()) {
            skuList[0]
        } else ""
    }

    private fun querySubscriptionHistory() {
        if (isBillingClientDead) {
            return
        }
        if (subscriptionClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).responseCode == BillingClient.BillingResponseCode.OK) {
            subscriptionClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
                    .build(), object : PurchasesResponseListener {
                    override fun onQueryPurchasesResponse(
                        p0: BillingResult, p1: MutableList<Purchase>
                    ) {
                        if (p0.responseCode == BillingClient.BillingResponseCode.OK) {
                            if (p1.isNotEmpty()) {
                                for (purchase in p1) {
                                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && checkSubscriptionsId(
                                            getSku(purchase.products)
                                        )
                                    ) {
                                        if (purchase.isAcknowledged) {
                                            setSubscribed(purchase)
                                            coroutineScope.launch {
                                                _appSubscribed.send(true)
                                            }
                                        } else {
                                            acknowledgedPurchase(purchase)
                                        }
                                        return
                                    }
                                }
                            }
                        }
                        resetAllPurchases()
                        coroutineScope.launch {
                            _appSubscribed.send(false)
                        }
                    }

                })
        } else {
            resetAllPurchases()
        }
    }

    fun setSubscribed(purchase: Purchase) {
        SUBSCRIBED_PRODUCT_ID = getSku(purchase.products)
        subscribeProductToken = purchase.purchaseToken
        AdSharedPreference.getInstance(context).appAdPurchased = true
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, list: List<Purchase>?) {
        Log.d("onPurchasesUpdated", "code : ${billingResult.responseCode}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            if (list.isNullOrEmpty()) {
                querySubscriptionHistory()
            } else {
                for (purchase in list) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && checkSubscriptionsId(
                            getSku(purchase.products)
                        )
                    ) {
                        try {
                            if (purchase.isAcknowledged) {
                                setSubscribed(purchase)
                                coroutineScope.launch {
                                    _appSubscribed.send(true)
                                }
                            } else {
                                acknowledgedPurchase(purchase)
                            }
                        } catch (_: Exception) {
                        }
                        break
                    }
                }
                querySubscriptionHistory()
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            querySubscriptionHistory()
        }
    }

    fun getSelectedSubscriptionId(selectedPlan: PLAN): String {
        return when (selectedPlan) {
            PLAN.WEEKLY -> {
                WEEKLY_SUBSCRIPTION_ID
            }

            PLAN.MONTHLY -> {
                MONTHLY_SUBSCRIPTION_ID
            }

            PLAN.YEARLY -> {
                YEARLY_SUBSCRIPTION_ID
            }

            else -> MONTHLY_SUBSCRIPTION_ID
        }
    }

    private fun checkSubscriptionsId(sku: String?): Boolean {
        when (sku) {
            WEEKLY_SUBSCRIPTION_ID, MONTHLY_SUBSCRIPTION_ID, YEARLY_SUBSCRIPTION_ID -> return true
        }
        return false
    }

    fun acknowledgedPurchase(purchase: Purchase) {
        if (isBillingClientDead) {
            return
        }
        val acknowledgePurchaseParams =
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        subscriptionClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult: BillingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                setSubscribed(purchase)
                coroutineScope.launch {
                    _appSubscribed.send(true)
                }
            }
        }
    }

    fun fetchProductsListIfNull() {
        coroutineScope.launch {
            setupConnection()
            if (_productListFlow.value.productsList.isEmpty()) {
                querySubscriptionProducts()
            } else {
                querySubscriptionHistory()
            }
        }
    }

    fun initBilling() {
        coroutineScope.launch {
            setupConnection()
        }
    }

    private fun setupConnection() {
        try {
            if (!::subscriptionClient.isInitialized) {
                subscriptionClient = BillingClient.newBuilder(context).enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                ).setListener(this@SubscriptionHelper).build()
            }
            if (!isBillingReady) {
                subscriptionClient.apply {
                    if (!this.isReady) {
                        this.startConnection(object : BillingClientStateListener {
                            override fun onBillingServiceDisconnected() {
                                isBillingReady = false
                            }

                            override fun onBillingSetupFinished(p0: BillingResult) {
                                if (p0.responseCode == BillingClient.BillingResponseCode.OK) {
                                    isBillingReady = true
                                    querySubscriptionProducts()
                                }
                            }
                        })
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun viewUrlContent(url: String) {
        try {
            Intent().apply {
                action = Intent.ACTION_VIEW
                data = url.toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addCategory(Intent.CATEGORY_BROWSABLE)
            }.also {
                if (it.resolveActivity(context.packageManager) != null) {
                    context.startActivity(it)
                }
            }
        } catch (ignored: Exception) {
        }
    }
}