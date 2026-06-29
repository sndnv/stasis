package stasis.client_android.sources.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import stasis.client_android.sources.Cursors.collect
import stasis.client_android.sources.Cursors.queryFirst
import stasis.client_android.sources.Cursors.queryList
import stasis.client_android.sources.EntityPreview
import stasis.client_android.sources.ExportedContent
import stasis.client_android.sources.LibrarySource
import java.util.Base64

class ContactsSource(
    private val resolver: ContentResolver,
    private val hasReadPermission: () -> Boolean,
    private val hasWritePermission: () -> Boolean
) : LibrarySource<ContactRecord> {
    override val scheme: String = Scheme

    override val recordClass: Class<ContactRecord> = ContactRecord::class.java

    override fun hasReadPermission(): Boolean = hasReadPermission.invoke()

    override fun hasWritePermission(): Boolean = hasWritePermission.invoke()

    override fun list(): List<ContactRecord> =
        resolver.queryList(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.STARRED
            ),
            null,
            null,
            null
        ) { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val lookupIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val starredIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)

            cursor.collect { row ->
                val contactId = row.getLong(idIndex).toString()

                ContactRecord(
                    id = stableId(contactId, row.getStringOrNull(lookupIndex).orEmpty()),
                    displayName = row.getStringOrNull(nameIndex).orEmpty(),
                    starred = (row.getIntOrNull(starredIndex) ?: 0) != 0,
                    data = dataRows(contactId)
                )
            }
        }

    override fun restore(record: ContactRecord) {
        val rawContactId = findRawContact(record)
        val operations = if (rawContactId != null) updateOperations(rawContactId, record) else insertOperations(record)
        resolver.applyBatch(ContactsContract.AUTHORITY, operations)
    }

    override fun id(record: ContactRecord): String = record.id

    override fun displayName(record: ContactRecord): String = record.displayName

    override fun attributes(record: ContactRecord): Map<String, String> = mapOf("name" to record.displayName)

    override fun describe(record: ContactRecord): EntityPreview {
        val contact = buildList {
            add(EntityPreview.Field("Name", record.displayName.ifBlank { "(no name)" }))
            if (record.starred) add(EntityPreview.Field("Starred", "Yes"))
        }

        val details = record.data
            .filterNot { it.mimeType == ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE }
            .filterNot { it.mimeType == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE }
            .mapNotNull { row ->
                row.values.firstOrNull { !it.isNullOrBlank() }?.let { value ->
                    EntityPreview.Field(friendlyMimeType(row.mimeType), value)
                }
            }

        return EntityPreview(
            sections = listOf(
                EntityPreview.Section("Contact", contact),
                EntityPreview.Section("Details", details)
            ).filter { it.fields.isNotEmpty() }
        )
    }

    override fun export(record: ContactRecord): ExportedContent =
        ExportedContent(extension = "vcf", mimeType = "text/vcard", bytes = ContactVCard.toVCard(record))

    private fun friendlyMimeType(mimeType: String): String = when (mimeType) {
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> "Phone"
        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> "Email"
        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> "Address"
        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> "Organization"
        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> "Website"
        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> "Note"
        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> "Nickname"
        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> "Event"
        ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE -> "Relation"
        else -> mimeType.substringAfterLast('/')
    }

    private fun stableId(contactId: String, lookupKey: String): String {
        val sourceId = resolver.queryFirst(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.SOURCE_ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND $LocalAccountSelection",
            arrayOf(contactId),
            null
        ) { it.getStringOrNull(it.getColumnIndexOrThrow(ContactsContract.RawContacts.SOURCE_ID)) }

        return sourceId?.takeIf { it.isNotBlank() } ?: lookupKey
    }

    private fun dataRows(contactId: String): List<ContactDataRow> =
        queryDataRows("${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(contactId))

    private fun queryDataRows(selection: String, args: Array<String>): List<ContactDataRow> =
        resolver.queryList(
            ContactsContract.Data.CONTENT_URI,
            DataProjection,
            selection,
            args,
            "${ContactsContract.Data.MIMETYPE} ASC, ${ContactsContract.Data.DATA1} ASC"
        ) { cursor ->
            val mimeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val dataIndexes = DataColumns.map { cursor.getColumnIndexOrThrow(it) }

            cursor.collect { row ->
                val mimeType = row.getStringOrNull(mimeIndex).orEmpty()
                val values = DataColumns.indices.map { index ->
                    val columnIndex = dataIndexes[index]
                    val column = DataColumns[index]
                    when {
                        isPhotoBlob(mimeType, column) ->
                            row.getBlob(columnIndex)?.let { Base64.getEncoder().encodeToString(it) }
                        isPhotoFileId(mimeType, column) -> null
                        else -> row.getStringOrNull(columnIndex)
                    }
                }

                ContactDataRow(mimeType = mimeType, values = values)
            }
        }

    private fun findRawContact(record: ContactRecord): Long? = rawContactBySourceId(record.id)

    private fun rawContactBySourceId(sourceId: String): Long? =
        resolver.queryFirst(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "$LocalAccountSelection AND ${ContactsContract.RawContacts.SOURCE_ID} = ?",
            arrayOf(sourceId),
            null
        ) { it.getLong(it.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)) }

    private fun insertOperations(record: ContactRecord): ArrayList<ContentProviderOperation> {
        val operations = ArrayList<ContentProviderOperation>()

        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.SOURCE_ID, record.id)
                .withValue(ContactsContract.RawContacts.STARRED, if (record.starred) 1 else 0)
                .build()
        )

        record.data.forEach { row ->
            val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            columnValues(row).forEach { (column, value) -> builder.withValue(column, value) }
            operations.add(builder.build())
        }

        return operations
    }

    private fun updateOperations(rawContactId: Long, record: ContactRecord): ArrayList<ContentProviderOperation> {
        val operations = ArrayList<ContentProviderOperation>()

        operations.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ?", arrayOf(rawContactId.toString()))
                .build()
        )

        record.data.forEach { row ->
            val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            columnValues(row).forEach { (column, value) -> builder.withValue(column, value) }
            operations.add(builder.build())
        }

        operations.add(
            ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
                .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString()))
                .withValue(ContactsContract.RawContacts.STARRED, if (record.starred) 1 else 0)
                .withValue(ContactsContract.RawContacts.SOURCE_ID, record.id)
                .build()
        )

        return operations
    }

    private fun columnValues(row: ContactDataRow): List<Pair<String, Any>> {
        val values = mutableListOf<Pair<String, Any>>(ContactsContract.Data.MIMETYPE to row.mimeType)

        row.values.forEachIndexed { index, value ->
            val column = DataColumns[index]
            if (value != null && !isPhotoFileId(row.mimeType, column)) {
                values.add(column to if (isPhotoBlob(row.mimeType, column)) Base64.getDecoder().decode(value) else value)
            }
        }

        return values
    }

    private fun isPhotoBlob(mimeType: String, column: String): Boolean =
        mimeType == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE && column == ContactsContract.Data.DATA15

    private fun isPhotoFileId(mimeType: String, column: String): Boolean =
        mimeType == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE && column == ContactsContract.Data.DATA14

    companion object {
        const val Scheme: String = "contacts"

        private val LocalAccountSelection: String =
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL AND ${ContactsContract.RawContacts.ACCOUNT_NAME} IS NULL"

        private val DataColumns: Array<String> = arrayOf(
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA5,
            ContactsContract.Data.DATA6,
            ContactsContract.Data.DATA7,
            ContactsContract.Data.DATA8,
            ContactsContract.Data.DATA9,
            ContactsContract.Data.DATA10,
            ContactsContract.Data.DATA11,
            ContactsContract.Data.DATA12,
            ContactsContract.Data.DATA13,
            ContactsContract.Data.DATA14,
            ContactsContract.Data.DATA15
        )

        private val DataProjection: Array<String> = arrayOf(ContactsContract.Data.MIMETYPE) + DataColumns
    }
}
