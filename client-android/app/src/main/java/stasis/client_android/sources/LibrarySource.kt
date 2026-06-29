package stasis.client_android.sources

interface LibrarySource<R : Any> {
    val scheme: String

    val recordClass: Class<R>

    fun hasReadPermission(): Boolean

    fun hasWritePermission(): Boolean

    fun list(): List<R>

    fun restore(record: R)

    fun id(record: R): String

    fun displayName(record: R): String

    fun attributes(record: R): Map<String, String>

    fun describe(record: R): EntityPreview

    fun export(record: R): ExportedContent
}
