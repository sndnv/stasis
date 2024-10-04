package stasis.identity.model.tokens.generators

import java.security.SecureRandom

import scala.util.Random

import stasis.identity.model.tokens.RefreshToken

class RandomRefreshTokenGenerator(tokenSize: Int) extends RefreshTokenGenerator {
  override def generate(): RefreshToken = {
    val random = Random.javaRandomToRandom(new SecureRandom())
    RefreshToken(random.alphanumeric.take(tokenSize).mkString(""))
  }
}

object RandomRefreshTokenGenerator {
  def apply(tokenSize: Int): RandomRefreshTokenGenerator =
    new RandomRefreshTokenGenerator(tokenSize = tokenSize)
}
