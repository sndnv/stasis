package stasis.client_android.lib.ops.exceptions

import java.nio.file.Path

data class EntityProcessingFailure(val entity: Path, override val cause: Throwable) : Exception(cause)
