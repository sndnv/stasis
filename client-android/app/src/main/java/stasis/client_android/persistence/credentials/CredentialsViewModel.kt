package stasis.client_android.persistence.credentials

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import okio.ByteString
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.utils.Try
import stasis.client_android.providers.ProviderContext
import javax.inject.Inject

class CredentialsViewModel @Inject constructor(
    contextFactory: ProviderContext.Factory,
    application: Application
) : AndroidViewModel(application) {
    private val repo: CredentialsRepository = CredentialsRepository(
        contextFactory = contextFactory,
        context = application
    )

    val user: LiveData<Try<AccessTokenResponse.Claims>> = repo.user
    val device: LiveData<Try<AccessTokenResponse.Claims>> = repo.device

    val available: Boolean
        get() = repo.available

    fun login(username: String, password: String, f: (Try<Unit>) -> Unit) =
        repo.login(username, password, f)

    fun logout(f: () -> Unit): Unit =
        repo.logout(f)

    fun updateDeviceSecret(password: String, secret: ByteString, f: (Try<Unit>) -> Unit) =
        repo.updateDeviceSecret(password, secret, f)
}
