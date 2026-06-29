package stasis.test.client_android.activities.fragments.backup

import okio.ByteString.Companion.encodeUtf8
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import stasis.client_android.activities.fragments.backup.EntryDisplay
import stasis.client_android.lib.model.EntityMetadata
import java.math.BigInteger
import java.nio.file.FileSystems
import java.time.Instant

class EntryDisplaySpec {
    private val filesystem = FileSystems.getDefault()

    private fun library(path: String, attributes: String): EntityMetadata.Library =
        EntityMetadata.Library(
            path = path,
            created = Instant.now(),
            updated = Instant.now(),
            size = 1L,
            checksum = BigInteger.ONE,
            crates = emptyMap(),
            compression = "none",
            attributes = attributes.encodeUtf8()
        )

    private fun file(path: String): EntityMetadata.File =
        EntityMetadata.File(
            path = path,
            link = null,
            isHidden = false,
            created = Instant.now(),
            updated = Instant.now(),
            owner = "test",
            group = "test",
            permissions = "rw-r--r--",
            size = 1L,
            checksum = BigInteger.ONE,
            crates = emptyMap(),
            compression = "none"
        )

    @Test
    fun decodeLibraryAttributes() {
        assertThat(
            EntryDisplay.decodeAttributes(
                library("calendar:/uid-1", "{\"name\":\"test\",\"calendar\":\"test a\"}")
            ),
            equalTo(mapOf("name" to "test", "calendar" to "test a"))
        )
    }

    @Test
    fun returnNoAttributesForFilesystemOrMissingMetadata() {
        assertThat(EntryDisplay.decodeAttributes(file("/tmp/test")), equalTo(emptyMap()))
        assertThat(EntryDisplay.decodeAttributes(null), equalTo(emptyMap()))
    }

    @Test
    fun returnNoAttributesForBlankAttributes() {
        assertThat(EntryDisplay.decodeAttributes(library("calendar:/uid-1", "")), equalTo(emptyMap()))
    }

    @Test
    fun useTheLibraryDisplayNameWhenPresent() {
        assertThat(
            EntryDisplay.displayName("calendar:/uid-1", library("calendar:/uid-1", "{\"name\":\"test\"}"), filesystem),
            equalTo("test")
        )
    }

    @Test
    fun fallBackToTheKeySegmentForLibraryWithoutName() {
        assertThat(
            EntryDisplay.displayName("calendar:/uid-1", library("calendar:/uid-1", "{}"), filesystem),
            equalTo("uid-1")
        )
    }

    @Test
    fun useTheFileNameForFilesystemMetadata() {
        assertThat(
            EntryDisplay.displayName("/tmp/test.pdf", file("/tmp/test.pdf"), filesystem),
            equalTo("test.pdf")
        )
    }
}
