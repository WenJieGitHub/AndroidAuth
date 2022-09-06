package love.nuoyan.android.auth.wx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbiz.OpenWebview
import com.tencent.mm.opensdk.modelbiz.WXOpenBusinessWebview
import com.tencent.mm.opensdk.modelmsg.*
import com.tencent.mm.opensdk.modelpay.PayReq
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import love.nuoyan.android.auth.AbsAuthBuildForWX
import love.nuoyan.android.auth.Auth
import love.nuoyan.android.auth.AuthResult
import love.nuoyan.android.auth.WXShareScene
import kotlin.coroutines.resume

class AuthBuildForWX : AbsAuthBuildForWX() {
    internal companion object {
        internal var mAPI: IWXAPI? = null
        var mAppId: String? = null

        init {
            initApi()
        }

        internal fun initApi() {
            if (mAPI == null) {
                mAppId = Auth.getMetaData("WXAppId")
                require(!mAppId.isNullOrEmpty())  { "请配置 WXAppId" }
                mAPI = WXAPIFactory.createWXAPI(Auth.appContext, mAppId, true)
                mAPI?.registerApp(mAppId)

                // 动态监听微信启动广播进行注册到微信
                Auth.appContext.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        mAPI?.registerApp(mAppId)
                    }
                }, IntentFilter(ConstantsAPI.ACTION_REFRESH_WXAPP))
            }
        }
    }

    init {
        initApi()
    }

    override fun registerCallback(callback: (result: AuthResult) -> Unit) {
        AuthActivityForWX.callback = callback
    }

    override fun checkAppInstalled(): AuthResult {
        return try {
            if (mAPI?.isWXAppInstalled == true) {
                AuthResult.Success("已经安装了微信客户端")
            } else {
                AuthResult.Uninstalled
            }
        } catch (e: Exception) {
            AuthResult.Error(e.stackTraceToString(), e)
        }
    }

    override suspend fun login() = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 API 初始化失败")
        } else {
            AuthActivityForWX.authBuildForWX = this
            mAPI?.sendReq(SendAuth.Req().apply {
                scope = "snsapi_userinfo"
                state = mSign
            })
        }
    }

    override suspend fun shareLink(
        url: String,
        title: String?,
        des: String?,
        thumb: ByteArray?,
        shareScene: WXShareScene
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 API 初始化失败")
        } else {
            AuthActivityForWX.authBuildForWX = this
            mAPI?.sendReq(SendMessageToWX.Req().apply {
                transaction = mSign
                message = WXMediaMessage(WXWebpageObject(url)).apply {
                    this.title = title
                    description = des
                    thumbData = thumb
                }
                scene = when (shareScene) {
                    WXShareScene.Session -> SendMessageToWX.Req.WXSceneSession
                    WXShareScene.Timeline -> SendMessageToWX.Req.WXSceneTimeline
                    WXShareScene.Favorite -> SendMessageToWX.Req.WXSceneFavorite
                }
            })
        }
    }

    override suspend fun shareImage(
        bitmap: Bitmap,
        title: String?,
        des: String?,
        thumb: ByteArray?,
        shareScene: WXShareScene
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 API 初始化失败")
        } else {
            AuthActivityForWX.authBuildForWX = this
            mAPI?.sendReq(SendMessageToWX.Req().apply {
                transaction = mSign
                message = WXMediaMessage(WXImageObject(bitmap)).apply {
                    this.title = title
                    description = des
                    thumbData = thumb
                }
                scene = when (shareScene) {
                    WXShareScene.Session -> SendMessageToWX.Req.WXSceneSession
                    WXShareScene.Timeline -> SendMessageToWX.Req.WXSceneTimeline
                    WXShareScene.Favorite -> SendMessageToWX.Req.WXSceneFavorite
                }
            })
        }
    }

    override suspend fun pay(
        partnerId: String,
        prepayId: String,
        nonceStr: String,
        timeStamp: String,
        sign: String,
        packageValue: String
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 API 初始化失败")
        } else {
            AuthActivityForWX.authBuildForWX = this
            val req = PayReq()
            req.appId = mAppId
            req.partnerId = partnerId
            req.prepayId = prepayId
            req.packageValue = packageValue
            req.nonceStr = nonceStr
            req.timeStamp = timeStamp
            req.sign = sign
            req.transaction = mSign     // 回调时这个标记为 null, 只有 prePayId 可用, 所以使用 prePayId 作为标记
            mAPI?.sendReq(req)
        }
    }

    override suspend fun payTreaty(
        data: String,
        useOld: Boolean
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微信 API 初始化失败")
        } else if (useOld) {
            AuthActivityForWX.authBuildForWX = this
            val req = OpenWebview.Req()
            req.transaction = mSign             // 回调时这个标记和设置的不一样, 无法作为判断依据
            req.url = data
            mAPI?.sendReq(req)
        } else {
            AuthActivityForWX.authBuildForWX = this
            val req = WXOpenBusinessWebview.Req()
            req.transaction = mSign
            req.businessType = 12               // 固定值
            req.queryInfo = hashMapOf(Pair("pre_entrustweb_id", data))
            mAPI?.sendReq(req)
        }
    }
}