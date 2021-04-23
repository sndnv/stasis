package stasis.test.client_android.lib.model

import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.users.User
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.streams.asSequence

object Generators {
    fun generateUser(): User =
        User(
            id = UUID.randomUUID(),
            salt = generateString(withSize = 16),
            active = true,
            limits = null,
            permissions = emptySet()
        )

    fun generateSchedule(): Schedule {
        val rnd = ThreadLocalRandom.current()

        return Schedule(
            id = UUID.randomUUID(),
            info = generateString(withSize = 16),
            isPublic = rnd.nextBoolean(),
            start = LocalDateTime.now(),
            interval = generateDuration()
        )
    }

    fun generateDevice(): Device =
        Device(
            id = UUID.randomUUID(),
            node = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            active = true,
            limits = null
        )

    fun generateDefinition(): DatasetDefinition {
        val rnd = ThreadLocalRandom.current()

        return DatasetDefinition(
            id = UUID.randomUUID(),
            info = generateString(withSize = 16),
            device = UUID.randomUUID(),
            redundantCopies = rnd.nextInt(0, 42),
            existingVersions = DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.All,
                duration = generateDuration()
            ),
            removedVersions = DatasetDefinition.Retention(
                policy = DatasetDefinition.Retention.Policy.All,
                duration = generateDuration()
            )
        )
    }

    fun generateEntry(): DatasetEntry =
        DatasetEntry(
            id = UUID.randomUUID(),
            definition = UUID.randomUUID(),
            device = UUID.randomUUID(),
            data = generateList(g = { UUID.randomUUID() }).toSet(),
            metadata = UUID.randomUUID(),
            created = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        )

    fun generateDuration(): Duration {
        val rnd = ThreadLocalRandom.current()

        return Duration.ofSeconds(rnd.nextLong(0, Duration.ofDays(1).seconds))
    }

    fun generateString(withSize: Long): String {
        val rnd = ThreadLocalRandom.current()

        return rnd.ints(withSize, 0, chars.size)
            .asSequence()
            .map { chars[it] }
            .joinToString("")
    }

    fun generateUri(): String {
        val rnd = ThreadLocalRandom.current()

        val host = generateString(withSize = 10)
        val port = rnd.nextInt(50000, 60000)
        val endpoint = generateString(withSize = 20)
        return "http://$host:$port/$endpoint".toLowerCase()
    }

    fun <T> generateList(
        min: Int = 0,
        max: Int = 10,
        g: () -> T
    ): List<T> {
        val rnd = ThreadLocalRandom.current()

        val size = rnd.nextInt(min, max)
        return (0..size).map { g() }
    }

    private val chars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
}
