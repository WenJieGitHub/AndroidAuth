package love.nuoyan.android.auth

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import java.lang.reflect.InvocationTargetException

object Auth {
    lateinit var appContext: Application
    val separatorLine = System.getProperty("line.separator") ?: "\n"        // 换行符

    /**
     * 初始化 Auth，同意隐私协议后
     */
    fun init(application: Application) = apply {
        appContext = application

        try {
            withHW().initSdk()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            withXM().initSdk()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            withWB()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getMetaData(key: String): String? {
        return appContext.packageManager?.getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA)?.metaData?.get(key)?.toString()
    }

    //检测是否安装
    fun isInstalled(packageName: String, intent: Intent): Boolean {
        val resInfo = Auth.appContext.packageManager.queryIntentActivities(intent, 0)
        if (resInfo.isNotEmpty()) {
            for (info in resInfo) {
                val activityInfo = info.activityInfo
                if (activityInfo.packageName.contains(packageName)) {
                    return true
                }
            }
        }
        return false
    }

    fun withMore(): AuthBuildForMore {
        return AuthBuildForMore
    }

    fun withGoogle(): AbsAuthBuildForGoogle {
        return try {
            val constructor = Class.forName("love.nuoyan.android.auth.google.AuthBuildForGoogle").getConstructor()
            constructor.newInstance() as AbsAuthBuildForGoogle
        } catch (e: Exception) {
            throw NullPointerException("添加谷歌依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }

    fun withHW(): AbsAuthBuildForHW {
        return try {
            val constructor = Class.forName("love.nuoyan.android.auth.hw.AuthBuildForHW").getConstructor()
            constructor.newInstance() as AbsAuthBuildForHW
        } catch (e: Exception) {
            throw IllegalAccessException("添加华为依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }
    fun withOPPO(): AbsAuthBuildForOPPO {
        return try {
            val constructor = Class.forName("love.nuoyan.android.auth.oppo.AuthBuildForOPPO").getConstructor()
            constructor.newInstance() as AbsAuthBuildForOPPO
        } catch (e: Exception) {
            throw IllegalAccessException("添加OPPO依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }
    fun withQQ(): AbsAuthBuildForQQ {
        return try {
            val constructor = Class.forName("love.nuoyan.android.auth.qq.AuthBuildForQQ").getConstructor()
            constructor.newInstance() as AbsAuthBuildForQQ
        } catch (e: Exception) {
            throw IllegalAccessException("添加QQ依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }
    fun withWB(): AbsAuthBuildForWB {
        return try {
            val constructor = Class.forName("love.nuoyan.android.auth.wb.AuthBuildForWB").getConstructor()
            constructor.newInstance() as AbsAuthBuildForWB
        } catch (e: Exception) {
            throw IllegalAccessException("添加微博依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }
    fun withWX(): AbsAuthBuildForWX {
        return try {
            val constructor = Class.forName("love.nuoyan.android.auth.wx.AuthBuildForWX").getConstructor()
            constructor.newInstance() as AbsAuthBuildForWX
        } catch (e: Exception) {
            throw IllegalAccessException("添加微信依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }
    fun withXM(): AbsAuthBuildForXM {
        return try {
            val constructor = Class.forName("love.nuoyan.android.auth.xm.AuthBuildForXM").getConstructor()
            constructor.newInstance() as AbsAuthBuildForXM
        } catch (e: Exception) {
            throw IllegalAccessException("添加小米依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }
    fun withYL(): AbsAuthBuildForYL {
        return try {
            val constructor = Class.forName("love.nuoyan.android.auth.yl.AuthBuildForYL").getConstructor()
            constructor.newInstance() as AbsAuthBuildForYL
        } catch (e: Exception) {
            throw IllegalAccessException("添加银联依赖: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }
    fun withZFB(): AbsAuthBuildForZFB {
        return try {
            val constructor = Class.forName("love.nuoyan.android.auth.zfb.AuthBuildForZFB").getConstructor()
            constructor.newInstance() as AbsAuthBuildForZFB
        } catch (e: Exception) {
            throw IllegalAccessException("添加支付宝依赖, 并配置: $e ${(e as? InvocationTargetException)?.targetException}")
        }
    }
}