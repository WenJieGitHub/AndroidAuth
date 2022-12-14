package love.nuoyan.android.auth.xm

import android.app.Activity
import android.app.Application
import android.text.TextUtils
import android.util.Log
import com.xiaomi.gamecenter.sdk.MiAccountType
import com.xiaomi.gamecenter.sdk.MiCode
import com.xiaomi.gamecenter.sdk.MiCommplatform
import com.xiaomi.gamecenter.sdk.MiLoginType
import com.xiaomi.gamecenter.sdk.entry.MiAppInfo
import com.xiaomi.gamecenter.sdk.entry.MiBuyInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import love.nuoyan.android.auth.AbsAuthBuildForXM
import love.nuoyan.android.auth.Auth
import love.nuoyan.android.auth.XMAccountType
import love.nuoyan.android.auth.XMLoginType
import org.json.JSONObject
import kotlin.coroutines.resume

class AuthBuildForXM : AbsAuthBuildForXM() {
    override fun initSdk(application: Application) {
        MiCommplatform.setApplication(application)
    }

    override fun onActivityCreate(activity: Activity, isDialog: Boolean, isToast: Boolean) {
        val id = Auth.getMetaData("XMAppId")?.replace("xm", "")
        require(!TextUtils.isEmpty(id))  { "请配置 XMAppId" }
        val key = Auth.getMetaData("XMAppKey")?.replace("xm", "")
        require(!TextUtils.isEmpty(key))  { "请配置 XMAppKey" }

        val appInfo = MiAppInfo()
        appInfo.appId = id
        appInfo.appKey = key
        MiCommplatform.Init(activity, appInfo) { code: Int, msg: String? ->
            when (code) {
                MiCode.MI_INIT_SUCCESS -> {               // 初始化小米 SDK 成功
                    MiCommplatform.getInstance().isAlertDialogDisplay = isDialog
                    MiCommplatform.getInstance().isToastDisplay = isToast
                }
                else -> { Log.e("Auth", "小米SDK初始化失败: $msg") }
            }
        }
    }

    override fun onActivityDestroy() {
        MiCommplatform.getInstance().removeAllListener()
    }

    override suspend fun login(
        activity: Activity,
        type: XMLoginType,
        account: XMAccountType,
        extra: String?
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val lt = when (type) {
            XMLoginType.AutoFirst -> MiLoginType.AUTO_FIRST
            XMLoginType.AutoOnly -> MiLoginType.AUTO_ONLY
            XMLoginType.ManualOnly -> MiLoginType.MANUAL_ONLY
        }
        val at = when (account) {
            XMAccountType.App -> MiAccountType.APP
            XMAccountType.XM -> MiAccountType.MI_SDK
        }
        MiCommplatform.getInstance().miLogin(activity, { code, account ->
            when (code) {
                MiCode.MI_LOGIN_SUCCESS -> {            // 登录成功
                    val uid = account?.uid              // 获取用户的登录后的UID（即用户唯一标识）
                    val session = account?.sessionId    // 获取用户的登陆的Session（请参考5.3.3流程校验Session有效性），可选,12小时过期
                    val unionId = account?.unionId      // 用于验证在不同应用中 是否为同一用户, 如果为空 则代表没有开启unionID权限
                    // 请开发者完成将uid和session提交给开发者自己服务器进行session验证
                    if (!TextUtils.isEmpty(uid) && !TextUtils.isEmpty(session)) {
                        val json = JSONObject().put("uid", uid).put("session", session).put("unionId", unionId).toString()
                        resultSuccess("code: $code", json)
                    } else {
                        resultError("code = $code")
                    }
                }
                MiCode.MI_ERROR_LOGIN_CANCEL -> resultCancel()
                else -> resultError("errorCode = $code")      // 登录失败,详细错误码见5.4 返回码
            }
        }, lt, at, extra)
    }

    override suspend fun payAmount(
        activity: Activity,
        orderId: String,
        amount: Int,
        userInfo: String
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val miBuyInfo = MiBuyInfo()
        miBuyInfo.cpOrderId = orderId      // 订单号唯一（不为空）
        miBuyInfo.cpUserInfo = userInfo    // 此参数在用户支付成功后会透传给CP的服务器
        miBuyInfo.feeValue = amount        // 必须是大于0的整数，100代表1元人民币（不为空）
        MiCommplatform.getInstance().miUniPay(activity, miBuyInfo) { code: Int, msg: String? ->
            when (code) {
                MiCode.MI_PAY_SUCCESS -> resultSuccess(msg)         // 购买成功，建议先通过自家服务器校验后再处理发货
                MiCode.MI_ERROR_PAY_CANCEL -> resultCancel()        // 购买取消
                else -> resultError("code=$code; msg=$msg")     // 购买失败,详细错误码见5.4 返回码
            }
        }
    }

    override suspend fun payCode(
        activity: Activity,
        orderId: String,
        productCode: String,
        quantity: Int
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val miBuyInfo = MiBuyInfo()
        miBuyInfo.cpOrderId = orderId          // 订单号唯一（不为空）
        miBuyInfo.productCode = productCode    // 商品代码，开发者申请获得（不为空）
        miBuyInfo.quantity = quantity          // 购买数量(商品数量最大9999，最小1)（不为空）
        MiCommplatform.getInstance().miUniPay(activity, miBuyInfo) { code: Int, msg: String? ->
            when (code) {
                MiCode.MI_PAY_SUCCESS -> resultSuccess(msg)     // 按计费代码 购买成功，建议先通过自家服务器校验后再处理发货
                MiCode.MI_ERROR_PAY_CANCEL -> resultCancel()    // 购买取消
                else -> resultError("code=$code; msg=$msg") // 购买失败,详细错误码见5.4 返回码
            }
        }
    }

    override suspend fun payTreaty(
        activity: Activity,
        orderId: String,
        productCode: String,
        quantity: Int
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        val miBuyInfo = MiBuyInfo()
        miBuyInfo.cpOrderId = orderId          // 订单号唯一（不为空）
        miBuyInfo.productCode = productCode    // 商品代码，开发者申请获得（不为空）
        miBuyInfo.quantity = quantity          // 购买数量(商品数量最大9999，最小1)（不为空）
        MiCommplatform.getInstance().miSubscribe(activity, miBuyInfo) { code: Int, msg: String? ->
            when (code) {
                MiCode.MI_SUB_SUCCESS -> resultSuccess(msg)     // 订阅成功
                MiCode.MI_ERROR_PAY_CANCEL -> resultCancel()    // 购买取消
                else -> resultError("code=$code; msg=$msg") // 购买失败,详细错误码见5.4 返回码
            }
        }
    }
}