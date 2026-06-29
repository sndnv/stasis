package stasis.test.client_android.sources.contacts

import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentResolver
import android.database.MatrixCursor
import android.provider.ContactsContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.sources.contacts.ContactDataRow
import stasis.client_android.sources.contacts.ContactRecord
import stasis.client_android.sources.contacts.ContactsSource
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class ContactsSourceSpec {
    private fun sourceWith(resolver: ContentResolver): ContactsSource =
        ContactsSource(resolver = resolver, hasReadPermission = { true }, hasWritePermission = { true })

    @Test
    fun listContactsWithDataRows() {
        val resolver = mockk<ContentResolver>()
        every { resolver.query(ContactsContract.Contacts.CONTENT_URI, any(), any(), any(), any()) } returns contactsCursor()
        every {
            resolver.query(ContactsContract.RawContacts.CONTENT_URI, any(), StableIdSelection, any(), any())
        } answers { sourceIdCursor("src-1") }
        every {
            resolver.query(ContactsContract.Data.CONTENT_URI, any(), ByContactSelection, any(), any())
        } answers { dataCursor() }

        val contacts = sourceWith(resolver).list()

        assertThat(contacts.size, equalTo(1))
        val contact = contacts.single()
        assertThat(contact.id, equalTo("src-1"))
        assertThat(contact.displayName, equalTo("test"))
        assertThat(contact.starred, equalTo(false))
        assertThat(contact.data.size, equalTo(1))
        assertThat(contact.data.single().values.first(), equalTo("test"))
    }

    @Test
    fun listExcludesTheVolatilePhotoFileId() {
        val resolver = mockk<ContentResolver>()
        every { resolver.query(ContactsContract.Contacts.CONTENT_URI, any(), any(), any(), any()) } returns contactsCursor()
        every {
            resolver.query(ContactsContract.RawContacts.CONTENT_URI, any(), StableIdSelection, any(), any())
        } answers { sourceIdCursor("src-1") }
        every {
            resolver.query(ContactsContract.Data.CONTENT_URI, any(), ByContactSelection, any(), any())
        } answers { photoDataCursor() }

        val row = sourceWith(resolver).list().single().data.single()

        assertThat(row.values[PhotoFileIdIndex], nullValue())
        assertThat(row.values[PhotoBlobIndex], equalTo(Base64.getEncoder().encodeToString(PhotoBytes)))
    }

    @Test
    fun restoreInsertsADeviceLocalRawContact() {
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(ContactsContract.RawContacts.CONTENT_URI, any(), BySourceIdSelection, any(), any())
        } returns emptyRawIdCursor()

        val operations = slot<ArrayList<ContentProviderOperation>>()
        every { resolver.applyBatch(ContactsContract.AUTHORITY, capture(operations)) } returns arrayOf()

        sourceWith(resolver).restore(record())

        val rawContactInsert = operations.captured.first()
        assertThat(rawContactInsert.uri, equalTo(ContactsContract.RawContacts.CONTENT_URI))

        val values = requireNotNull(rawContactInsert.resolveValueBackReferences(emptyArray<ContentProviderResult>(), 0))
        assertThat(values.getAsString(ContactsContract.RawContacts.ACCOUNT_TYPE), nullValue())
        assertThat(values.getAsString(ContactsContract.RawContacts.ACCOUNT_NAME), nullValue())
        assertThat(values.getAsString(ContactsContract.RawContacts.SOURCE_ID), equalTo("src-1"))
    }

    @Test
    fun restoreUpdatesAnExistingContactBySourceIdWithoutDuplicating() {
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(ContactsContract.RawContacts.CONTENT_URI, any(), BySourceIdSelection, any(), any())
        } returns rawIdCursor(20L)

        val operations = slot<ArrayList<ContentProviderOperation>>()
        every { resolver.applyBatch(ContactsContract.AUTHORITY, capture(operations)) } returns arrayOf()

        sourceWith(resolver).restore(record())

        verify(exactly = 1) { resolver.applyBatch(ContactsContract.AUTHORITY, any()) }
        assertThat(operations.captured.size, equalTo(3))
        assertThat(operations.captured.first().uri, equalTo(ContactsContract.Data.CONTENT_URI))
        assertThat(operations.captured.last().uri, equalTo(ContactsContract.RawContacts.CONTENT_URI))
    }

    @Test
    fun restoreInsertsWhenNoSourceIdMatchesAndNeverContentMatches() {
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(ContactsContract.RawContacts.CONTENT_URI, any(), BySourceIdSelection, any(), any())
        } returns emptyRawIdCursor()

        val operations = slot<ArrayList<ContentProviderOperation>>()
        every { resolver.applyBatch(ContactsContract.AUTHORITY, capture(operations)) } returns arrayOf()

        sourceWith(resolver).restore(record())

        verify(exactly = 0) {
            resolver.query(ContactsContract.RawContacts.CONTENT_URI, any(), ByDisplayNameSelection, any(), any())
        }
        assertThat(operations.captured.size, equalTo(2))
        assertThat(operations.captured.first().uri, equalTo(ContactsContract.RawContacts.CONTENT_URI))
    }

    private fun contactsCursor(): MatrixCursor =
        MatrixCursor(
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.STARRED
            )
        ).apply {
            addRow(arrayOf<Any?>(1L, "lookup-1", "test", 0))
        }

    private fun sourceIdCursor(sourceId: String): MatrixCursor =
        MatrixCursor(arrayOf(ContactsContract.RawContacts.SOURCE_ID)).apply { addRow(arrayOf<Any?>(sourceId)) }

    private fun rawIdCursor(id: Long): MatrixCursor =
        MatrixCursor(arrayOf(ContactsContract.RawContacts._ID)).apply { addRow(arrayOf<Any?>(id)) }

    private fun emptyRawIdCursor(): MatrixCursor =
        MatrixCursor(arrayOf(ContactsContract.RawContacts._ID))

    private fun dataCursor(): MatrixCursor =
        MatrixCursor(DataColumns).apply {
            val values = arrayOfNulls<Any?>(DataColumns.size)
            values[0] = ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            values[1] = "test"
            addRow(values)
        }

    private fun photoDataCursor(): MatrixCursor =
        MatrixCursor(DataColumns).apply {
            val values = arrayOfNulls<Any?>(DataColumns.size)
            values[0] = ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
            values[Data14Column] = "999"
            values[Data15Column] = PhotoBytes
            addRow(values)
        }

    private fun record(): ContactRecord =
        ContactRecord(
            id = "src-1",
            displayName = "test",
            starred = false,
            data = listOf(
                ContactDataRow(
                    mimeType = ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                    values = listOf("test") + List(14) { null }
                )
            )
        )

    companion object {
        private val StableIdSelection =
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND " +
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL AND ${ContactsContract.RawContacts.ACCOUNT_NAME} IS NULL"

        private val BySourceIdSelection =
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL AND ${ContactsContract.RawContacts.ACCOUNT_NAME} IS NULL AND " +
                "${ContactsContract.RawContacts.SOURCE_ID} = ?"

        private val ByDisplayNameSelection =
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND ${ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY} = ?"

        private val ByContactSelection = "${ContactsContract.Data.CONTACT_ID} = ?"

        private val PhotoBytes: ByteArray = byteArrayOf(1, 2, 3)

        private const val PhotoFileIdIndex = 13
        private const val PhotoBlobIndex = 14
        private const val Data14Column = 14
        private const val Data15Column = 15

        private val DataColumns: Array<String> = arrayOf(
            ContactsContract.Data.MIMETYPE,
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
    }
}
