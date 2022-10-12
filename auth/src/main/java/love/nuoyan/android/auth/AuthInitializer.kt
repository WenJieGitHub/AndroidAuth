package love.nuoyan.android.auth

import android.content.Context
import androidx.startup.Initializer

class AuthInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Auth.appContext = context
    }
    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}