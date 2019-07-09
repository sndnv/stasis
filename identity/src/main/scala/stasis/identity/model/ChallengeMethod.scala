package stasis.identity.model

sealed trait ChallengeMethod

object ChallengeMethod {
  case object Plain extends ChallengeMethod
  case object S256 extends ChallengeMethod
}
