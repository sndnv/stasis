package stasis.client_android.lib.telemetry.analytics

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import stasis.client_android.lib.telemetry.ApplicationInformation
import java.time.Instant


interface AnalyticsEntry {
    val runtime: RuntimeInformation
    val events: List<Event>
    val failures: List<Failure>
    val created: Instant
    val updated: Instant

    fun asCollected(): Collected = Collected(
        runtime = runtime,
        events = events,
        failures = failures,
        created = created,
        updated = updated
    )

    fun asJson(): AsJson

    @JsonClass(generateAdapter = true)
    data class AsJson(
        @field:Json(name = "entry_type")
        val entryType: String,
        override val runtime: RuntimeInformation,
        override val events: List<Event>,
        override val failures: List<Failure>,
        override val created: Instant,
        override val updated: Instant,
    ) : AnalyticsEntry {
        override fun asJson(): AsJson  = this
    }

    data class Collected(
        override val runtime: RuntimeInformation,
        override val events: List<Event>,
        override val failures: List<Failure>,
        override val created: Instant,
        override val updated: Instant,
    ) : AnalyticsEntry {
        override fun asCollected(): Collected = this

        override fun asJson(): AsJson = AsJson(
            entryType = "collected",
            runtime = runtime,
            events = events,
            failures = failures,
            created = created,
            updated = updated
        )

        fun withEvent(name: String, attributes: Map<String, String>): Collected {
            val event = uniqueEventFrom(name, attributes)
            return copy(
                events = events + Event(id = events.size, event = event),
                updated = Instant.now()
            )
        }

        fun withFailure(message: String): Collected = copy(
            failures = failures + Failure(
                message = message,
                timestamp = Instant.now()
            ),
            updated = Instant.now()
        )

        fun discardEvents(): Collected = copy(
            events = emptyList(),
            updated = Instant.now()
        )

        fun discardFailures(): Collected = copy(
            failures = emptyList(),
            updated = Instant.now()
        )
    }

    @JsonClass(generateAdapter = true)
    data class Event(
        val id: Int,
        val event: String
    )

    @JsonClass(generateAdapter = true)
    data class Failure(
        val message: String,
        val timestamp: Instant
    )

    @JsonClass(generateAdapter = true)
    data class RuntimeInformation(
        val id: String,
        val app: String,
        val jre: String,
        val os: String
    ) {
        companion object {
            operator fun invoke(app: ApplicationInformation): RuntimeInformation = RuntimeInformation(
                id = RuntimeId,
                app = app.asString(),
                jre = JRE.asString(),
                os = OS.asString()
            )

            val RuntimeId: String = java.util.UUID.randomUUID().toString()

            object JRE {
                val version: String = System.getProperty("java.vm.version", "unknown")
                val vendor: String = System.getProperty("java.vm.vendor", "unknown")

                fun asString(): String = "$version;$vendor"
            }

            object OS {
                val arch: String = System.getProperty("os.arch", "unknown")
                val name: String = System.getProperty("os.name", "unknown")
                val version: String = System.getProperty("os.version", "unknown")

                fun asString(): String = "$name;$version;$arch"
            }
        }
    }

    companion object {
        fun collected(app: ApplicationInformation): Collected {
            val now = Instant.now()
            return Collected(
                runtime = RuntimeInformation(app = app),
                events = emptyList(),
                failures = emptyList(),
                created = now,
                updated = now
            )
        }

        private fun uniqueEventFrom(name: String, attributes: Map<String, String>): String =
            if (attributes.isNotEmpty()) {
                val flattened = attributes
                    .toList()
                    .sortedBy { it.first }
                    .joinToString(separator = ",") { (k, v) -> "$k='$v'" }

                "$name{$flattened}"
            } else {
                name
            }
    }
}
