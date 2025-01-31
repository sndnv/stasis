package stasis.client_android.api

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import stasis.client_android.activities.helpers.Common.getOrRenderFailure
import stasis.client_android.lib.model.server.users.User
import stasis.client_android.lib.utils.Cache
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import javax.inject.Inject

class UserStatusViewModel @Inject constructor(
    application: Application,
    providerContextFactory: ProviderContext.Factory,
    private val userCache: Cache.Refreshing<Int, User>
) : AndroidViewModel(application) {
    private val preferences: SharedPreferences =
        ConfigRepository.getPreferences(application.applicationContext)
    private val providerContext = providerContextFactory.getOrCreate(preferences).required()
    private val userData: MutableLiveData<User> = MutableLiveData()

    private val userRefreshListener: (Int, User?) -> Unit =
        { _, user -> user?.let { userData.postValue(it) } }

    val user: LiveData<User> = userData

    init {
        userCache.registerOnEntryRefreshedListener(userRefreshListener)

        viewModelScope.launch {
            val value: User? = userCache.getOrLoad(
                key = 0,
                load = {
                    providerContext.api.user().getOrRenderFailure(withContext = application)
                        ?: throw RuntimeException("No user found") // retrieval failed; trigger faster cache refresh
                }
            )

            value?.let { userData.postValue(it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        userCache.unregisterOnEntryRefreshedListener(userRefreshListener)
    }
}
