package stasis.client_android.activities.receivers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object LogoutReceiver {
    private val underlying: MutableLiveData<Unit> = MutableLiveData()

    val requests: LiveData<Unit>
        get() = underlying

    fun logout() {
        underlying.value = Unit
    }
}
