package stasis.client.ops.exceptions

import java.nio.file.Path

final case class EntityProcessingFailure(entity: Path, cause: Throwable) extends Exception(cause)
