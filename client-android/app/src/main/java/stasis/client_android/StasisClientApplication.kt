package stasis.client_android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StasisClientApplication : Application() {
    val component: ServiceComponent = DaggerServiceComponent.create()
}
