package stasis.client_android.lib.ops.backup.stages.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.Sink
import okio.buffer
import okio.sink
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.ops.backup.Providers
import java.nio.file.Path
import kotlin.math.min

class PartitionedSource(
    private val source: BufferedSource,
    private val providers: Providers,
    private val withPartSecret: (Int) -> DeviceFileSecret,
    private val onPartStaged: () -> Unit,
    private val withMaximumPartSize: Long
) {
    suspend fun partitionAndStage(): List<Pair<Path, Path>> = withContext(Dispatchers.IO) {
        val parts = mutableListOf<Pair<Path, Path>>()

        var collected: Long = 0L
        var partId: Int = 0
        var secret: DeviceFileSecret = withPartSecret(partId)
        var staged: BufferedSink = providers.staging.temporary().let { temporary ->
            parts += secret.file to temporary
            temporary.sink().encrypted(providers, secret).buffer()
        }

        val next = Buffer()

        try {
            while (!source.exhausted()) {
                val read = source.read(next, min(withMaximumPartSize, SourceMaxChunkSize))

                if (read > 0) {
                    if (collected + read > withMaximumPartSize) {
                        staged.flush()
                        staged.flush()
                        staged.close()
                        onPartStaged()

                        collected = read
                        partId += 1
                        secret = withPartSecret(partId)
                        staged = providers.staging.temporary().let { temporary ->
                            parts += secret.file to temporary
                            temporary.sink().encrypted(providers, secret).buffer()
                        }
                    } else {
                        collected += read
                    }

                    staged.write(next, read)
                }
            }
        } catch (e: Throwable) {
            parts.forEach { (_, staged) -> providers.staging.discard(staged) }
            throw e
        } finally {
            staged.flush()
            staged.close()
            onPartStaged()

            source.close()
        }

        parts
    }

    private fun Sink.encrypted(providers: Providers, fileSecret: DeviceFileSecret): Sink =
        providers.encryptor.encrypt(this, fileSecret)

    companion object {
        const val SourceMaxChunkSize: Long = 8192L
    }
}
