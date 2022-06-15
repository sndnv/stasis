package stasis.client_android.lib.ops.recovery.stages.internal

import okio.Source
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.ops.recovery.Providers
import java.nio.file.Path

object DecryptedCrates {
    fun List<Triple<Int, Path, suspend () -> Source>>.decrypt(
        withPartSecret: (Path) -> DeviceFileSecret,
        providers: Providers
    ): List<Triple<Int, Path, suspend () -> Source>> {
        return map { (partId, partPath, source) ->
            val decrypted = suspend { providers.decryptor.decrypt(source(), withPartSecret(partPath)) }
            Triple(partId, partPath, decrypted)
        }
    }
}
