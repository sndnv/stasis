package stasis.client_android.persistence.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import stasis.client_android.lib.model.server.devices.DeviceBootstrapParameters
import javax.inject.Inject

class ConfigViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    private val repo: ConfigRepository = ConfigRepository(application)

    val available: Boolean
        get() = repo.available

    fun bootstrap(params: DeviceBootstrapParameters) = repo.bootstrap(params)

    fun reset() = repo.reset()
}
