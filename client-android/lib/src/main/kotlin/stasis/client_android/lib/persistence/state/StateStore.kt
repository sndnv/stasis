package stasis.client_android.lib.persistence.state

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.take
import stasis.client_android.lib.utils.Try
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.streams.toList

class StateStore<S>(
    private val target: Path,
    private val retainedVersions: Int,
    private val serdes: Serdes<S>
) {
    init {
        Files.createDirectories(target)
    }

    suspend fun persist(state: S) = coroutineScope {
        val serialized = serdes.serialize(state)
        val timestamp = Instant.now().toEpochMilli().toString()

        Files.createDirectories(target)

        target.resolve("state_$timestamp").writeBytes(serialized)

        prune(keep = retainedVersions)
    }

    suspend fun discard() =
        prune(keep = 0)

    suspend fun prune(keep: Int) =
        collectStateFiles().dropLast(n = keep).forEach { Files.delete(it) }

    suspend fun restore(): S? {
        fun load(file: Path): S? =
            when (val result = serdes.deserialize(file.readBytes())) {
                is Try.Success -> result.value
                is Try.Failure -> null
            }

        return collectStateFiles()
            .reversed()
            .asFlow()
            .mapNotNull { load(it) }
            .take(1)
            .firstOrNull()
    }

    private suspend fun collectStateFiles(): List<Path> = coroutineScope {
        Files
            .walk(target)
            .filter { path ->
                !Files.isDirectory(path) && path.fileName.toString().startsWith("state_")
            }
            .toList()
            .sorted()
    }

    interface Serdes<S> {
        fun serialize(state: S): ByteArray
        fun deserialize(bytes: ByteArray): Try<S>
    }

    companion object {
        const val MinRetainedVersions: Int = 2

        operator fun <S> invoke(
            target: Path,
            serdes: Serdes<S>
        ): StateStore<S> =
            StateStore(
                target = target,
                retainedVersions = MinRetainedVersions,
                serdes = serdes
            )
    }
}
