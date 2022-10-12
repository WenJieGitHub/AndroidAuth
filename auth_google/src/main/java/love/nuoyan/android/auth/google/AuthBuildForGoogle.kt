package love.nuoyan.android.auth.google

import android.app.Activity
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.ProductType
import kotlinx.coroutines.*
import love.nuoyan.android.auth.*
import org.json.JSONObject
import kotlin.coroutines.resume


class AuthBuildForGoogle: AbsAuthBuildForGoogle() {
    // 构建 Client, 添加交易更新监听; listener 可接收应用中所有购买交易的更新
    private fun newClient(listener: PurchasesUpdatedListener): BillingClient {
        return BillingClient.newBuilder(Auth.appContext)
            .setListener(listener)
            .enablePendingPurchases()
            .build()
    }
    // 开始尝试建立与 Google Play 的连接
    private fun startConnection(client: BillingClient, onBillingSetupFinished: (billingResult: BillingResult) -> Unit) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> onBillingSetupFinished(result)
                    BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                    else -> resultError("GooglePlay 连接失败 请重试: code=${result.responseCode}  msg=${result.debugMessage}", null, null, 1002)
                }
            }
            override fun onBillingServiceDisconnected() {
                resultError("GooglePlay 断开连接, 请重试", null, null, 1001)
            }
        })
    }

    override suspend fun payProductQuery(
        productList: List<String>,
        productType: GoogleProductType
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (productList.isEmpty()) {
            resultError("payProductQuery productList 参数不能为空")
        } else {
            val client = newClient { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        val list = purchases?.map { JSONObject(it.originalJson) }
                        resultSuccess("payProductQuery 购买交易的更新", null, null, list)
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                    else -> resultError("payProductQuery 购买交易的更新: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                }
            }
            startConnection(client) {
                val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        productList.map { productId ->
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(
                                    when (productType) {
                                        GoogleProductType.INAPP ->  ProductType.INAPP
                                        GoogleProductType.SUBS ->  ProductType.SUBS
                                    }
                                )
                                .build()
                        }
                    )
                    .build()
                client.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
                    when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.OK -> {
                            val list = productDetailsList.map { productDetails ->
                                val oneTimePurchaseOfferDetails = productDetails.oneTimePurchaseOfferDetails?.let {
                                    GoogleProductDetails.OneTimePurchaseOfferDetails(
                                        it.formattedPrice,
                                        it.priceAmountMicros,
                                        it.priceCurrencyCode,
                                        it.zza()
                                    )
                                }
                                val subscriptionOfferDetails = productDetails.subscriptionOfferDetails?.let { list ->
                                    list.map {
                                        val pricingPhases = GoogleProductDetails.PricingPhases(
                                            it.pricingPhases.pricingPhaseList.map { pp ->
                                                GoogleProductDetails.PricingPhase(
                                                    pp.formattedPrice,
                                                    pp.priceAmountMicros,
                                                    pp.priceCurrencyCode,
                                                    pp.billingPeriod,
                                                    pp.billingCycleCount,
                                                    pp.recurrenceMode
                                                )
                                            }
                                        )
                                        GoogleProductDetails.SubscriptionOfferDetails(
                                            it.offerToken,
                                            it.offerTags,
                                            pricingPhases
                                        )
                                    }
                                }
                                GoogleProductDetails(
                                    productDetails,
                                    productDetails.toString(),
                                    productDetails.productId,
                                    productDetails.productType,
                                    productDetails.title,
                                    productDetails.name,
                                    productDetails.zza(),
                                    productDetails.description,
                                    oneTimePurchaseOfferDetails,
                                    subscriptionOfferDetails
                                )
                            }
                            resultSuccess("payProductQuery 查询商品成功", null, null, list)
                        }
                        BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                        else -> resultError("payProductQuery 查询商品失败: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                    }
                    client.endConnection()
                }
            }
        }
    }

    override suspend fun pay(
        activity: Activity,
        googleProductDetails: GoogleProductDetails,
        selectedOfferToken: String?,
        oldPurchaseToken: String?,
        prorationMode: ProrationMode,
        isOfferPersonalized: Boolean
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val client = newClient { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val list = purchases?.map { JSONObject(it.originalJson) }
                    resultSuccess("pay 购买交易更新", null, null, list)
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                else -> resultError("pay 购买交易更新: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
            }
        }
        startConnection(client) {
            val pd = googleProductDetails.productDetails as ProductDetails
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                    .setProductDetails(pd)
                    .apply {
                        // to get an offer token, call ProductDetails.subscriptionOfferDetails()
                        // for a list of offers that are available to the user
                        selectedOfferToken?.let { setOfferToken(selectedOfferToken) }
                    }
                    .build()
            )
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .apply { // 升降级时参数设置
                    if (!oldPurchaseToken.isNullOrEmpty()) {
                        setSubscriptionUpdateParams(
                            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                                .setOldPurchaseToken(oldPurchaseToken)
                                .setReplaceProrationMode(prorationMode.code)
                                .build()
                        )
                    }
                }
                .setIsOfferPersonalized(isOfferPersonalized)
                .build()
            // Launch the billing flow
            val billingResult = client.launchBillingFlow(activity, billingFlowParams)
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    // 走 newClient 回调监听
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    resultCancel()
                    client.endConnection()
                }
                else -> {
                    resultError("pay 启动购买流程失败: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                    client.endConnection()
                }
            }
        }
    }

    override suspend fun payConsume(purchaseToken: String) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val client = newClient { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val list = purchases?.map { JSONObject(it.originalJson) }
                    resultSuccess("payConsume 购买交易更新", null, null, list)
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                else -> resultError("payConsume 购买交易更新: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
            }
        }
        startConnection(client) {
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            client.consumeAsync(consumeParams) { billingResult, purchaseToken ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> resultSuccess("payConsume 购买商品消耗成功", purchaseToken, null)
                    BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                    else -> resultError("payConsume 购买商品消耗失败: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                }
                client.endConnection()
            }
        }
    }

    override suspend fun purchaseQuery(productType: GoogleProductType) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val client = newClient { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val list = purchases?.map { JSONObject(it.originalJson) }
                    resultSuccess("purchaseQuery 购买交易更新", null, null, list)
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                else -> resultError("purchaseQuery 购买交易更新: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
            }
        }
        startConnection(client) {
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(
                when (productType) {
                    GoogleProductType.INAPP -> ProductType.INAPP
                    GoogleProductType.SUBS -> ProductType.SUBS
                }
            ).build()) { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        resultSuccess("purchaseQuery", null, null, purchases.map { JSONObject(it.originalJson) })
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                    else -> resultError("purchaseQuery: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                }
                client.endConnection()
            }
        }
    }

    override suspend fun purchaseHistoryQuery(productType: GoogleProductType) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val client = newClient { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val list = purchases?.map { JSONObject(it.originalJson) }
                    resultSuccess("purchaseHistoryQuery 购买交易更新", null, null, list)
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                else -> resultError("purchaseHistoryQuery 购买交易更新: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
            }
        }
        startConnection(client) {
            client.queryPurchaseHistoryAsync(QueryPurchaseHistoryParams.newBuilder().setProductType(
                when (productType) {
                    GoogleProductType.INAPP -> ProductType.INAPP
                    GoogleProductType.SUBS -> ProductType.SUBS
                }
            ).build()) { billingResult, purchasesHistoryList ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        resultSuccess("purchaseHistoryQuery", null, null, purchasesHistoryList?.map { JSONObject(it.originalJson) })
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> resultCancel()
                    else -> resultError("purchaseHistoryQuery: code=${billingResult.responseCode}  msg=${billingResult.debugMessage}")
                }
                client.endConnection()
            }
        }
    }
}