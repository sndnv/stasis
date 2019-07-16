package stasis.test.specs.unit.identity.service

import akka.Done
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
import org.jose4j.jwk.{JsonWebKey, JsonWebKeySet}
import org.jose4j.jws.JsonWebSignature
import org.scalatest.concurrent.Eventually
import play.api.libs.json._
import stasis.identity.api.Formats._
import stasis.identity.api.manage.requests.{CreateApi, CreateOwner, CreateRealm}
import stasis.identity.model.apis.Api
import stasis.identity.model.realms.Realm
import stasis.identity.service.Service
import stasis.test.specs.unit.identity.RouteTest

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

class ServiceSpec extends RouteTest with Eventually {
  import ServiceSpec._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  "Identity Service" should "authenticate and authorize actions" in {
    val service = new Service {}
    val serviceInterface = "localhost"
    val servicePort = 19090
    val serviceUrl = s"http://$serviceInterface:$servicePort"

    val owner = ResourceOwnerCredentials(
      username = "existing-user",
      password = "existing-user-password",
      scope = s"urn:stasis:identity:audience:${Api.ManageMaster}"
    )

    val client = ClientCredentials(
      username = eventually { getEntity(service.persistence.clients.clients).id.toString },
      password = "existing-client-secret"
    )

    val newRealm = "new-realm"
    val newApi = "new-api"
    val newUser = "new-user"
    val newUserPassword = "new-user-password"
    val newUserScope = "urn:stasis:identity:audience:new-api"

    for {
      masterAccessToken <- getAccessToken(
        serviceUrl = serviceUrl,
        realm = Realm.Master,
        owner = owner,
        client = client
      )
      _ <- createEntity(
        serviceUrl = serviceUrl,
        entities = "realms",
        realm = Realm.Master,
        request = CreateRealm(id = newRealm, refreshTokensAllowed = true),
        accessToken = masterAccessToken
      )
      _ <- createEntity(
        serviceUrl = serviceUrl,
        entities = "apis",
        realm = newRealm,
        request = CreateApi(id = newApi),
        accessToken = masterAccessToken
      )
      _ <- createEntity(
        serviceUrl = serviceUrl,
        entities = "owners",
        realm = newRealm,
        request = CreateOwner(username = newUser, rawPassword = newUserPassword, allowedScopes = Seq(newUserScope)),
        accessToken = masterAccessToken
      )
      newRealmAccessToken <- getAccessToken(
        serviceUrl = serviceUrl,
        realm = newRealm,
        owner = ResourceOwnerCredentials(username = newUser, password = newUserPassword, scope = newUserScope),
        client = client
      )
      realms <- service.persistence.realms.realms
      apis <- service.persistence.apis.apis
      owners <- service.persistence.resourceOwners.owners
      clients <- service.persistence.clients.clients
      signatureKey <- getJwk(serviceUrl)
      _ <- service.persistence.drop()
      _ = service.stop()
    } yield {
      realms.size should be(3)
      apis.size should be(3)
      owners.size should be(2)
      clients.size should be(1)

      val jws = new JsonWebSignature()
      jws.setCompactSerialization(newRealmAccessToken)
      jws.setKey(signatureKey.getKey)

      jws.verifySignature() should be(true)
    }
  }

  it should "handle bootstrap failures" in {
    val service = new Service {
      override protected def systemConfig: Config = ConfigFactory.load("application-invalid-bootstrap")
    }

    eventually {
      service.state should be(Service.State.Failed)
    }
  }

  private def getAccessToken(
    serviceUrl: String,
    realm: Realm.Id,
    owner: ResourceOwnerCredentials,
    client: ClientCredentials
  ): Future[String] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.POST,
            uri = s"$serviceUrl/oauth/$realm/token" +
              s"?grant_type=password" +
              s"&username=${owner.username}" +
              s"&password=${owner.password}" +
              s"&scope=${owner.scope}"
          ).addCredentials(BasicHttpCredentials(client.username, client.password))
        )
      entity <- response.entity.dataBytes.runFold(ByteString.empty)(_ concat _)
    } yield {
      (Json.parse(entity.utf8String).as[JsObject] \ "access_token").as[String]
    }

  private def createEntity(
    serviceUrl: String,
    entities: String,
    realm: Realm.Id,
    request: RequestEntity,
    accessToken: String
  ): Future[Done] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"$serviceUrl/manage/$realm/$entities",
          entity = request
        ).addCredentials(OAuth2BearerToken(token = accessToken))
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, _, _) => Future.successful(Done)
        case response                              => fail(s"Unexpected response received: [$response]")
      }

  private def getJwk(
    serviceUrl: String
  ): Future[JsonWebKey] =
    for {
      response <- Http()
        .singleRequest(
          request = HttpRequest(
            method = HttpMethods.GET,
            uri = s"$serviceUrl/jwks/jwks.json"
          )
        )
      entity <- response.entity.dataBytes.runFold(ByteString.empty)(_ concat _)
    } yield {
      new JsonWebKeySet(entity.utf8String).getJsonWebKeys.asScala.toList match {
        case jwk :: Nil => jwk
        case _          => fail(s"Unexpected JWKs response received: [$entity]")
      }
    }

  private def getEntity[T](list: => Future[Map[_, T]]): T =
    list.await.values.headOption match {
      case Some(entity) => entity
      case None         => fail("Existing entity expected but none was found")
    }
}

object ServiceSpec {
  private final case class ResourceOwnerCredentials(username: String, password: String, scope: String)
  private final case class ClientCredentials(username: String, password: String)
}
