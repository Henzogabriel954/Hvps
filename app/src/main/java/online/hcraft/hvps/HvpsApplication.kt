package online.hcraft.hvps

import android.app.Application
import online.hcraft.hvps.utils.TokenManager

class HvpsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
    }
}
