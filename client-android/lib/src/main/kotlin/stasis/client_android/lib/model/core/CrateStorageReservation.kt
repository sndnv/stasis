package stasis.client_android.lib.model.core

import java.util.UUID

data class CrateStorageReservation(
    val id: CrateStorageReservationId,
    val crate: CrateId,
    val size: Long,
    val copies: Int,
    val origin: NodeId,
    val target: NodeId
)

typealias CrateStorageReservationId = UUID
