package stasis.identity.model.codes.generators

import java.security.SecureRandom

import scala.util.Random

import stasis.identity.model.codes.AuthorizationCode

class DefaultAuthorizationCodeGenerator(codeSize: Int) extends AuthorizationCodeGenerator {
  override def generate(): AuthorizationCode = {
    val random = Random.javaRandomToRandom(new SecureRandom())
    AuthorizationCode(random.alphanumeric.take(codeSize).mkString(""))
  }
}

object DefaultAuthorizationCodeGenerator {
  def apply(codeSize: Int): DefaultAuthorizationCodeGenerator =
    new DefaultAuthorizationCodeGenerator(codeSize = codeSize)
}
