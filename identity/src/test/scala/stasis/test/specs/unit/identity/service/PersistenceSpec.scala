package stasis.test.specs.unit.identity.service

import java.time.Instant

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import com.typesafe.config.Config
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.tokens.StoredRefreshToken
import stasis.identity.service.Persistence
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

class PersistenceSpec extends AsyncUnitSpec {
  "Persistence" should "setup service data stores based on config" in {
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
      owner = expectedOwner,
      scope = None,
      expiration = Instant.now().plusSeconds(42)
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
      actualToken <- persistence.refreshTokens.get(expectedClient.id)
      _ <- persistence.authorizationCodes.put(expectedCode)
      actualCode <- persistence.authorizationCodes.get(expectedCode.code)
      _ <- persistence.drop()
    } yield {
      actualApi should be(Some(expectedApi))
      actualClient should be(Some(expectedClient))
      actualOwner should be(Some(expectedOwner))
      actualToken should be(Some(expectedToken.copy(expiration = actualToken.map(_.expiration).getOrElse(Instant.MIN))))
      actualCode should be(Some(expectedCode))
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "PersistenceSpec"
  )

  private val config: Config = system.settings.config.getConfig("stasis.test.identity")
}
