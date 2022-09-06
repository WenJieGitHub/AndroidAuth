package love.nuoyan.android.auth.oppo

import android.app.Activity
import com.nearme.game.sdk.GameCenterSDK
import com.nearme.game.sdk.callback.SinglePayCallback
import com.nearme.game.sdk.common.model.biz.PayInfo
import com.nearme.game.sdk.common.util.AppUtil
import com.nearme.platform.opensdk.pay.PayResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import love.nuoyan.android.auth.AbsAuthBuildForOPPO
import love.nuoyan.android.auth.Auth
import kotlin.coroutines.resume

class AuthBuildForOPPO: AbsAuthBuildForOPPO() {
    private companion object {
        var mAppSecret: String? = null

        init {
            initApi()
        }

        fun initApi() {
            if (mAppSecret == null) {
                mAppSecret = Auth.getMetaData("OPPOAppSecret")
                require(!mAppSecret.isNullOrEmpty())  { "请配置 OPPOAppSecret" }
            }
        }
    }
    init {
        initApi()
    }

    override fun onActivityCreate(activity: Activity) {
        GameCenterSDK.init(mAppSecret, activity)
        destroy()
    }

    override fun exit(activity: Activity) {
        GameCenterSDK.getInstance().onExit(activity) { AppUtil.exitGameProcess(activity) }
        destroy()
    }

    override suspend fun pay(
        orderId: String,
        amount: Int,
        productName: String,
        productDesc: String?,
        attach: String?,
        callbackUrl: String?,
        useCachedChannel: Boolean
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }

        val info = PayInfo(orderId, attach, amount)
        info.productName = productName
        info.productDesc = productDesc
        info.callbackUrl = callbackUrl
        info.useCachedChannel = useCachedChannel

        GameCenterSDK.getInstance().doSinglePay(Auth.appContext, info, object : SinglePayCallback {
            override fun onCallCarrierPay(payInfo: PayInfo, bySelectSMSPay: Boolean) {      // 可以忽略该回调方法，运营商支付
                resultSuccess("忽略该回调方法，运营商支付: $payInfo")
            }
            override fun onSuccess(resultMsg: String) {                                     // 支付成功处理逻辑
                resultSuccess(resultMsg)
            }
            override fun onFailure(resultMsg: String, resultCode: Int) {                    // 支付失败处理逻辑
                if (PayResponse.CODE_CANCEL == resultCode) {
                    resultCancel()
                } else {
                    resultError("code: $resultCode; msg: $resultMsg")
                }
            }
        })
    }
}