package stasis.client_android.lib.model.core

import java.util.UUID

data class CrateStorageRequest(
    val id: CrateStorageRequestId,
    val crate: CrateId,
    val size: Long,
    val copies: Int,
    val origin: NodeId,
    val source: NodeId
) {
    companion object {
        operator fun invoke(manifest: Manifest): CrateStorageRequest =
            CrateStorageRequest(
                id = UUID.randomUUID(),
                crate = manifest.crate,
                size = manifest.size,
                copies = manifest.copies,
                origin = manifest.origin,
                source = manifest.source
            )
    }
}

typealias CrateStorageRequestId = UUID
