package stasis.client_android.lib.staging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

class DefaultFileStaging(
    private val storeDirectory: Path?,
    private val prefix: String,
    private val suffix: String
) : FileStaging {
    override suspend fun temporary(): Path = withContext(Dispatchers.IO) {
        when (storeDirectory) {
            null -> Files.createTempFile(prefix, suffix, temporaryFileAttributes)
            else -> Files.createTempFile(storeDirectory, prefix, suffix, temporaryFileAttributes)
        }
    }

    override suspend fun discard(file: Path): Unit = withContext(Dispatchers.IO) {
        Files.deleteIfExists(file)
    }

    override suspend fun destage(from: Path, to: Path): Unit = withContext(Dispatchers.IO) {
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
    }

    private val temporaryFilePermissions = "rw-------"

    private val temporaryFileAttributes =
        PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString(
                temporaryFilePermissions
            )
        )
}
