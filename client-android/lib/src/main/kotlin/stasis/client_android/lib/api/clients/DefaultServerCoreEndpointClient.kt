package stasis.client_android.lib.api.clients

import okio.Source
import stasis.client_android.lib.api.clients.exceptions.EndpointFailure
import stasis.client_android.lib.api.clients.internal.ClientExtensions
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.core.CrateStorageRequest
import stasis.client_android.lib.model.core.CrateStorageReservation
import stasis.client_android.lib.model.core.Manifest
import stasis.client_android.lib.model.core.NodeId
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.utils.AsyncOps

class DefaultServerCoreEndpointClient(
    serverCoreUrl: String,
    override val credentials: suspend () -> HttpCredentials,
    override val self: NodeId
) : ServerCoreEndpointClient, ClientExtensions() {
    override val server: String = serverCoreUrl.trimEnd { it == '/' }

    override val retryConfig: AsyncOps.RetryConfig = AsyncOps.RetryConfig.Default

    override suspend fun push(manifest: Manifest, content: Source) {
        val reservation = reserveStorage(manifest)
        return pushCrate(manifest, content, reservation)
    }

    override suspend fun pull(crate: CrateId): Source? {
        return pullCrate(crate)
    }

    private suspend fun reserveStorage(
        manifest: Manifest,
    ): CrateStorageReservation {
        val storageRequest = CrateStorageRequest(manifest)

        val response = request { builder ->
            builder
                .url("$server/reservations")
                .put(storageRequest.toBody())
        }

        return when (val code = response.code) {
            StatusOk -> response.toRequiredModel()
            StatusInsufficientStorage -> throw EndpointFailure(
                "Endpoint [$server] was unable to reserve enough storage for request [$storageRequest]"
            )
            else -> throw EndpointFailure(
                "Endpoint [$server] responded to storage request with unexpected status: [$code]"
            )
        }
    }

    private suspend fun pushCrate(
        manifest: Manifest,
        content: Source,
        reservation: CrateStorageReservation
    ) {
        val response = request { builder ->
            builder
                .url("$server/crates/${manifest.crate}?reservation=${reservation.id}")
                .put(content.toBody())
        }

        response.body?.close()

        when (val code = response.code) {
            StatusOk -> return
            else -> throw EndpointFailure(
                "Endpoint [$server] responded to push for crate [${manifest.crate}] with unexpected status: [$code]"
            )
        }
    }

    private suspend fun pullCrate(
        crate: CrateId
    ): Source? {
        val response = request { builder ->
            builder
                .url("$server/crates/$crate")
                .get()
        }

        return when (val code = response.code) {
            StatusOk -> response.body?.source()
            StatusNotFound -> null
            else -> throw EndpointFailure(
                "Endpoint [$server] responded to pull for crate [$crate] with unexpected status: [$code]"
            )
        }
    }

    companion object {
        const val StatusOk: Int = 200
        const val StatusNotFound: Int = 404
        const val StatusInsufficientStorage: Int = 507
    }
}
