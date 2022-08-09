package stasis.test.client_android.lib.persistence.state

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import stasis.client_android.lib.persistence.state.StateStore
import stasis.client_android.lib.utils.Try
import stasis.test.client_android.lib.ResourceHelpers.FileSystemSetup
import stasis.test.client_android.lib.ResourceHelpers.content
import stasis.test.client_android.lib.ResourceHelpers.createMockFileSystem
import stasis.test.client_android.lib.ResourceHelpers.files
import kotlin.io.path.writeBytes

class StateStoreSpec : WordSpec({
    "A StateStore" should {
        data class State(val a: String, val b: Int, val c: Boolean)

        val serdes = object : StateStore.Serdes<Map<String, State>> {
            override fun serialize(state: Map<String, State>): ByteArray =
                state
                    .map { (k, v) -> "$k->${v.a},${v.b},${v.c}" }
                    .joinToString(";")
                    .toByteArray()


            override fun deserialize(bytes: ByteArray): Try<Map<String, State>> = Try {
                String(bytes)
                    .split(";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }.associate { entry ->
                        val kv = entry.split("->")
                        require(kv.size == 2) { "Unexpected result received: [$kv]" }

                        val fields = kv[1].split(",")
                        require(fields.size == 3) { "Unexpected result received: [$fields]" }

                        kv[0] to State(a = fields[0], b = fields[1].toInt(), c = fields[2].toBooleanStrict())
                    }
            }
        }

        val setup = FileSystemSetup.Unix

        "support persisting state to file" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/store")

            val store = StateStore(
                target = target,
                retainedVersions = 10,
                serdes = serdes
            )

            val state = mapOf(
                "id-a" to State(a = "a", b = 1, c = true),
                "id-b" to State(a = "b", b = 2, c = false),
                "id-c" to State(a = "c", b = 3, c = true)
            )

            store.persist(state)

            val persistedStatePath = when (val file = target.files().firstOrNull()) {
                null -> fail("Expected at least one file but none were found")
                else -> file
            }

            val content = persistedStatePath.content()
            val deserialized = serdes.deserialize(content.toByteArray())
            deserialized shouldBe (Try.Success(state))
        }

        "support pruning old state files" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/store")

            val store = StateStore(
                target = target,
                retainedVersions = 10,
                serdes = serdes
            )

            val initialState = emptyMap<String, State>()

            val updatedState = mapOf(
                "id-a" to State(a = "a", b = 1, c = true)
            )

            val latestState = mapOf(
                "id-a" to State(a = "a", b = 1, c = true),
                "id-b" to State(a = "b", b = 2, c = false),
                "id-c" to State(a = "c", b = 3, c = true)
            )

            store.persist(state = initialState)
            delay(50)
            store.persist(state = updatedState)
            delay(50)
            store.persist(state = latestState)
            delay(50)

            val filesBeforePruning = target.files()
            val stateBeforePruning = filesBeforePruning.map { serdes.deserialize(it.content().toByteArray()) }

            store.prune(keep = 1)

            val filesAfterPruning = target.files()
            val stateAfterPruning = filesAfterPruning.map { serdes.deserialize(it.content().toByteArray()) }

            filesBeforePruning.size shouldBe (3)
            filesAfterPruning.size shouldBe (1)

            stateBeforePruning.size shouldBe (3)
            stateBeforePruning[0] shouldBe (Try.Success(initialState))
            stateBeforePruning[1] shouldBe (Try.Success(updatedState))
            stateBeforePruning[2] shouldBe (Try.Success(latestState))

            stateAfterPruning.size shouldBe (1)
            stateAfterPruning[0] shouldBe (Try.Success(latestState))
        }

        "support discarding state files" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/store")

            val store = StateStore(
                target = target,
                retainedVersions = 10,
                serdes = serdes
            )

            val initialState = emptyMap<String, State>()

            val updatedState = mapOf(
                "id-a" to State(a = "a", b = 1, c = true)
            )

            val latestState = mapOf(
                "id-a" to State(a = "a", b = 1, c = true),
                "id-b" to State(a = "b", b = 2, c = false),
                "id-c" to State(a = "c", b = 3, c = true)
            )

            store.persist(state = initialState)
            delay(50)
            store.persist(state = updatedState)
            delay(50)
            store.persist(state = latestState)
            delay(50)

            val filesBeforePruning = target.files()
            val stateBeforePruning = filesBeforePruning.map { serdes.deserialize(it.content().toByteArray()) }

            store.discard()

            val filesAfterPruning = target.files()

            filesBeforePruning.size shouldBe (3)
            filesAfterPruning.size shouldBe (0)

            stateBeforePruning.size shouldBe (3)
            stateBeforePruning[0] shouldBe (Try.Success(initialState))
            stateBeforePruning[1] shouldBe (Try.Success(updatedState))
            stateBeforePruning[2] shouldBe (Try.Success(latestState))
        }

        "support restoring existing state from file" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/store")

            val store = StateStore(
                target = target,
                retainedVersions = 10,
                serdes = serdes
            )

            val state = mapOf(
                "id-a" to State(a = "a", b = 1, c = true),
                "id-b" to State(a = "b", b = 2, c = false),
                "id-c" to State(a = "c", b = 3, c = true)
            )

            store.persist(state)

            store.restore() shouldBe (state)
        }

        "handle deserialization failures" {
            val (filesystem, _) = createMockFileSystem(setup)

            val target = filesystem.getPath("/store")

            val store = StateStore(
                target = target,
                retainedVersions = 10,
                serdes = serdes
            )

            val initialState = emptyMap<String, State>()

            val updatedState = mapOf(
                "id-a" to State(a = "a", b = 1, c = true)
            )

            val latestState = mapOf(
                "id-a" to State(a = "a", b = 1, c = true),
                "id-b" to State(a = "b", b = 2, c = false),
                "id-c" to State(a = "c", b = 3, c = true)
            )

            store.persist(state = initialState)
            delay(50)
            store.persist(state = updatedState)
            delay(50)
            store.persist(state = latestState)
            delay(50)

            target.files().lastOrNull()?.writeBytes("invalid".toByteArray())
            store.restore() shouldBe (updatedState)

            target.files().forEach { it.writeBytes("invalid".toByteArray()) }
            store.restore() shouldBe (null)
        }
    }
})
