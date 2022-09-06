package love.nuoyan.android.auth.qq

import android.os.Bundle
import android.text.TextUtils
import com.tencent.connect.share.QQShare
import com.tencent.tauth.IUiListener
import com.tencent.tauth.Tencent
import com.tencent.tauth.UiError
import kotlinx.coroutines.suspendCancellableCoroutine
import love.nuoyan.android.auth.AbsAuthBuildForQQ
import love.nuoyan.android.auth.Auth
import love.nuoyan.android.auth.AuthResult
import org.json.JSONObject
import kotlin.coroutines.resume

class AuthBuildForQQ : AbsAuthBuildForQQ() {
    internal companion object {
        internal var mAPI: Tencent? = null

        init {
            initApi()
        }

        @Synchronized
        internal fun initApi() {
            if (mAPI == null) {
                val id = Auth.getMetaData("QQAppId")
                val authorities = Auth.getMetaData("QQAuthorities")
                require(!TextUtils.isEmpty(id) && id != "0")  { "请配置 QQAppId" }
                // 其中 Authorities 为 Manifest 文件中注册 FileProvider 时设置的 authorities 属性值
                mAPI = Tencent.createInstance(id?.replace("tencent", ""), Auth.appContext, authorities)
            }
        }
    }

    init {
        initApi()
    }

    override fun checkAppInstalled(): AuthResult {
        return try {
            if (mAPI == null) {
                AuthResult.Error("QQ API 初始化失败")
            } else if (mAPI?.isQQInstalled(Auth.appContext) == true) {
                AuthResult.Success("已经安装了QQ客户端")
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
            resultError("QQ API 初始化失败")
        } else if (mAPI?.isQQInstalled(Auth.appContext) == true) {
            AuthActivityForQQ.authBuildForQQ = this
            AuthActivityForQQ.callbackActivity = { activity ->
                val listener = object : IUiListener {
                    override fun onComplete(any: Any?) {
                        try {
                            (any as? JSONObject)?.let {
                                if (it.optInt("ret") == 0 && !TextUtils.isEmpty(it.optString("access_token"))) {
                                    resultSuccess(it.toString(), it.optString("access_token"), activity)
                                    return
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        resultError("返回结果异常: $any", activity)
                    }
                    override fun onCancel() {
                        resultCancel(activity)
                    }
                    override fun onError(e: UiError?) {
                        resultError(e?.toString(), activity)
                    }
                    override fun onWarning(p0: Int) {
                    }
                }
                AuthActivityForQQ.callbackActivityResult = { requestCode, resultCode, data ->
                    Tencent.onActivityResultData(requestCode, resultCode, data, listener)
                }
                mAPI?.login(activity, "all", listener, false)
            }
            startAuthActivity(AuthActivityForQQ::class.java)
        } else {
            resultUninstalled()
        }
    }

    override suspend fun shareLink(
        targetUrl: String,
        title: String,
        summary: String?,
        imageUrl: String?
    ) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        if (mAPI == null) {
            resultError("微博 API 初始化失败")
        } else if (mAPI?.isQQInstalled(Auth.appContext) == true) {
            AuthActivityForQQ.authBuildForQQ = this
            AuthActivityForQQ.callbackActivity = { activity ->
                val listener = object : IUiListener {
                    override fun onComplete(any: Any?) {
                        try {
                            (any as? JSONObject)?.let {
                                if (it.optInt("ret") == 0) {
                                    resultSuccess(it.toString(), activity = activity)
                                    return
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        resultError("信息异常: $any", activity)
                    }
                    override fun onCancel() {
                        resultCancel(activity)
                    }
                    override fun onError(e: UiError?) {
                        resultError(e?.toString(), activity)
                    }
                    override fun onWarning(p0: Int) {
                    }
                }
                AuthActivityForQQ.callbackActivityResult = { requestCode, resultCode, data ->
                    Tencent.onActivityResultData(requestCode, resultCode, data, listener)
                }
                val shareParams = Bundle()                     // 分享参数
                shareParams.putString(QQShare.SHARE_TO_QQ_TARGET_URL, targetUrl)
                shareParams.putString(QQShare.SHARE_TO_QQ_TITLE, title)
                summary?.let { shareParams.putString(QQShare.SHARE_TO_QQ_SUMMARY, it) }
                imageUrl?.let { shareParams.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, it) }
                shareParams.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT)
                mAPI?.shareToQQ(activity, shareParams, listener)
            }
            startAuthActivity(AuthActivityForQQ::class.java)
        } else {
            resultUninstalled()
        }
    }
}