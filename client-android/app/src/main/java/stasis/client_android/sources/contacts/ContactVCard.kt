package stasis.client_android.sources.contacts

import android.provider.ContactsContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.vcard.Entity
import net.fortuna.ical4j.vcard.EntityList
import net.fortuna.ical4j.vcard.VCard
import net.fortuna.ical4j.vcard.VCardOutputter
import net.fortuna.ical4j.vcard.property.Address
import net.fortuna.ical4j.vcard.property.Email
import net.fortuna.ical4j.vcard.property.Fn
import net.fortuna.ical4j.vcard.property.N
import net.fortuna.ical4j.vcard.property.Nickname
import net.fortuna.ical4j.vcard.property.Note
import net.fortuna.ical4j.vcard.property.Org
import net.fortuna.ical4j.vcard.property.Telephone
import net.fortuna.ical4j.vcard.property.Title
import net.fortuna.ical4j.vcard.property.Url
import net.fortuna.ical4j.vcard.property.Version
import java.io.ByteArrayOutputStream
import java.net.URI

object ContactVCard {
    fun toVCard(record: ContactRecord): ByteArray {
        val properties = mutableListOf<Property>(
            Version(VCardVersion),
            Fn(record.displayName.ifBlank { UnknownName })
        )

        record.data.forEach { row ->
            when (row.mimeType) {
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> name(row)
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                    row.value(0)?.let { Telephone(it) }

                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                    row.value(0)?.let { Email(it) }

                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> address(row)
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE ->
                    row.value(0)?.let { Org(it) }

                ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> website(row)
                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE ->
                    row.value(0)?.let { Note(it) }

                ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE ->
                    row.value(0)?.let { Nickname(it) }

                else -> null
            }?.let { properties.add(it) }

            if (row.mimeType == ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE) {
                row.value(3)?.let { properties.add(Title(it)) }
            }
        }

        val card = VCard(EntityList(listOf(Entity(PropertyList(properties)))))

        return ByteArrayOutputStream().use { out ->
            VCardOutputter(false).output(card, out)
            out.toByteArray()
        }
    }

    private fun name(row: ContactDataRow): N? {
        val given = row.value(1)
        val family = row.value(2)
        val prefix = row.value(3)
        val middle = row.value(4)
        val suffix = row.value(5)

        if (listOfNotNull(given, family, prefix, middle, suffix).isEmpty()) return null

        return N(
            family.orEmpty(),
            given.orEmpty(),
            listOfNotNull(middle).toTypedArray(),
            listOfNotNull(prefix).toTypedArray(),
            listOfNotNull(suffix).toTypedArray()
        )
    }

    private fun address(row: ContactDataRow): Address? {
        val street = row.value(3)
        val poBox = row.value(4)
        val extended = row.value(5)
        val city = row.value(6)
        val region = row.value(7)
        val postcode = row.value(8)
        val country = row.value(9)

        if (listOfNotNull(street, poBox, extended, city, region, postcode, country).isEmpty()) return null

        return Address(
            poBox.orEmpty(),
            extended.orEmpty(),
            street.orEmpty(),
            city.orEmpty(),
            region.orEmpty(),
            postcode.orEmpty(),
            country.orEmpty()
        )
    }

    private fun website(row: ContactDataRow): Url? =
        row.value(0)?.let { runCatching { Url(URI(it)) }.getOrNull() }

    private fun ContactDataRow.value(index: Int): String? =
        values.getOrNull(index)?.takeIf { it.isNotBlank() }

    private const val UnknownName: String = "Unknown"

    private const val VCardVersion: String = "3.0"
}
