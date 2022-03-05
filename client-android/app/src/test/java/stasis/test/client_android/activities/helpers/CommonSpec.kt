package stasis.test.client_android.activities.helpers

import android.content.Context
import android.graphics.Typeface
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.activities.helpers.Common.StyledString
import stasis.client_android.activities.helpers.Common.asChangedString
import stasis.client_android.activities.helpers.Common.asChronoUnit
import stasis.client_android.activities.helpers.Common.asQuantityString
import stasis.client_android.activities.helpers.Common.asSizeString
import stasis.client_android.activities.helpers.Common.asString
import stasis.client_android.activities.helpers.Common.checksum
import stasis.client_android.activities.helpers.Common.crates
import stasis.client_android.activities.helpers.Common.fromAssignmentTypeString
import stasis.client_android.activities.helpers.Common.fromPolicyTypeString
import stasis.client_android.activities.helpers.Common.renderAsSpannable
import stasis.client_android.activities.helpers.Common.size
import stasis.client_android.activities.helpers.Common.toAssignmentTypeString
import stasis.client_android.activities.helpers.Common.toFields
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.helpers.Common.toOperationStageString
import stasis.client_android.activities.helpers.Common.toOperationStepString
import stasis.client_android.activities.helpers.Common.toPolicyTypeString
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import java.math.BigInteger
import java.nio.file.Paths
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class CommonSpec {
    @Test
    fun convertUuidsToMinimizedStrings() {
        val uuid = UUID.fromString("eb07fd7f-6ec6-4e6d-a92b-7a713abdd7f4")
        assertThat(uuid.toMinimizedString(), equalTo("7a713abdd7f4"))
    }

    @Test
    fun convertDurationsToFields() {
        assertThat(Duration.ofSeconds(0).toFields(), equalTo(Pair(0, ChronoUnit.SECONDS)))
        assertThat(Duration.ofSeconds(1).toFields(), equalTo(Pair(1, ChronoUnit.SECONDS)))
        assertThat(Duration.ofSeconds(59).toFields(), equalTo(Pair(59, ChronoUnit.SECONDS)))
        assertThat(Duration.ofSeconds(61).toFields(), equalTo(Pair(61, ChronoUnit.SECONDS)))

        assertThat(Duration.ofSeconds(60).toFields(), equalTo(Pair(1, ChronoUnit.MINUTES)))
        assertThat(Duration.ofMinutes(1).toFields(), equalTo(Pair(1, ChronoUnit.MINUTES)))
        assertThat(Duration.ofMinutes(59).toFields(), equalTo(Pair(59, ChronoUnit.MINUTES)))
        assertThat(Duration.ofMinutes(61).toFields(), equalTo(Pair(61, ChronoUnit.MINUTES)))

        assertThat(Duration.ofMinutes(60).toFields(), equalTo(Pair(1, ChronoUnit.HOURS)))
        assertThat(Duration.ofHours(1).toFields(), equalTo(Pair(1, ChronoUnit.HOURS)))
        assertThat(Duration.ofHours(23).toFields(), equalTo(Pair(23, ChronoUnit.HOURS)))
        assertThat(Duration.ofHours(25).toFields(), equalTo(Pair(25, ChronoUnit.HOURS)))

        assertThat(Duration.ofHours(24).toFields(), equalTo(Pair(1, ChronoUnit.DAYS)))
        assertThat(Duration.ofDays(1).toFields(), equalTo(Pair(1, ChronoUnit.DAYS)))
        assertThat(Duration.ofDays(2).toFields(), equalTo(Pair(2, ChronoUnit.DAYS)))
        assertThat(Duration.ofDays(3).toFields(), equalTo(Pair(3, ChronoUnit.DAYS)))
        assertThat(Duration.ofDays(99).toFields(), equalTo(Pair(99, ChronoUnit.DAYS)))
    }

    @Test
    fun convertDurationsToStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(Duration.ofSeconds(0).asString(context), equalTo("0 seconds"))
        assertThat(Duration.ofSeconds(1).asString(context), equalTo("1 second"))
        assertThat(Duration.ofSeconds(2).asString(context), equalTo("2 seconds"))
    }

    @Test
    fun convertLongsToStrings() {
        assertThat(0L.asString(), equalTo("0"))
        assertThat(1L.asString(), equalTo("1"))
        assertThat(100L.asString(), equalTo("100"))
        assertThat(1000L.asString(), equalTo("1,000"))
        assertThat(10000L.asString(), equalTo("10,000"))
    }

    @Test
    fun convertBigIntegersToSizeStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(BigInteger.valueOf(1L).asSizeString(context), equalTo("1 B"))
        assertThat(BigInteger.valueOf(1000L).asSizeString(context), equalTo("1.00 kB"))
        assertThat(BigInteger.valueOf(1024L).asSizeString(context), equalTo("1.02 kB"))
        assertThat(BigInteger.valueOf(10 * 1024L).asSizeString(context), equalTo("10.24 kB"))
        assertThat(BigInteger.valueOf(42 * 1024 * 1024L).asSizeString(context), equalTo("44.04 MB"))
    }

    @Test
    fun convertLongsToSizeStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(1L.asSizeString(context), equalTo("1 B"))
        assertThat(1000L.asSizeString(context), equalTo("1.00 kB"))
        assertThat(1024L.asSizeString(context), equalTo("1.02 kB"))
        assertThat((10L * 1024L).asSizeString(context), equalTo("10.24 kB"))
        assertThat((42L * 1024L * 1024L).asSizeString(context), equalTo("44.04 MB"))
    }

    @Test
    fun convertOperationTypesToStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(Operation.Type.Backup.asString(context), equalTo("Backup"))
        assertThat(Operation.Type.Recovery.asString(context), equalTo("Recovery"))
        assertThat(Operation.Type.Expiration.asString(context), equalTo("Expiration"))
        assertThat(Operation.Type.Validation.asString(context), equalTo("Validation"))
        assertThat(Operation.Type.KeyRotation.asString(context), equalTo("Key Rotation"))
        assertThat(
            Operation.Type.GarbageCollection.asString(context),
            equalTo("Garbage Collection")
        )
        assertThat(null.asString(context), equalTo("Unknown"))
    }

    @Test
    fun convertDatasetDefinitionRetentionToStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(
            DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.AtMost(versions = 0),
                duration = Duration.ofSeconds(42)
            ).asString(context),
            equalTo("42 seconds, at most 0 versions")
        )

        assertThat(
            DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.AtMost(versions = 1),
                duration = Duration.ofHours(21)
            ).asString(context),
            equalTo("21 hours, at most 1 version")
        )

        assertThat(
            DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.AtMost(versions = 2),
                duration = Duration.ofSeconds(1)
            ).asString(context),
            equalTo("1 second, at most 2 versions")
        )

        assertThat(
            DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.LatestOnly,
                duration = Duration.ofSeconds(42)
            ).asString(context),
            equalTo("42 seconds, latest version only")
        )

        assertThat(
            DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.All,
                duration = Duration.ofSeconds(42)
            ).asString(context),
            equalTo("42 seconds, all versions")
        )
    }

    @Test
    fun convertPolicyTypeStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat("at-most".toPolicyTypeString(context), equalTo("At most"))
        assertThat("latest-only".toPolicyTypeString(context), equalTo("Latest-only"))
        assertThat("all".toPolicyTypeString(context), equalTo("All"))

        try {
            "other".toPolicyTypeString(context)
        } catch (e: IllegalArgumentException) {
            assertThat(
                e.message,
                equalTo("Unexpected DatasetDefinition Retention Policy provided: [other]")
            )
        }

        assertThat("At most".fromPolicyTypeString(context), equalTo("at-most"))
        assertThat("Latest-only".fromPolicyTypeString(context), equalTo("latest-only"))
        assertThat("All".fromPolicyTypeString(context), equalTo("all"))

        try {
            "other".fromPolicyTypeString(context)
        } catch (e: IllegalArgumentException) {
            assertThat(
                e.message,
                equalTo("Unexpected DatasetDefinition Retention Policy provided: [other]")
            )
        }
    }

    @Test
    fun convertAssignmentTypeStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat("backup".toAssignmentTypeString(context), equalTo("Backup"))
        assertThat("expiration".toAssignmentTypeString(context), equalTo("Expiration"))
        assertThat("validation".toAssignmentTypeString(context), equalTo("Validation"))
        assertThat("key-rotation".toAssignmentTypeString(context), equalTo("Key Rotation"))

        try {
            "other".toAssignmentTypeString(context)
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected assignment type provided: [other]"))
        }

        assertThat("Backup".fromAssignmentTypeString(context), equalTo("backup"))
        assertThat("Expiration".fromAssignmentTypeString(context), equalTo("expiration"))
        assertThat("Validation".fromAssignmentTypeString(context), equalTo("validation"))
        assertThat("Key Rotation".fromAssignmentTypeString(context), equalTo("key-rotation"))

        try {
            "other".fromAssignmentTypeString(context)
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected assignment type provided: [other]"))
        }
    }

    @Test
    fun convertAssignmentsToTypeStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val schedule = UUID.randomUUID()
        val definition = UUID.randomUUID()

        assertThat(
            OperationScheduleAssignment.Backup(schedule, definition, emptyList())
                .toAssignmentTypeString(context),
            equalTo("Backup")
        )

        assertThat(
            OperationScheduleAssignment.Expiration(schedule).toAssignmentTypeString(context),
            equalTo("Expiration")
        )

        assertThat(
            OperationScheduleAssignment.Validation(schedule).toAssignmentTypeString(context),
            equalTo("Validation")
        )

        assertThat(
            OperationScheduleAssignment.KeyRotation(schedule).toAssignmentTypeString(context),
            equalTo("Key Rotation")
        )
    }

    @Test
    fun convertOperationStageStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat("specification".toOperationStageString(context), equalTo("Specification"))
        assertThat("examination".toOperationStageString(context), equalTo("Examination"))
        assertThat("collection".toOperationStageString(context), equalTo("Collection"))
        assertThat("processing".toOperationStageString(context), equalTo("Processing"))
        assertThat("metadata".toOperationStageString(context), equalTo("Metadata"))
        assertThat("metadata-applied".toOperationStageString(context), equalTo("Metadata Applied"))
        assertThat("other".toOperationStageString(context), equalTo("other"))
    }

    @Test
    fun convertOperationStepStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat("processing".toOperationStepString(context), equalTo("Processing"))
        assertThat("collection".toOperationStepString(context), equalTo("Collection"))
        assertThat("push".toOperationStepString(context), equalTo("Push"))
        assertThat("other".toOperationStepString(context), equalTo("other"))
    }

    @Test
    fun extractEntityMetadataSize() {
        assertThat(fileMetadata.size(), equalTo(fileMetadata.size))
        assertThat(directoryMetadata.size(), equalTo(null))
    }

    @Test
    fun extractEntityMetadataChecksum() {
        assertThat(fileMetadata.checksum(), equalTo(fileMetadata.checksum))
        assertThat(directoryMetadata.checksum(), equalTo(null))
    }

    @Test
    fun extractEntityMetadataCrates() {
        assertThat(fileMetadata.crates(), equalTo(fileMetadata.crates.size))
        assertThat(directoryMetadata.crates(), equalTo(null))
    }

    @Test
    fun convertEntityMetadataChangedStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat("content".asChangedString(context), equalTo("content"))
        assertThat("metadata".asChangedString(context), equalTo("metadata"))

        try {
            "other".asChangedString(context)
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected value provided: [other]"))
        }
    }

    @Test
    fun convertValuesToStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat((null as Int?).asString(context), equalTo("-"))
        assertThat((5 as Int?).asString(context), equalTo("5"))
        assertThat((42L as Long?).asString(context), equalTo("42"))
        assertThat(BigInteger.valueOf(42L).asString(context), equalTo("42"))
        assertThat(Paths.get("test-path").asString(context), equalTo("test-path"))
        assertThat((true as Boolean?).asString(context), equalTo("Yes"))
        assertThat((false as Boolean?).asString(context), equalTo("No"))
        assertThat(("test" as String?).asString(context), equalTo("test"))

        try {
            (4.2 as Double?).asString(context)
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected value provided: [4.2]"))
        }
    }

    @Test
    fun convertChronoUnitsToStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(ChronoUnit.DAYS.asString(context), equalTo("days"))
        assertThat(ChronoUnit.HOURS.asString(context), equalTo("hours"))
        assertThat(ChronoUnit.MINUTES.asString(context), equalTo("minutes"))
        assertThat(ChronoUnit.SECONDS.asString(context), equalTo("seconds"))

        try {
            ChronoUnit.WEEKS.asString(context)
            fail("Unexpected result received")
        } catch (e: java.lang.IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected ChronoUnit provided: [WEEKS]"))
        }
    }

    @Test
    fun convertChronoUnitsToQualifiedStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(ChronoUnit.DAYS.asQuantityString(amount = 1, context), equalTo("day"))
        assertThat(ChronoUnit.DAYS.asQuantityString(amount = 10, context), equalTo("days"))
        assertThat(ChronoUnit.HOURS.asQuantityString(amount = 1, context), equalTo("hour"))
        assertThat(ChronoUnit.HOURS.asQuantityString(amount = 10, context), equalTo("hours"))
        assertThat(ChronoUnit.MINUTES.asQuantityString(amount = 1, context), equalTo("minute"))
        assertThat(ChronoUnit.MINUTES.asQuantityString(amount = 10, context), equalTo("minutes"))
        assertThat(ChronoUnit.SECONDS.asQuantityString(amount = 1, context), equalTo("second"))
        assertThat(ChronoUnit.SECONDS.asQuantityString(amount = 10, context), equalTo("seconds"))

        try {
            ChronoUnit.WEEKS.asQuantityString(amount = 1, context)
            fail("Unexpected result received")
        } catch (e: java.lang.IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected ChronoUnit provided: [WEEKS]"))
        }
    }

    @Test
    fun convertStringsToChronoUnits() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat("days".asChronoUnit(context), equalTo(ChronoUnit.DAYS))
        assertThat("hours".asChronoUnit(context), equalTo(ChronoUnit.HOURS))
        assertThat("minutes".asChronoUnit(context), equalTo(ChronoUnit.MINUTES))
        assertThat("seconds".asChronoUnit(context), equalTo(ChronoUnit.SECONDS))

        try {
            "other".asChronoUnit(context)
            fail("Unexpected result received")
        } catch (e: java.lang.IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected ChronoUnit string provided: [other]"))
        }
    }

    @Test
    fun convertDaysOfTheWeekToStrings() {
        val someDays =
            setOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        val allDays = someDays + setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)

        assertThat(someDays.asString(DayOfWeek.MONDAY), equalTo("Tue, Fri, Sat, Sun"))
        assertThat(allDays.asString(DayOfWeek.MONDAY), equalTo("Mon, Tue, Wed, Thu, Fri, Sat, Sun"))
        assertThat(emptySet<DayOfWeek>().asString(DayOfWeek.MONDAY), equalTo(""))

        assertThat(someDays.asString(DayOfWeek.SATURDAY), equalTo("Sat, Sun, Tue, Fri"))
        assertThat(
            allDays.asString(DayOfWeek.SATURDAY),
            equalTo("Sat, Sun, Mon, Tue, Wed, Thu, Fri")
        )
        assertThat(emptySet<DayOfWeek>().asString(DayOfWeek.SATURDAY), equalTo(""))

        assertThat(someDays.asString(DayOfWeek.SUNDAY), equalTo("Sun, Tue, Fri, Sat"))
        assertThat(allDays.asString(DayOfWeek.SUNDAY), equalTo("Sun, Mon, Tue, Wed, Thu, Fri, Sat"))
        assertThat(emptySet<DayOfWeek>().asString(DayOfWeek.SUNDAY), equalTo(""))
    }

    @Test
    fun renderSpannableStrings() {
        val rendered = "%1\$s | %2\$s | %3\$s".renderAsSpannable(
            StyledString(
                placeholder = "%1\$s",
                content = "test-1",
                style = StyleSpan(Typeface.BOLD_ITALIC)
            ),
            StyledString(
                placeholder = "%2\$s",
                content = "test-2",
                style = StyleSpan(Typeface.ITALIC)
            ),
            StyledString(
                placeholder = "%3\$s",
                content = "test-3",
                style = StyleSpan(Typeface.BOLD)
            )
        )

        val spans = rendered.getSpans(1, rendered.length, StyleSpan::class.java)

        assertThat(rendered.toString(), equalTo("test-1 | test-2 | test-3"))
        assertThat(spans.size, equalTo(3))
        assertThat(spans[0].style, equalTo(Typeface.BOLD_ITALIC))
        assertThat(spans[1].style, equalTo(Typeface.ITALIC))
        assertThat(spans[2].style, equalTo(Typeface.BOLD))
    }

    private val fileMetadata = EntityMetadata.File(
        path = Paths.get("/tmp/file/one"),
        size = 15 * 1024L,
        link = null,
        isHidden = false,
        created = Instant.now().minusSeconds(42).truncatedTo(ChronoUnit.SECONDS),
        updated = Instant.now().truncatedTo(ChronoUnit.SECONDS),
        owner = "root",
        group = "root",
        permissions = "rwxrwxrwx",
        checksum = BigInteger("1"),
        crates = mapOf(
            Paths.get("/tmp/file/one_0") to UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
        )
    )

    private val directoryMetadata = EntityMetadata.Directory(
        path = Paths.get("/tmp/directory/one"),
        link = null,
        isHidden = false,
        created = Instant.now().minusSeconds(42).truncatedTo(ChronoUnit.SECONDS),
        updated = Instant.now().truncatedTo(ChronoUnit.SECONDS),
        owner = "root",
        group = "root",
        permissions = "rwxrwxrwx"
    )
}
