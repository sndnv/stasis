package stasis.test.client_android.sources.contacts

import android.provider.ContactsContract
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.sources.contacts.ContactDataRow
import stasis.client_android.sources.contacts.ContactRecord
import stasis.client_android.sources.contacts.ContactVCard

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class ContactVCardSpec {
    @Test
    fun renderAContactAsVCard() {
        val record = ContactRecord(
            id = "6df54e0c-ced7-4c69-ad03-ff3ad9fea7e7",
            displayName = "Test A",
            starred = true,
            data = listOf(
                row(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, 0 to "Test A", 1 to "Test", 2 to "A"),
                row(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, 0 to "+15550100"),
                row(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, 0 to "test@example.com"),
                row(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE, 0 to "Test Co", 3 to "Engineer"),
                row(
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                    3 to "1 Test St", 6 to "Testville", 7 to "TS", 8 to "00000", 9 to "Testland"
                ),
                row(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE, 0 to "https://example.com"),
                row(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, 0 to "test note"),
                row(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, 0 to "T")
            )
        )

        val text = unfold(ContactVCard.toVCard(record))

        assertThat(text, containsString("BEGIN:VCARD"))
        assertThat(text, containsString("VERSION:3.0"))
        assertThat(text, containsString("FN:Test A"))
        assertThat(text, containsString("N:A;Test"))
        assertThat(text, containsString("+15550100"))
        assertThat(text, containsString("test@example.com"))
        assertThat(text, containsString("ORG:Test Co"))
        assertThat(text, containsString("TITLE:Engineer"))
        assertThat(text, containsString("1 Test St"))
        assertThat(text, containsString("example.com"))
        assertThat(text, containsString("NOTE:test note"))
        assertThat(text, containsString("NICKNAME:T"))
        assertThat(text, containsString("END:VCARD"))
    }

    @Test
    fun fallBackToAPlaceholderNameWhenBlank() {
        val record = ContactRecord(id = "no-name", displayName = "", starred = false, data = emptyList())

        val text = unfold(ContactVCard.toVCard(record))

        assertThat(text, containsString("FN:Unknown"))
    }

    private fun row(mimeType: String, vararg values: Pair<Int, String>): ContactDataRow {
        val byIndex = values.toMap()
        return ContactDataRow(mimeType = mimeType, values = (0 until 15).map { byIndex[it] })
    }

    private fun unfold(bytes: ByteArray): String = bytes.decodeToString().replace("\r\n ", "").replace("\n ", "")
}
