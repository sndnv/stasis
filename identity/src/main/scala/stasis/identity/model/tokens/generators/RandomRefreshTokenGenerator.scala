package stasis.identity.model.tokens.generators

import java.security.SecureRandom

import stasis.identity.model.tokens.RefreshToken

import scala.util.Random

class RandomRefreshTokenGenerator(tokenSize: Int) extends RefreshTokenGenerator {
  override def generate(): RefreshToken = {
    val random = Random.javaRandomToRandom(new SecureRandom())
    RefreshToken(random.alphanumeric.take(tokenSize).mkString(""))
  }
}
