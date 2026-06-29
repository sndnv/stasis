package stasis.client_android.api.clients

import android.provider.ContactsContract
import com.google.gson.Gson
import stasis.client_android.sources.calendar.CalendarAttendee
import stasis.client_android.sources.calendar.CalendarEvent
import stasis.client_android.sources.calendar.CalendarReminder
import stasis.client_android.sources.contacts.ContactDataRow
import stasis.client_android.sources.contacts.ContactRecord
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.UUID

object MockDatasetContent {
    enum class Kind { File, Calendar, Contact }

    data class Sample(
        val key: String,
        val crate: UUID,
        val size: Long,
        val checksum: BigInteger,
        val compression: String,
        val created: Instant,
        val updated: Instant,
        val kind: Kind,
        val attributes: Map<String, String>,
        val content: ByteArray
    ) {
        val cratePath: String get() = "${key}_0"
    }

    private val gson: Gson = Gson()

    private val calendarEvent: CalendarEvent = CalendarEvent(
        id = "uid-e6c12064-eec2-4981-af21-184b989ef029",
        calendar = "test a",
        title = "test",
        description = "test a",
        location = "test",
        start = Instant.parse("2026-06-30T09:00:00Z").toEpochMilli(),
        end = Instant.parse("2026-06-30T10:00:00Z").toEpochMilli(),
        duration = null,
        allDay = false,
        timezone = "UTC",
        endTimezone = null,
        rrule = "FREQ=WEEKLY;BYDAY=MO",
        rdate = null,
        exdate = null,
        organizer = "test@test",
        availability = 0,
        accessLevel = 0,
        status = 1,
        hasAlarm = true,
        reminders = listOf(CalendarReminder(minutes = 10, method = 1)),
        attendees = listOf(CalendarAttendee(name = "test a", email = "test@test", relationship = 1, type = 1, status = 1))
    )

    private val contactRecord: ContactRecord = ContactRecord(
        id = "source-2fc8258a-8d37-4224-abea-18b0bc7fb780",
        displayName = "test a",
        starred = true,
        data = listOf(
            dataRow(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "555 0100"),
            dataRow(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, "test@test")
        )
    )

    val samples: List<Sample> = listOf(
        Sample(
            key = "/tmp/file/test.txt",
            crate = UUID.fromString("0a1b2c3d-4e5f-4061-8273-8495a6b7c8d9"),
            size = 0,
            checksum = BigInteger("106"),
            compression = "none",
            created = Instant.parse("2026-06-30T08:00:00Z"),
            updated = Instant.parse("2026-06-30T08:00:00Z"),
            kind = Kind.File,
            attributes = emptyMap(),
            content = "test\ntest a\ntest\n".encodeToByteArray()
        ).let { it.copy(size = it.content.size.toLong()) },
        Sample(
            key = "/tmp/photos/test.bmp",
            crate = UUID.fromString("b1e7c0de-4a2f-4d8a-9c6b-2e0f5a7c1d34"),
            size = 0,
            checksum = BigInteger("101"),
            compression = "none",
            created = Instant.parse("2026-06-30T08:30:00Z"),
            updated = Instant.parse("2026-06-30T08:30:00Z"),
            kind = Kind.File,
            attributes = emptyMap(),
            content = sampleImage(width = 96, height = 96)
        ).let { it.copy(size = it.content.size.toLong()) },
        Sample(
            key = "calendar:/uid-e6c12064-eec2-4981-af21-184b989ef029",
            crate = UUID.fromString("aa65422e-e064-4f4b-92ee-1b8dc1dab939"),
            size = 0,
            checksum = BigInteger("102"),
            compression = "none",
            created = Instant.parse("2026-06-30T09:15:00Z"),
            updated = Instant.parse("2026-06-30T09:15:00Z"),
            kind = Kind.Calendar,
            attributes = mapOf("name" to calendarEvent.title, "calendar" to calendarEvent.calendar),
            content = gson.toJson(calendarEvent).encodeToByteArray()
        ).let { it.copy(size = it.content.size.toLong()) },
        Sample(
            key = "contacts:/source-2fc8258a-8d37-4224-abea-18b0bc7fb780",
            crate = UUID.fromString("f9815d81-bb5e-40c2-812c-3efa4da7d4c3"),
            size = 0,
            checksum = BigInteger("103"),
            compression = "none",
            created = Instant.parse("2026-06-30T11:42:00Z"),
            updated = Instant.parse("2026-06-30T11:42:00Z"),
            kind = Kind.Contact,
            attributes = mapOf("name" to contactRecord.displayName),
            content = gson.toJson(contactRecord).encodeToByteArray()
        ).let { it.copy(size = it.content.size.toLong()) },
        Sample(
            key = "/tmp/photos/test.mp4",
            crate = UUID.fromString("c4d5e6f7-1122-4a3b-8c9d-0e1f2a3b4c5d"),
            size = 25L * 1024 * 1024,
            checksum = BigInteger("104"),
            compression = "none",
            created = Instant.parse("2026-06-29T18:05:00Z"),
            updated = Instant.parse("2026-06-29T18:05:00Z"),
            kind = Kind.File,
            attributes = emptyMap(),
            content = ByteArray(0)
        ),
        Sample(
            key = "/tmp/misc/test.bin",
            crate = UUID.fromString("d7e8f9a0-3344-4b5c-9d0e-1f2a3b4c5d6e"),
            size = 48,
            checksum = BigInteger("105"),
            compression = "none",
            created = Instant.parse("2026-06-28T07:20:00Z"),
            updated = Instant.parse("2026-06-28T07:20:00Z"),
            kind = Kind.File,
            attributes = emptyMap(),
            content = ByteArray(48) { 0xFF.toByte() }
        )
    )

    val byCrate: Map<UUID, Sample> = samples.associateBy { it.crate }

    private fun dataRow(mimeType: String, value: String): ContactDataRow =
        ContactDataRow(mimeType = mimeType, values = List(15) { if (it == 0) value else null })

    private fun sampleImage(width: Int, height: Int): ByteArray {
        val rowSize = ((24 * width + 31) / 32) * 4
        val pixelArraySize = rowSize * height
        val fileSize = 54 + pixelArraySize

        val buffer = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put('B'.code.toByte())
        buffer.put('M'.code.toByte())
        buffer.putInt(fileSize)
        buffer.putInt(0)
        buffer.putInt(54)

        buffer.putInt(40)
        buffer.putInt(width)
        buffer.putInt(height)
        buffer.putShort(1)
        buffer.putShort(24)
        buffer.putInt(0)
        buffer.putInt(pixelArraySize)
        buffer.putInt(2835)
        buffer.putInt(2835)
        buffer.putInt(0)
        buffer.putInt(0)

        for (y in 0 until height) {
            var written = 0
            for (x in 0 until width) {
                buffer.put(128.toByte())
                buffer.put((y * 255 / height).toByte())
                buffer.put((x * 255 / width).toByte())
                written += 3
            }
            while (written < rowSize) {
                buffer.put(0)
                written++
            }
        }

        return buffer.array()
    }
}
