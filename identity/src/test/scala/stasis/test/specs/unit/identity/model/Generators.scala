package stasis.test.specs.unit.identity.model

import java.util.concurrent.ThreadLocalRandom

import org.apache.pekko.util.ByteString
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.RefreshToken
import stasis.test.Generators._

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

  def generateApiId(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Api.Id =
    generateString(withSize = Defaults.Apis.IdSize)

  def generateApi(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Api =
    Api(id = generateApiId)

  def generateClient(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Client =
    Client(
      id = Client.generateId(),
      redirectUri = generateUri,
      tokenExpiration = generateFiniteDuration,
      secret = generateSecret(Defaults.Clients.SecretSize),
      salt = generateString(Defaults.Clients.SaltSize),
      active = true,
      subject = if (rnd.nextBoolean()) Some(generateString(withSize = 10)) else None
    )

  def generateAuthorizationCode(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): AuthorizationCode =
    AuthorizationCode(value = generateString(withSize = Defaults.Codes.CodeSize))

  def generateResourceOwner(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): ResourceOwner =
    ResourceOwner(
      username = generateString(withSize = Defaults.Owners.UsernameSize),
      password = generateSecret(withSize = Defaults.Owners.SecretSize),
      salt = generateString(withSize = Defaults.Owners.SaltSize),
      allowedScopes = generateSeq(min = 1, g = generateString(withSize = 10)),
      active = true,
      subject = if (rnd.nextBoolean()) Some(generateString(withSize = 10)) else None
    )

  def generateSecret(withSize: Int)(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Secret =
    Secret(ByteString(generateString(withSize)))

  def generateRefreshToken(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): RefreshToken =
    RefreshToken(value = generateString(withSize = Defaults.Tokens.TokenSize))
}
