package stasis.client_android.lib.ops.recovery.stages.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Source
import okio.buffer
import okio.sink
import stasis.client_android.lib.ops.recovery.Providers
import java.nio.file.Files
import java.nio.file.Path

object DestagedByteStringSource {
    suspend fun Source.destage(to: Path, providers: Providers) = withContext(Dispatchers.IO) {
        val staged = providers.staging.temporary()

        try {
            staged.sink().buffer().use {
                it.writeAll(this@destage)
            }

            providers.staging.destage(from = staged, to = to)
        } catch (e: Throwable) {
            providers.staging.discard(staged)
            throw e
        }
    }
}
