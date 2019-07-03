package stasis.identity.model.tokens

sealed trait TokenType

object TokenType {
  case object Bearer extends TokenType
}
