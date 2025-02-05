package stasis.test.client_android.persistence

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.persistence.Converters
import stasis.client_android.persistence.rules.RuleEntity
import stasis.client_android.persistence.schedules.ActiveScheduleEntity
import stasis.client_android.persistence.schedules.LocalScheduleEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class ConvertersSpec {
    @Test
    fun convertUUIDs() {
        val converters = Converters()

        val original = UUID.randomUUID()
        val converted = converters.uuidToString(original)

        assertThat(converted, equalTo(original.toString()))
        assertThat(converters.stringToUuid(converted), equalTo(original))
    }

    @Test
    fun convertRuleOperations() {
        val converters = Converters()

        val convertedInclude = converters.ruleOperationToString(Rule.Operation.Include)
        val convertedExclude = converters.ruleOperationToString(Rule.Operation.Exclude)

        assertThat(convertedInclude, equalTo("include"))
        assertThat(convertedExclude, equalTo("exclude"))
        assertThat(converters.ruleOperationToString(null), equalTo(null))

        assertThat(converters.stringToRuleOperation(convertedInclude), equalTo(Rule.Operation.Include))
        assertThat(converters.stringToRuleOperation(convertedExclude), equalTo(Rule.Operation.Exclude))
        assertThat(converters.stringToRuleOperation(null), equalTo(null))
    }

    @Test
    fun failToConvertInvalidRuleOperations() {
        val converters = Converters()

        try {
            converters.stringToRuleOperation("""other""")
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected operation provided: [other]"))
        }
    }

    @Test
    fun convertLocalDateTimes() {
        val converters = Converters()

        val timestampString = "2020-01-01T01:02:03"
        val timestamp = LocalDateTime.parse(timestampString)

        assertThat(converters.localDateTimeToString(timestamp), equalTo(timestampString))
        assertThat(converters.stringToLocalDateTime(timestampString), equalTo(timestamp))
    }

    @Test
    fun convertDurations() {
        val converters = Converters()

        val durationLong = 12 * 1000L
        val duration = Duration.ofMillis(durationLong)

        assertThat(converters.durationToLong(duration), equalTo(durationLong))
        assertThat(converters.longToDuration(durationLong), equalTo(duration))
    }

    @Test
    fun convertInstants() {
        val converters = Converters()

        val instantString = "2020-01-01T01:02:03Z"
        val instant = Instant.parse(instantString)
        val instantLong = instant.toEpochMilli()

        assertThat(converters.instantToLong(instant), equalTo(instantLong))
        assertThat(converters.longToInstant(instantLong), equalTo(instant))
    }

    @Test
    fun convertRulesToEntities() {
        val rule = Rule(
            id = 0L,
            operation = Rule.Operation.Include,
            directory = "/a/b/c",
            pattern = "**",
            definition = UUID.randomUUID()
        )

        val entity = RuleEntity(
            id = rule.id,
            operation = rule.operation,
            directory = rule.directory,
            pattern = rule.pattern,
            definition = rule.definition
        )

        assertThat(Converters.ruleToEntity(rule), equalTo(entity))
        assertThat(Converters.entityToRule(entity), equalTo(rule))
    }

    @Test
    fun convertActiveSchedulesToEntities() {
        val backupDefinition = UUID.randomUUID()

        val backupSchedule = ActiveSchedule(
            id = 0L,
            assignment = OperationScheduleAssignment.Backup(
                schedule = UUID.randomUUID(),
                definition = backupDefinition,
                entities = emptyList()
            )
        )

        val expirationSchedule = ActiveSchedule(
            id = 0L,
            assignment = OperationScheduleAssignment.Expiration(schedule = UUID.randomUUID())
        )

        val validationSchedule = ActiveSchedule(
            id = 0L,
            assignment = OperationScheduleAssignment.Validation(schedule = UUID.randomUUID())
        )

        val keyRotationSchedule = ActiveSchedule(
            id = 0L,
            assignment = OperationScheduleAssignment.KeyRotation(schedule = UUID.randomUUID())
        )

        val backupEntity = ActiveScheduleEntity(
            id = 0L,
            schedule = backupSchedule.assignment.schedule,
            type = "backup",
            data = """{"definition":"$backupDefinition","entities":[]}"""
        )

        val expirationEntity = ActiveScheduleEntity(
            id = 0L,
            schedule = expirationSchedule.assignment.schedule,
            type = "expiration",
            data = null
        )

        val validationEntity = ActiveScheduleEntity(
            id = 0L,
            schedule = validationSchedule.assignment.schedule,
            type = "validation",
            data = null
        )

        val keyRotationEntity = ActiveScheduleEntity(
            id = 0L,
            schedule = keyRotationSchedule.assignment.schedule,
            type = "key_rotation",
            data = null
        )

        assertThat(Converters.activeScheduleToEntity(backupSchedule), equalTo(backupEntity))
        assertThat(Converters.entityToActiveSchedule(backupEntity), equalTo(backupSchedule))

        assertThat(Converters.activeScheduleToEntity(expirationSchedule), equalTo(expirationEntity))
        assertThat(Converters.entityToActiveSchedule(expirationEntity), equalTo(expirationSchedule))

        assertThat(Converters.activeScheduleToEntity(validationSchedule), equalTo(validationEntity))
        assertThat(Converters.entityToActiveSchedule(validationEntity), equalTo(validationSchedule))

        assertThat(Converters.activeScheduleToEntity(keyRotationSchedule), equalTo(keyRotationEntity))
        assertThat(Converters.entityToActiveSchedule(keyRotationEntity), equalTo(keyRotationSchedule))
    }

    @Test
    fun convertAccessTokenResponsesToJson() {
        val response = AccessTokenResponse(
            access_token = "test-access-token",
            refresh_token = "test-refresh-token",
            expires_in = 42L,
            scope = "test-scope"
        )

        val string =
            """{"access_token":"test-access-token","refresh_token":"test-refresh-token","expires_in":42,"scope":"test-scope"}"""

        assertThat(Converters.accessTokenResponseToString(response), equalTo(string))
        assertThat(Converters.stringToAccessTokenResponse(string), equalTo(response))
    }


    @Test
    fun convertLocalSchedulesToEntities() {
        val now = Instant.now()

        val schedule = Schedule(
            id = UUID.randomUUID(),
            info = "test-info",
            isPublic = false,
            start = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
            interval = Duration.ofSeconds(60),
            created = now,
            updated = now,
        )

        val entity = LocalScheduleEntity(
            id = schedule.id,
            info = schedule.info,
            start = schedule.start,
            interval = schedule.interval,
            created = schedule.created
        )

        assertThat(Converters.localScheduleEntityToSchedule(entity), equalTo(schedule))
        assertThat(Converters.scheduleToLocalScheduleEntity(schedule), equalTo(entity))
    }

    @Test
    fun convertActiveSchedulesToJson() {
        val backupDefinition = UUID.randomUUID()

        val backupSchedule = ActiveSchedule(
            id = 1L,
            assignment = OperationScheduleAssignment.Backup(
                schedule = UUID.randomUUID(),
                definition = backupDefinition,
                entities = emptyList()
            )
        )

        val expirationSchedule = ActiveSchedule(
            id = 2L,
            assignment = OperationScheduleAssignment.Expiration(schedule = UUID.randomUUID())
        )

        val validationSchedule = ActiveSchedule(
            id = 3L,
            assignment = OperationScheduleAssignment.Validation(schedule = UUID.randomUUID())
        )

        val keyRotationSchedule = ActiveSchedule(
            id = 4L,
            assignment = OperationScheduleAssignment.KeyRotation(schedule = UUID.randomUUID())
        )

        val backupString =
            """{"id":1,"assignment":{"schedule":"${backupSchedule.assignment.schedule}","definition":"$backupDefinition","entities":[],"type":"backup"}}"""

        val expirationString =
            """{"id":2,"assignment":{"schedule":"${expirationSchedule.assignment.schedule}","type":"expiration"}}"""

        val validationString =
            """{"id":3,"assignment":{"schedule":"${validationSchedule.assignment.schedule}","type":"validation"}}"""

        val keyRotationString =
            """{"id":4,"assignment":{"schedule":"${keyRotationSchedule.assignment.schedule}","type":"key_rotation"}}"""

        assertThat(Converters.activeScheduleToString(backupSchedule), equalTo(backupString))
        assertThat(Converters.stringToActiveSchedule(backupString), equalTo(backupSchedule))

        assertThat(Converters.activeScheduleToString(expirationSchedule), equalTo(expirationString))
        assertThat(Converters.stringToActiveSchedule(expirationString), equalTo(expirationSchedule))

        assertThat(Converters.activeScheduleToString(validationSchedule), equalTo(validationString))
        assertThat(Converters.stringToActiveSchedule(validationString), equalTo(validationSchedule))

        assertThat(Converters.activeScheduleToString(keyRotationSchedule), equalTo(keyRotationString))
        assertThat(Converters.stringToActiveSchedule(keyRotationString), equalTo(keyRotationSchedule))
    }
}
