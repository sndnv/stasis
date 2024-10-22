package stasis.client_android.mocks

import org.jose4j.jwk.RsaJwkGenerator
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.users.User
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.streams.asSequence

object Generators {
    fun generateJwt(sub: String): String {
        val claims = JwtClaims().apply {
            subject = sub
            setIssuedAtToNow()
            setGeneratedJwtId()
            setExpirationTimeMinutesInTheFuture(420f)
        }

        val jws = JsonWebSignature().apply {
            payload = claims.toJson()
            key = rsaKey.privateKey
            keyIdHeaderValue = rsaKey.keyId
            algorithmHeaderValue = AlgorithmIdentifiers.RSA_USING_SHA256
        }

        return jws.compactSerialization
    }

    fun generateUser(): User =
        User(
            id = UUID.randomUUID(),
            salt = generateString(withSize = 16),
            active = true,
            limits = null,
            permissions = emptySet(),
            created = Instant.now(),
            updated = Instant.now(),
        )

    fun generateSchedule(): Schedule {
        val rnd = ThreadLocalRandom.current()

        return Schedule(
            id = UUID.randomUUID(),
            info = generateString(withSize = 16),
            isPublic = rnd.nextBoolean(),
            start = LocalDateTime.now(),
            interval = generateDuration(),
            created = Instant.now(),
            updated = Instant.now(),
        )
    }

    fun generateDevice(): Device =
        Device(
            id = UUID.randomUUID(),
            name = generateString(withSize = 12),
            node = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            active = true,
            limits = null,
            created = Instant.now(),
            updated = Instant.now(),
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
            ),
            created = Instant.now(),
            updated = Instant.now(),
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
        return "http://$host:$port/$endpoint".lowercase()
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

    private val rsaKey = RsaJwkGenerator.generateJwk(2048).apply {
        keyId = "androidTest-rsa-key"
    }
}
