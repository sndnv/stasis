package stasis.test.specs.unit.identity.model

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

import akka.util.ByteString
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.secrets.Secret
import scala.concurrent.duration._

import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshToken

object Generators {
  object Defaults {
    object Apis {
      final val IdSize = 32
    }

    object Clients {
      final val SecretSize = 24
      final val SaltSize = 16
    }

    object Codes {
      final val CodeSize = 64
    }

    object Owners {
      final val UsernameSize = 64
      final val SecretSize = 24
      final val SaltSize = 16
    }

    object Tokens {
      final val TokenSize = 64
    }
  }

  def generateString(
    withSize: Int
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): String = {
    val random = Random.javaRandomToRandom(rnd)
    random.alphanumeric.take(withSize).mkString("")
  }

  def generateFiniteDuration(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): FiniteDuration =
    rnd.nextLong(0, 1.day.toSeconds).seconds

  def generateApiId(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Api.Id =
    generateString(withSize = Defaults.Apis.IdSize)

  def generateUri(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): String = {
    val host = generateString(withSize = 10)
    val port = rnd.nextInt(50000, 60000)
    val endpoint = generateString(withSize = 20)
    s"http://$host:$port/$endpoint"
  }

  def generateSeq[T](
    min: Int = 0,
    max: Int = 10,
    g: => T
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Seq[T] =
    Stream.continually(g).take(rnd.nextInt(min, max))

  def generateApi(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Api =
    Api(id = generateApiId)

  def generateClient(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Client =
    Client(
      id = Client.generateId(),
      allowedScopes = generateSeq(min = 1, g = generateString(withSize = 10)),
      redirectUri = generateUri,
      tokenExpiration = generateFiniteDuration,
      secret = generateSecret(Defaults.Clients.SecretSize),
      salt = generateString(Defaults.Clients.SaltSize),
      active = true
    )

  def generateAuthorizationCode(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): AuthorizationCode =
    AuthorizationCode(value = generateString(withSize = Defaults.Codes.CodeSize))

  def generateResourceOwner(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): ResourceOwner =
    ResourceOwner(
      username = generateString(withSize = Defaults.Owners.UsernameSize),
      password = generateSecret(withSize = Defaults.Owners.SecretSize),
      salt = generateString(withSize = Defaults.Owners.SaltSize),
      allowedScopes = generateSeq(min = 1, g = generateString(withSize = 10)),
      active = true
    )

  def generateSecret(withSize: Int)(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Secret =
    Secret(ByteString(generateString(withSize)))

  def generateRefreshToken(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): RefreshToken =
    RefreshToken(value = generateString(withSize = Defaults.Tokens.TokenSize))
}
