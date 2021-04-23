package stasis.client_android.lib.ops.recovery.stages.internal

import okio.Source
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.ops.recovery.Providers
import java.nio.file.Path

object DecryptedCrates {
    fun List<Pair<Path, Source>>.decrypt(
        withPartSecret: (Path) -> DeviceFileSecret,
        providers: Providers
    ): List<Pair<Path, Source>> {
        return map { (partPath, source) ->
            partPath to providers.decryptor.decrypt(source, withPartSecret(partPath))
        }
    }
}
