package stasis.client.ops.exceptions

import stasis.client.model.EntityRef

final case class EntityProcessingFailure(entity: EntityRef, cause: Throwable) extends Exception(cause)
