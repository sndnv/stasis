package stasis.client_android.lib.staging

import java.nio.file.Path

interface FileStaging {
    suspend fun temporary(): Path
    suspend fun discard(file: Path)
    suspend fun destage(from: Path, to: Path)
}
