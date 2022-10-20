# Auth
- Auth 是一款第三方登陆、分享、支付的快速集成库。
- 支持平台包括微信、QQ、微博、支付宝、华为、小米、OPPO、银联、GooglePay。
- 支持 Intent 方式调用 twitter、facebook 等分享。
- 根据项目需求按需添加对应平台依赖。

## 配置项目

### 配置maven仓库
```groovy
maven { url 'https://maven.aliyun.com/repository/google' }
maven { url 'https://maven.aliyun.com/repository/public' }      // 小米有些库需要jcenter
maven { url 'https://jitpack.io' }                              // jitpack仓库
maven { url 'https://developer.huawei.com/repo/' }              // 华为仓库
maven {                                                         // 小米仓库
    credentials {
        username '5f45c9022d5925c55bc00c6f'
        password 'NQwPJAa42nlV'
    }
    url 'https://packages.aliyun.com/maven/repository/2028284-release-awMPKn/'
}
```

### 配置微博支持的 SO 架构
```groovy
ndk { abiFilters 'armeabi', 'armeabi-v7a', 'arm64-v8a' }
```

### 添加依赖
```groovy
// 版本不固定
implementation 'androidx.appcompat:appcompat:1.5.1'
// 版本不固定  QQ 库需要引入 okhttp3
implementation 'com.squareup.okhttp3:okhttp:4.9.1'

def auth_version = "0.0.6"
implementation "love.nuoyan.android:auth:$auth_version"
implementation "love.nuoyan.android:auth_google:$auth_version"
implementation "love.nuoyan.android:auth_oppo:$auth_version"
implementation "love.nuoyan.android:auth_hw:$auth_version"
implementation "love.nuoyan.android:auth_qq:$auth_version"
implementation "love.nuoyan.android:auth_wb:$auth_version"
implementation "love.nuoyan.android:auth_wx:$auth_version"
implementation "love.nuoyan.android:auth_xm:$auth_version"
implementation "love.nuoyan.android:auth_yl:$auth_version"
implementation "love.nuoyan.android:auth_zfb:$auth_version"
```

### app build.gradle 中配置相应平台参数，未依赖平台可忽略
```groovy
manifestPlaceholders = [
        // OPPO
        OPPODebug:"false",
        OPPOAppKey:"xxx",
        OPPOAppSecret:"xxx",
        // QQ 注意前缀tencent; Authorities 为 Manifest 文件中注册 FileProvider 时设置的 authorities 属性值
        QQAppId:"tencentxxx",
        QQAuthorities:"xxx",
        // 微博 注意前缀wb
        WBAppKey:"wbxxx",
        WBScope:"xxx",
        WBRedirectUrl:"xxx",
        // 微信
        WXAppId:"",
        // 小米 注意前缀
        XMAppId:"xmxxx",
        XMAppKey:"xmxxx",
        XMRedirectUri:"xxx",
        // 支付宝
        ZFBScheme:"xxx",
]
```

### 集成华为 SDK 时需要配置 json 文件，json 文件来自华为
- assets 中添加 agconnect-services.json 文件


## 使用

### 同意隐私协议后初始化
```kotlin
// 同意隐私协议后，初始化 华为SDK 小米SDK 微博SDK，这三个平台如果集成需要提前初始化
Auth.init(application)
```

### 微信配置，注册回调
```kotlin
Auth.withWX().registerCallback {
    // 微信请求数据会在此回调内，按需解析数据
}
```

### 应用打开主页时调用，OPPO、华为、小米需要（根据联运要求）
```kotlin
Auth.withHW().onActivityCreate(activity)
Auth.withOPPO().onActivityCreate(activity)
Auth.withXM().onActivityCreate(activity)
```

### 登陆、分享功能，注意调用的如果是 suspend 函数需要在携程内调用
```kotlin
lifecycleScope.launch {
    val loginResult = Auth.withWX().login()
    val shareLinkResult = Auth.withWX().shareLink("http://www.baidu.com")
    // 根据 result 判断是否成功
}
```

### 华为支付流程
1. 支付前检查是否支持
2. PMS商品，需要先查询商品列表
3. 调用支付
4. 消耗型商品和服务器核对后消耗（服务端也可消耗）

#### 消耗型商品的补单
1. 购买消耗型商品支付后返回 AuthResult 为 Error，且 code=1001 时
2. 应用启动时
3. 调用以下代码查询，根据返回数据进行补单操作，一般数据上传给服务器进行后续操作
4. 订阅和非消耗型商品也可通过此接口查询记录

```kotlin
Auth.withHW.purchaseHistoryQuery(activity, 0, false)
```

### google 支付
- 结果返回 Error 时，如果 code=1001、1002 请再次重新尝试购买（连接Google结算库失败，重试次数自定义）

### 小米支付
- 小米依赖支付宝 sdk，集成时需要添加支付宝集成；
- * 支付前判断是否登录状态，未登录先调用登录

### 使用系统分享：更多分享、根据包名调用应用分享
1. 调用 Auth.withMore()
2. 需要传参目标应用包名，在清单文件中添加<queries>标签，并将目标应用包名加入
3. 库中已经添加<queries>标签，无需再添加的标签：
```xml
    <queries>
        <package android:name="com.twitter.android" />
        <package android:name="com.whatsapp" />
        <package android:name="com.linkedin.android" />
        <package android:name="com.instagram.android" />
        <package android:name="com.facebook.katana" />
    </queries>
```

## 第三方库版本及对应链接
- [微信 : 6.8.0](https://open.weixin.qq.com/cgi-bin/showdocument?action=dir_list&t=resource/res_list&verify=1&id=1417751808&token=&lang=zh_CN)
- [QQ : 3.5.5](https://wiki.open.qq.com/index.php?)
  - 3.5.11 版本会有异常：引入 okhttp3 后，java.lang.NoClassDefFoundError: Failed resolution of: Lokhttp3/internal/Version;
- [微博 : 12.5.0](https://github.com/sinaweibosdk/weibo_android_sdk)
- [支付宝: 15.8.10](https://docs.open.alipay.com/204/105296/)
- [华为联运: 6.4.0](https://developer.huawei.com/consumer/cn/doc/development/HMS-Guides/iap-development-guide-v4)
- [小米联运: 3.5.3](https://dev.mi.com/distribute/doc/details?pId=1150#6)
- OPPO联运 离线:2.0.0
- [银联: 3.4.0](https://open.unionpay.com/tjweb/doc/mchnt/list?productId=3)
- [Google Pay billing-ktx:5.0.0](https://developer.android.com/google/play/billing/integrate#fetch)
