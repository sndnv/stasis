package stasis.identity.model.codes.generators

import java.security.SecureRandom

import stasis.identity.model.codes.AuthorizationCode

import scala.util.Random

class DefaultAuthorizationCodeGenerator(codeSize: Int) extends AuthorizationCodeGenerator {
  override def generate(): AuthorizationCode = {
    val random = Random.javaRandomToRandom(new SecureRandom())
    AuthorizationCode(random.alphanumeric.take(codeSize).mkString(""))
  }
}
