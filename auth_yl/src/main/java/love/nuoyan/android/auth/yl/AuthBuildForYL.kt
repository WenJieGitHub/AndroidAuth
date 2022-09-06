package love.nuoyan.android.auth.yl

import com.unionpay.UPPayAssistEx
import kotlinx.coroutines.suspendCancellableCoroutine
import love.nuoyan.android.auth.AbsAuthBuildForYL
import kotlin.coroutines.resume

class AuthBuildForYL : AbsAuthBuildForYL() {

    override suspend fun pay(orderInfo: String, test: Boolean) = suspendCancellableCoroutine { coroutine ->
        mCallback = { coroutine.resume(it) }
        AuthActivityForYL.authBuildForYL = this
        AuthActivityForYL.callbackActivity = { activity ->
            AuthActivityForYL.callbackActivityResult = { _, _, data ->
                if (data != null && data.extras != null) {
                    val s = data.extras?.getString("pay_result")
                    when {
                        "success".equals(s, true) -> resultSuccess("支付成功", s, activity)
                        "cancel".equals(s, true) -> resultCancel(activity)
//                        "fail".equals(s, true) -> resultError("支付失败: ${data.extras.toString()}", activity)
                        else -> resultError("支付失败: ${data.extras.toString()}", activity)
                    }
                } else {
                    resultError("返回结果为空", activity)
                }
            }
            val i: Int = if (test) {                                            // 银联测试环境
                UPPayAssistEx.startPay(activity, null, null, orderInfo, "01")
            } else {                                                            // 银联正式环境
                UPPayAssistEx.startPay(activity, null, null, orderInfo, "00")
            }
            if (UPPayAssistEx.PLUGIN_VALID == i) {                              // 该终端已经安装控件，并启动控件

            } else if (UPPayAssistEx.PLUGIN_NOT_FOUND == i) {                   // 手机终端尚未安装支付控件，需要先安装支付控件
                resultError("请安装银联支付控件", activity)
            } else {
                resultError("未知异常: code=$i", activity)
            }
        }
        startAuthActivity(AuthActivityForYL::class.java)
    }
}