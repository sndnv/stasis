package stasis.client_android.lib.model.core

data class Manifest(
    val crate: CrateId,
    val size: Long,
    val copies: Int,
    val origin: NodeId,
    val source: NodeId,
    val destinations: List<NodeId>
) {
    companion object {
        operator fun invoke(
            crate: CrateId,
            size: Long,
            copies: Int,
            origin: NodeId,
            source: NodeId
        ): Manifest = Manifest(
            crate = crate,
            size = size,
            copies = copies,
            origin = origin,
            source = source,
            destinations = emptyList()
        )
    }
}
