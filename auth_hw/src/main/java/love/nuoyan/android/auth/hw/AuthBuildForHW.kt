package love.nuoyan.android.auth.hw

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.text.TextUtils
import android.util.Base64
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.agconnect.config.LazyInputStream
import com.huawei.hms.common.ApiException
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapApiException
import com.huawei.hms.iap.entity.*
import com.huawei.hms.iap.util.IapClientHelper
import com.huawei.hms.jos.AppParams
import com.huawei.hms.jos.JosApps
import com.huawei.hms.support.account.AccountAuthManager
import com.huawei.hms.support.account.request.AccountAuthParams
import com.huawei.hms.support.account.request.AccountAuthParamsHelper
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import com.huawei.updatesdk.service.otaupdate.CheckUpdateCallBack
import com.huawei.updatesdk.service.otaupdate.UpdateKey
import com.huawei.updatesdk.service.appmgr.bean.ApkUpgradeInfo
import love.nuoyan.android.auth.AbsAuthBuildForHW
import love.nuoyan.android.auth.Auth
import love.nuoyan.android.auth.HWPriceType
import kotlin.coroutines.resume

class AuthBuildForHW: AbsAuthBuildForHW() {
    override fun initSdk() {
        val config = AGConnectServicesConfig.fromContext(Auth.appContext)
        config.overlayWith(object : LazyInputStream(Auth.appContext) {
            override fun get(context: Context): InputStream? {
                return try {
                    context.assets.open("agconnect-services.json")
                } catch (e: IOException) {
                    null
                }
            }
        })
    }

    override fun onActivityCreate(activity: Activity, forceUpdate: Boolean) {
        val params = AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM
        val appsClient = JosApps.getJosAppsClient(activity)
        val initTask = appsClient.init(AppParams(params))
        initTask.addOnSuccessListener {
            // ???????????????
        }.addOnFailureListener {
            // ???????????????
        }

        val client = JosApps.getAppUpdateClient(activity)
        client.checkAppUpdate(activity, object : CheckUpdateCallBack {
            override fun onUpdateInfo(intent: Intent?) {
                intent?.let {
                    // ???????????????????????? Default_value????????????status?????????????????????????????????????????????
                    val status = intent.getIntExtra(UpdateKey.STATUS, 1001001)
                    // ????????????????????????
                    val rtnCode = intent.getIntExtra(UpdateKey.FAIL_CODE, 1001001)
                    // ???????????????????????????
                    val rtnMessage = intent.getStringExtra(UpdateKey.FAIL_REASON)
                    val info = intent.getSerializableExtra(UpdateKey.INFO)
                    // ?????????????????????info????????????ApkUpgradeInfo????????????????????????????????????
                    if (info is ApkUpgradeInfo) {
                        // ????????????showUpdateDialog????????????????????????
                        client.showUpdateDialog(activity, info, forceUpdate)
                    }
                    "onUpdateInfo status: $status, rtnCode: $rtnCode, rtnMessage: $rtnMessage"
                }
            }
            override fun onMarketInstallInfo(intent: Intent?) {
            }
            override fun onMarketStoreError(i: Int) {
            }
            override fun onUpdateStoreError(i: Int) {
            }
        })
    }

