package stasis.identity.service

import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.duration._

import com.typesafe.config.Config
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.model.Generators
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.tokens.StoredRefreshToken
import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.persistence.migration.MigrationResult
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext

class PersistenceSpec extends UnitSpec {
  "Persistence" should "setup service data stores based on config" in withRetry {
    val persistence = new Persistence(
      persistenceConfig = config.getConfig("persistence"),
      authorizationCodeExpiration = 3.seconds,
      refreshTokenExpiration = 3.seconds
    )

    val expectedApi = Generators.generateApi
    val expectedClient = Generators.generateClient
    val expectedOwner = Generators.generateResourceOwner

    val expectedToken = StoredRefreshToken(
      token = Generators.generateRefreshToken,
      client = expectedClient.id,
      owner = expectedOwner.username,
      scope = None,
      expiration = Instant.now().plusSeconds(42),
      created = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    )

    val expectedCode = StoredAuthorizationCode(
      code = Generators.generateAuthorizationCode,
      client = expectedClient.id,
      owner = expectedOwner,
      scope = None
    )

    for {
      _ <- persistence.init()
      _ <- persistence.apis.put(expectedApi)
      actualApi <- persistence.apis.get(expectedApi.id)
      _ <- persistence.clients.put(expectedClient)
      actualClient <- persistence.clients.get(expectedClient.id)
      _ <- persistence.resourceOwners.put(expectedOwner)
      actualOwner <- persistence.resourceOwners.get(expectedOwner.username)
      _ <- persistence.refreshTokens.put(expectedClient.id, expectedToken.token, expectedOwner, scope = None)
      actualToken <- persistence.refreshTokens.get(expectedToken.token)
      _ <- persistence.authorizationCodes.put(expectedCode)
      actualCode <- persistence.authorizationCodes.get(expectedCode.code)
      _ <- persistence.drop()
    } yield {
      actualApi should be(Some(expectedApi))
      actualClient should be(Some(expectedClient))
      actualOwner should be(Some(expectedOwner))
      actualCode should be(Some(expectedCode))

      actualToken match {
        case Some(token) =>
          token.copy(created = token.created.truncatedTo(ChronoUnit.SECONDS)) should be(
            expectedToken.copy(expiration = actualToken.map(_.expiration).getOrElse(Instant.MIN))
          )

        case None =>
          fail("Expected token but none was found")
      }
    }
  }

  it should "support running data store migrations" in {
    val persistence = new Persistence(
      persistenceConfig = config.getConfig("persistence"),
      authorizationCodeExpiration = 3.seconds,
      refreshTokenExpiration = 3.seconds
    )

    for {
      result <- persistence.migrate()
    } yield {
      result should be(MigrationResult(found = 5, executed = 0))
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    "PersistenceSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private val config: Config = system.settings.config.getConfig("stasis.test.identity")
}
