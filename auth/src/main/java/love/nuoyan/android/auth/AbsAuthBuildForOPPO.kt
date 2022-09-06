package love.nuoyan.android.auth

import android.app.Activity

abstract class AbsAuthBuildForOPPO : AbsAuthBuild() {
    /** 程序启动后主页面调用 */
    abstract fun onActivityCreate(activity: Activity)

    /** 程序退出时调用, 目前有可能要求不添加 */
    abstract fun exit(activity: Activity)

    /**
     * 支付
     * @param orderId 订单号
     * @param amount 消费总金额，单位为分
     * @param productName 商品名(不能有+号等特殊符号)
     * @param productDesc 商品描述(不能有+号等特殊符号)
     * @param attach 自定义回调字段
     * @param callbackUrl 支付回调地址,不需要服务端回调的可不填
     * @param useCachedChannel 是否直接使用上一次支付所用的方式，比如上次用微信，这次直接跳微信支付 默认false
     */
    abstract suspend fun pay(
        orderId: String,
        amount: Int,
        productName: String,
        productDesc: String?,
        attach: String?,
        callbackUrl: String?,
        useCachedChannel: Boolean = false
    ): AuthResult
}