    override suspend fun jumpToManageSubsPage(activity: Activity) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val req = StartIapActivityReq()
        req.type = StartIapActivityReq.TYPE_SUBSCRIBE_MANAGER_ACTIVITY
        val mClient = Iap.getIapClient(activity)
        val task = mClient.startIapActivity(req)
        task.addOnSuccessListener { result ->
            result?.startActivity(activity)     // ????????????????????????IAP???????????????
            resultSuccess()
        }.addOnFailureListener {
            resultError(it.message, null, it)
        }.addOnCanceledListener {
            resultCancel()
        }
    }

    override suspend fun login() = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        AuthActivityForHW.callbackActivity = { activity ->
            // 1???????????????????????????AccountAuthParams????????????????????????id(openid???unionid)???email???profile(???????????????)???;
            // 2???DEFAULT_AUTH_REQUEST_PARAM???????????????id???profile??????????????????????????????;
            // 3??????????????????????????????????????????setEmail();
            // 4?????????setAuthorizationCode()???????????????code?????????????????????????????????????????????????????????????????????????????????
            val authParam = AccountAuthParamsHelper(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
                .setEmail()
                .setAuthorizationCode()
                .createParams()
            // ??????????????????????????????????????????????????????AccountAuthService
            val authService = AccountAuthManager.getService(activity, authParam)
            // ??????????????????????????????????????????
            val task = authService.silentSignIn()
            task.addOnSuccessListener { authAccount ->  // ????????????????????????????????????????????????AuthAccount??????????????????????????????
                resultSuccess(authAccount.toString(), authAccount.authorizationCode, activity)
            }
            task.addOnFailureListener { ae ->            //???????????????????????????getSignInIntent()??????????????????????????????
                if (ae is ApiException) {
                    AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                        if (requestCode == 8888) {
                            if (data == null) {
                                resultError("??????????????????", activity)
                            } else {
                                val authAccountTask = AccountAuthManager.parseAuthResultFromIntent(data)
                                when {
                                    authAccountTask.isSuccessful -> resultSuccess(authAccountTask.result.toString(), authAccountTask.result.authorizationCode, activity)
                                    authAccountTask.isCanceled -> resultCancel(activity)
                                    else -> {
                                        val e = authAccountTask.exception
                                        when {
                                            e is ApiException -> {
                                                if (e.statusCode == 2012) {
                                                    resultCancel(activity)
                                                } else {
                                                    resultError("code: ${e.statusCode}; msg: ${e.message}", activity, e)
                                                }
                                            }
                                            e != null -> resultError(e.message, activity, e)
                                            else -> resultError("????????????", activity)
                                        }
                                    }
                                }
                            }
                        } else {
                            resultError("requestCode ?????????$requestCode", activity)
                        }
                    }
                    // ????????????????????????????????????????????????????????????????????????Intent????????????????????????
                    // intent.putExtra(CommonConstant.RequestParams.IS_FULL_SCREEN, true)
                    activity.startActivityForResult(authService.signInIntent, 8888)
                } else {
                    resultError(ae.stackTraceToString(), activity, ae)
                }
            }
        }
        startAuthActivity(AuthActivityForHW::class.java)
    }

    override suspend fun cancelAuth(activity: Activity) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val authParam = AccountAuthParamsHelper(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            .setEmail()
            .setAuthorizationCode()
            .createParams()
        // ??????????????????????????????????????????????????????AccountAuthService
        val authService = AccountAuthManager.getService(activity, authParam)
        val task = authService.cancelAuthorization()
        task.addOnSuccessListener {
            resultSuccess("cancelAuthorization success", null)
        }
        task.addOnFailureListener { e ->
            resultError("cancelAuthorization failure:" + e.javaClass.simpleName, null, e)
        }
    }

    override suspend fun purchaseHistoryQuery(
        activity: Activity,
        priceType: HWPriceType,
        record: Boolean
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val req = OwnedPurchasesReq()
        req.priceType = priceType.code
        val task = if (record) {
            Iap.getIapClient(activity).obtainOwnedPurchaseRecord(req)
        } else {
            Iap.getIapClient(activity).obtainOwnedPurchases(req)
        }
        task.addOnSuccessListener { result ->
            val jsonArray = JSONArray()
            for (i in result.inAppPurchaseDataList.indices) {
                val inAppSignature = result.inAppSignature[i]
                val inAppPurchaseData = result.inAppPurchaseDataList[i]
                try {
                    val inAppPurchaseDataBean = InAppPurchaseData(inAppPurchaseData)
                    val jo = JSONObject()
                    // ??? purchaseState ??? 0 ?????????????????????????????????
                    jo.put("purchaseState", inAppPurchaseDataBean.purchaseState)
                    jo.put("orderSn", inAppPurchaseDataBean.developerPayload)
                    jo.put("purchaseToken", inAppPurchaseDataBean.purchaseToken)
                    jo.put("inAppSignature", inAppSignature)
                    jo.put("inAppPurchaseData", inAppPurchaseData)
                    jo.put("isSubValid", inAppPurchaseDataBean.isSubValid)
                    jsonArray.put(jo)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            resultSuccess("????????????", null, null, jsonArray)
        }.addOnFailureListener { e ->
            if (e is IapApiException) {
                resultError("code: ${e.statusCode}; msg: ${e.message}", null, e)
            } else {
                resultError(e?.message, null, e)
            }
        }
    }

    override suspend fun payCheck() = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        AuthActivityForHW.callbackActivity = { activity ->
            val task = Iap.getIapClient(activity).isEnvReady
            task.addOnSuccessListener { result ->
                if (result.returnCode == 0) {
                    resultSuccess(null, null, activity)
                } else {
                    resultError("code: ${result.returnCode}", activity)
                }
            }.addOnFailureListener { e ->
                if (e is IapApiException) {
                    if (e.status.statusCode == OrderStatusCode.ORDER_HWID_NOT_LOGIN) {  // ?????????
                        if (e.status.hasResolution()) {
                            try {
                                AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                                    if (requestCode == 6666 && data != null && IapClientHelper.parseRespCodeFromIntent(data) == 0) {
                                        resultSuccess(null, null, activity)
                                    } else {
                                        resultError("??????????????????", activity)
                                    }
                                }
                                e.status.startResolutionForResult(activity, 6666)
                            } catch (exp: SendIntentException) {
                                resultError("????????????????????????????????????: ${exp.message}", activity, e)
                            }
                        } else {
                            resultError("????????????????????????????????????: ${e.message}", activity, e)
                        }
                    } else if (e.status.statusCode == OrderStatusCode.ORDER_ACCOUNT_AREA_NOT_SUPPORTED) {
                        resultError("?????????????????? code: ${e.status.statusCode}; msg: ${e.status.statusMessage}", activity, e)
                    } else {
                        resultError("code: ${e.status.statusCode}; msg: ${e.status.errorString}", activity, e)
                    }
                } else {
                    resultError(e.message, activity, e)
                }
            }
        }
        startAuthActivity(AuthActivityForHW::class.java)
    }

    override suspend fun payConsume(
        activity: Activity,
        purchaseToken: String
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val req = ConsumeOwnedPurchaseReq()
        req.purchaseToken = purchaseToken
        // ??????????????????????????????????????????consumeOwnedPurchase??????????????????
        val task = Iap.getIapClient(activity).consumeOwnedPurchase(req)
        task.addOnSuccessListener { result ->
            resultSuccess("code: ${result.returnCode}; msg: ${result.errMsg}")
        }.addOnFailureListener { e ->
            if (e is IapApiException) {
                resultError(e.status.toString(), null, e)
            } else {
                resultError(e?.message, null, e)
            }
        }
    }

    override suspend fun payProductQuery(
        activity: Activity,
        productList: List<String>,
        priceType: HWPriceType,
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val req = ProductInfoReq()
        req.priceType = priceType.code       // priceType: 0??????????????????; 1?????????????????????; 2??????????????????
        req.productIds = productList
        val task = Iap.getIapClient(activity).obtainProductInfo(req)// ??????obtainProductInfo????????????AppGallery Connect????????????????????????????????????
        task.addOnSuccessListener { result ->
            if (result.returnCode == 0) {
                resultSuccess("????????????", null, null, result.productInfoList.map {
                    JSONObject().apply {
                        put("ProductId", it.productId)
                        put("PriceType", it.priceType)
                        put("Price", it.price)
                        put("MicrosPrice", it.microsPrice)
                        put("OriginalLocalPrice", it.originalLocalPrice)
                        put("OriginalMicroPrice", it.originalMicroPrice)
                        put("Currency", it.currency)
                        put("ProductName", it.productName)
                        put("ProductDesc", it.productDesc)
                        put("SubPeriod", it.subPeriod)
                        put("SubSpecialPrice", it.subSpecialPrice)
                        put("SubSpecialPriceMicros", it.subSpecialPriceMicros)
                        put("SubSpecialPeriod", it.subSpecialPeriod)
                        put("SubSpecialPeriodCycles", it.subSpecialPeriodCycles)
                        put("SubFreeTrialPeriod", it.subFreeTrialPeriod)
                        put("SubGroupId", it.subGroupId)
                        put("SubGroupTitle", it.subGroupTitle)
                        put("SubProductLevel", it.subProductLevel)
                        put("Status", it.status)
                    }
                })
            } else {
                resultError("code: ${result.returnCode}  msg: ${result.errMsg}")
            }
        }.addOnFailureListener { e ->
            if (e is IapApiException) {
                resultError(e.status.toString(), null, e)
            } else {
                resultError(e.stackTraceToString(), null, e)
            }
        }
    }

    override suspend fun payPMS(
        publicKey: String,
        productId: String,
        priceType: HWPriceType,
        developerPayload: String?
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        AuthActivityForHW.callbackActivity = { activity ->
            val req = PurchaseIntentReq()
            req.productId = productId
            req.priceType = priceType.code
            req.developerPayload = developerPayload
            val task = Iap.getIapClient(activity).createPurchaseIntent(req)
            task.addOnSuccessListener { result ->
                val status = result.status
                if (status.hasResolution()) {
                    try {
                        AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                            if (requestCode == 4444) {
                                payResult(activity, data, publicKey, developerPayload)
                            } else {
                                resultError("requestCode ?????????$requestCode", activity)
                            }
                        }
                        status.startResolutionForResult(activity, 4444)
                    } catch (exp: Exception) {
                        resultError("????????????: ${exp.message}", activity, exp)
                    }
                } else {
                    resultError(result.status.toString(), activity)
                }
            }.addOnFailureListener { e ->
                if (e is IapApiException) {
                    resultError(e.status.toString(), activity, e)
                } else {
                    resultError(e.stackTraceToString(), activity, e)
                }
            }
        }
        startAuthActivity(AuthActivityForHW::class.java)
    }
    override suspend fun payAmount(
        publicKey: String,
        priceType: HWPriceType,
        productId: String,
        productName: String,
        amount: String,
        sdkChannel: String,
        country: String,
        currency: String,
        developerPayload: String?,
        serviceCatalog: String
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        AuthActivityForHW.callbackActivity = { activity ->
            val req = PurchaseIntentWithPriceReq()
            req.priceType = priceType.code
            req.productId = productId
            req.productName = productName
            req.amount = amount
            req.sdkChannel = sdkChannel
            req.country = country
            req.currency = currency
            req.developerPayload = developerPayload
            req.serviceCatalog = serviceCatalog
            payAmount(activity, req, publicKey, developerPayload)
        }
        startAuthActivity(AuthActivityForHW::class.java)
    }
    private fun payAmount(activity: Activity, req: PurchaseIntentWithPriceReq, publicKey: String, developerPayload: String?) {
        val task = Iap.getIapClient(activity).createPurchaseIntentWithPrice(req)
        task.addOnSuccessListener { result ->
            val paymentData = result.paymentData
            val paymentSignature = result.paymentSignature
            if (doCheck(paymentData, paymentSignature, publicKey)) {
                val status = result.status
                if (status.hasResolution()) {
                    try {
                        AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                            if (requestCode == 7777) {
                                payResult(activity, data, publicKey, developerPayload)
                            } else {
                                resultError("requestCode ?????????$requestCode", activity)
                            }
                        }
                        status.startResolutionForResult(activity, 7777)
                    } catch (exp: SendIntentException) {
                        resultError("????????????????????????", activity, exp)
                    }
                } else {
                    resultError("????????????????????????: ${result.errMsg}", activity)
                }
            } else {
                resultError("????????????: paymentData=$paymentData paymentSignature=$paymentSignature result=$result", activity)
            }
        }.addOnFailureListener { e ->
            if (e is IapApiException) {
                when (e.statusCode) {
                    OrderStatusCode.ORDER_HWID_NOT_LOGIN, OrderStatusCode.ORDER_NOT_ACCEPT_AGREEMENT -> {
                        if (e.status.hasResolution()) {
                            try {
                                AuthActivityForHW.callbackActivityResult = { requestCode, _, data ->
                                    if (requestCode == 5555) {
                                        if (data != null) {             // ?????????????????????????????????
                                            val returnCode = IapClientHelper.parseRespCodeFromIntent(data)
                                            if (returnCode == OrderStatusCode.ORDER_STATE_SUCCESS) {
                                                payAmount(activity, req, publicKey, developerPayload) // ????????????????????????
                                            } else {
                                                resultError("code: $returnCode; msg: ????????????????????????", activity)
                                            }
                                        } else {
                                            resultError("????????????????????????????????????", activity)
                                        }
                                    } else {
                                        resultError("requestCode ?????????$requestCode", activity)
                                    }
                                }
                                e.status.startResolutionForResult(activity, 5555)
                            } catch (exp: SendIntentException) {
                                resultError("code: ${e.statusCode}; msg: ${e.message}; ????????????????????????", activity, exp)
                            }
                        } else {
                            resultError("code: ${e.statusCode}; msg: ${e.message}; ?????????????????????????????????", activity)
                        }
                    }
                    OrderStatusCode.ORDER_PRODUCT_OWNED -> resultError("code: ${e.statusCode}; msg: ${e.message}; ??????????????????", activity)
                    else -> resultError("code: ${e.statusCode}; msg: ${e.message}", activity)
                }
            } else {
                resultError("????????????: $e", activity, e)
            }
        }
    }
    private fun payResult(activity: Activity, data: Intent?, publicKey: String, developerPayload: String?) {
        if (data == null) {
            resultError("??????????????????", activity)
        } else {
            val purchaseResultInfo = Iap.getIapClient(activity).parsePurchaseResultInfoFromIntent(data)
            when (purchaseResultInfo.returnCode) {
                OrderStatusCode.ORDER_STATE_CANCEL -> resultCancel(activity)
                OrderStatusCode.ORDER_STATE_FAILED,
                OrderStatusCode.ORDER_PRODUCT_OWNED,
                    // todo ???????????????????????????
                OrderStatusCode.ORDER_STATE_DEFAULT_CODE ->
                    resultError("???????????????????????????????????????code: ${purchaseResultInfo.returnCode}; msg: ${purchaseResultInfo.errMsg}", activity, null, 1001)
                OrderStatusCode.ORDER_STATE_SUCCESS -> {
                    val iApd = purchaseResultInfo.inAppPurchaseData
                    val iAds = purchaseResultInfo.inAppDataSignature
                    if (doCheck(iApd, iAds, publicKey)) {
                        try {
                            val d = InAppPurchaseData(iApd)
                            if (d.purchaseState == 0 || d.purchaseState == -1) {
                                val jo = JSONObject()
                                jo.put("orderSn", developerPayload)
                                jo.put("purchaseToken", d.purchaseToken)
                                jo.put("inAppPurchaseData", iApd)
                                jo.put("inAppDataSignature", iAds)
                                jo.put("purchaseState", d.purchaseState)
                                resultSuccess("????????????", jo.toString(), activity, jo)
                            } else {
                                resultError("??????????????????: code=${d.purchaseState}", activity)
                            }
                        } catch (e: Exception) {
                            resultError("????????????????????????", activity, e)
                        }
                    } else {
                        resultError("????????????: iApd=$iApd; iAds=$iAds; purchaseResultInfo=${purchaseResultInfo}", activity)
                    }
                }
                else -> resultError("code: ${purchaseResultInfo.returnCode}; msg: ${purchaseResultInfo.errMsg}", activity)
            }
        }
    }

    /**
     * ??????????????????
     * @param content ???????????????
     * @param sign ???????????????
     * @param publicKey ????????????
     * @return ??????????????????
     */
    private fun doCheck(content: String, sign: String, publicKey: String): Boolean {
        try {
            if (!TextUtils.isEmpty(sign) && !TextUtils.isEmpty(publicKey)) {
                val keyFactory = KeyFactory.getInstance("RSA")
                val encodedKey = Base64.decode(publicKey, Base64.DEFAULT)
                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
                val signature = Signature.getInstance("SHA256WithRSA")
                signature.initVerify(pubKey)
                signature.update(content.toByteArray(StandardCharsets.UTF_8))
                val bSign = Base64.decode(sign, Base64.DEFAULT)
                return signature.verify(bSign)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}