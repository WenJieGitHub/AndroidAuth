# AuthForAndroid

## 第三方库版本
- [华为联运: 6.4.0](https://developer.huawei.com/consumer/cn/doc/development/HMS-Guides/iap-development-guide-v4)
- OPPO联运 离线:2.0.0
- [QQ : 3.5.5](https://wiki.open.qq.com/index.php?)
  - 3.5.11 版本会有异常：引入 okhttp3 后，java.lang.NoClassDefFoundError: Failed resolution of: Lokhttp3/internal/Version;
- [微博 : 12.5.0](https://github.com/sinaweibosdk/weibo_android_sdk)
- [微信 : 6.8.0](https://open.weixin.qq.com/cgi-bin/showdocument?action=dir_list&t=resource/res_list&verify=1&id=1417751808&token=&lang=zh_CN)
- [小米联运: 3.5.3](https://dev.mi.com/distribute/doc/details?pId=1150#6)
- [银联: 3.4.0](https://open.unionpay.com/tjweb/doc/mchnt/list?productId=3)
- [支付宝: 15.8.10](https://docs.open.alipay.com/204/105296/)
- [Google Pay billing-ktx:5.0.0](https://developer.android.com/google/play/billing/integrate#fetch)

## 配置maven仓库
```groovy
maven { url 'https://www.jitpack.io' }                          // jitpack仓库
maven { url 'https://developer.huawei.com/repo/' }              // 华为仓库
maven {                                                         // 小米仓库
    credentials {
        username '5f45c9022d5925c55bc00c6f'
        password 'NQwPJAa42nlV'
    }
    url 'https://packages.aliyun.com/maven/repository/2028284-release-awMPKn/'
}
```

## 微博支持的 SO 架构
```groovy
ndk { abiFilters 'armeabi', 'armeabi-v7a', 'arm64-v8a' }
```

## 添加依赖
```groovy
// 版本不固定
implementation 'androidx.appcompat:appcompat:1.5.0'
// 版本不固定  QQ 库需要引入 okhttp3
implementation 'com.squareup.okhttp3:okhttp:4.9.1'

implementation "love.nuoyan.android:auth:0.0.3"
implementation "love.nuoyan.android:auth_google:0.0.3"
implementation "love.nuoyan.android:auth_hw:0.0.3"
implementation "love.nuoyan.android:auth_oppo:0.0.3"
implementation "love.nuoyan.android:auth_qq:0.0.3"
implementation "love.nuoyan.android:auth_wb:0.0.3"
implementation "love.nuoyan.android:auth_wx:0.0.3"
implementation "love.nuoyan.android:auth_xm:0.0.3"
implementation "love.nuoyan.android:auth_yl:0.0.3"
implementation "love.nuoyan.android:auth_zfb:0.0.3"
```

## app build.gradle 中配置
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
- 华为集成：assets 中添加 agconnect-services.json 文件

## 初始化
```kotlin
// 在同意隐私协议后
Auth.init(applicationContext)
```

## 使用
1. 注册回调，仅微信需要
```kotlin
Auth.withWX().registerCallback {
    // 微信请求数据会在此回调内
}
```
2. 应用打开主页时调用，OPPO、华为、小米需要（根据联运要求）
```kotlin
Auth.withHW().onActivityCreate(activity)
Auth.withOPPO().onActivityCreate(activity)
Auth.withXM().onActivityCreate(activity)
```

3. 登陆、分享功能，注意调用的如果是 suspend 函数需要在携程内调用
```kotlin
lifecycleScope.launch {
    val loginResult = Auth.withWX().login()
    val shareLinkResult = Auth.withWX().shareLink("http://www.baidu.com")
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
- 支付前判断是否登录状态，未登录先调用登录

## 测试
- 配置签名
```groovy
    signingConfigs {
        release {
            storeFile file("$projectDir/xxx")
            keyAlias "xxx"
            keyPassword "xxx"
            storePassword "xxx"
            v1SigningEnabled true
            v2SigningEnabled true
        }
    }
```
- 配置Flavors
```groovy
    productFlavors {
        auth {
            applicationId("你的包名")
            resValue("string", "app_name", "测试")
            buildConfigField("boolean", "isDebug", "true")
            manifestPlaceholders = [
                    WXAppId:"xxx",

                    WBAppKey:"wbxxx",
                    WBScope:"xxx",
                    WBRedirectUrl:"xxx",

                    ZFBScheme:"xxx",

                    QQAppId:"tencentxxx",
                    QQAuthorities:"${applicationId}.fileProvider",

                    XMAppId:"xmxxx",
                    XMAppKey:"xmxxx",
                    XMRedirectUri:"xxx",

                    OPPODebug:"false",
                    OPPOAppKey:"xxx",
                    OPPOAppSecret:"xxx",
            ]
        }
    }
```

## 使用系统分享
1. 调用 Auth.withMore()
2. 需要传参目标应用包名，需要在清单文件中添加<queries>标签，并将目标应用包名加入
3. 库中已经添加<queries>标签内包名为：
```xml
    <queries>
        <package android:name="com.twitter.android" />
        <package android:name="com.whatsapp" />
        <package android:name="com.linkedin.android" />
        <package android:name="com.instagram.android" />
        <package android:name="com.facebook.katana" />
    </queries>
```