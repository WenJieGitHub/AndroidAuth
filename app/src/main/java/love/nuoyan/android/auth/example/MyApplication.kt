package love.nuoyan.android.auth.example

import android.app.Application
import love.nuoyan.android.auth.Auth

class MyApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        Auth.init(this)
    }
}