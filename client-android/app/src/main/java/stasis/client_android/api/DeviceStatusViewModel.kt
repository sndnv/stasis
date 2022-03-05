package stasis.client_android.api

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import stasis.client_android.activities.helpers.Common.getOrRenderFailure
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.utils.Cache
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import javax.inject.Inject

class DeviceStatusViewModel @Inject constructor(
    application: Application,
    providerContextFactory: ProviderContext.Factory,
    private val deviceCache: Cache.Refreshing<Int, Device>
) : AndroidViewModel(application) {
    private val preferences: SharedPreferences =
        ConfigRepository.getPreferences(application.applicationContext)

    private val providerContext = providerContextFactory.getOrCreate(preferences).required()
    private val deviceData: MutableLiveData<Device> = MutableLiveData()

    private val deviceRefreshListener: (Int, Device?) -> Unit =
        { _, device -> device?.let { deviceData.postValue(it) } }

    val device: LiveData<Device> = deviceData

    init {
        deviceCache.registerOnEntryRefreshedListener(deviceRefreshListener)

        viewModelScope.launch {
            val value: Device? = deviceCache.getOrLoad(
                key = 0,
                load = { providerContext.api.device().getOrRenderFailure(withContext = application) }
            )

            value?.let { deviceData.postValue(it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        deviceCache.unregisterOnEntryRefreshedListener(deviceRefreshListener)
    }
}
