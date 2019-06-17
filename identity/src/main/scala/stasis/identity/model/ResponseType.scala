package stasis.identity.model

sealed trait ResponseType

object ResponseType {
  case object Code extends ResponseType
  case object Token extends ResponseType
}
