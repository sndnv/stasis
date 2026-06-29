package stasis.test.client_android.sources

import stasis.client_android.sources.EntityPreview
import stasis.client_android.sources.ExportedContent
import stasis.client_android.sources.LibrarySource

class FakeLibrarySource(
    private val records: List<FakeRecord>,
    private val readPermission: Boolean,
    private val writePermission: Boolean
) : LibrarySource<FakeRecord> {
    val restored: MutableList<FakeRecord> = mutableListOf()

    override val scheme: String = "fake"

    override val recordClass: Class<FakeRecord> = FakeRecord::class.java

    override fun hasReadPermission(): Boolean = readPermission

    override fun hasWritePermission(): Boolean = writePermission

    override fun list(): List<FakeRecord> = records

    override fun restore(record: FakeRecord) {
        restored.add(record)
    }

    override fun id(record: FakeRecord): String = record.key

    override fun displayName(record: FakeRecord): String = record.label

    override fun attributes(record: FakeRecord): Map<String, String> = mapOf("name" to record.label)

    override fun describe(record: FakeRecord): EntityPreview =
        EntityPreview(
            sections = listOf(
                EntityPreview.Section(
                    title = "Record",
                    fields = listOf(
                        EntityPreview.Field("Label", record.label),
                        EntityPreview.Field("Payload", record.payload)
                    )
                )
            )
        )

    override fun export(record: FakeRecord): ExportedContent =
        ExportedContent(
            extension = "txt",
            mimeType = "text/plain",
            bytes = "${record.label}:${record.payload}".encodeToByteArray()
        )
}
