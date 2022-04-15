package stasis.server.security.users

import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, RequestEntity, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.LoggerFactory
import play.api.libs.json.{Format, Json}
import stasis.core.api.PoolClient
import stasis.core.security.tls.EndpointContext
import stasis.server.security.CredentialsProvider
import stasis.server.security.exceptions.CredentialsManagementFailure
import stasis.server.security.users.UserCredentialsManager.Result
import stasis.shared.model.users.User

import scala.concurrent.{ExecutionContext, Future}

class IdentityUserCredentialsManager(
  identityUrl: String,
  identityCredentials: CredentialsProvider,
  override protected val context: Option[EndpointContext],
  override protected val requestBufferSize: Int
)(implicit override protected val system: ActorSystem[SpawnProtocol.Command])
    extends UserCredentialsManager
    with PoolClient {
  import IdentityUserCredentialsManager._

  private implicit val ec: ExecutionContext = system.executionContext

  private val log = LoggerFactory.getLogger(this.getClass.getName)

  override val id: String = identityUrl

  override def createResourceOwner(user: User, username: String, rawPassword: String): Future[Result] = {
    val request = CreateOwner(
      username = username,
      rawPassword = rawPassword,
      allowedScopes = Seq.empty,
      subject = user.id.toString
    )

    for {
      credentials <- identityCredentials.provide()
      entity <- marshalRequestEntity[CreateOwner](request)
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"$identityUrl/manage/owners",
          entity = entity
        ).addCredentials(credentials)
      )
      result <- response.asResult
    } yield {
      log.debugN("Resource owner creation for user [{}] completed with [{}]", user.id, result.toString)
      result
    }
  }

  override def activateResourceOwner(user: User.Id): Future[Result] =
    for {
      credentials <- identityCredentials.provide()
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$identityUrl/manage/owners/by-subject/${user.toString}/activate"
        ).addCredentials(credentials)
      )
      result <- response.asResult
    } yield {
      log.debugN("Resource owner activation for user [{}] completed with [{}]", user, result.toString)
      result
    }

  override def deactivateResourceOwner(user: User.Id): Future[Result] =
    for {
      credentials <- identityCredentials.provide()
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$identityUrl/manage/owners/by-subject/${user.toString}/deactivate"
        ).addCredentials(credentials)
      )
      result <- response.asResult
    } yield {
      log.debugN("Resource owner deactivation for user [{}] completed with [{}]", user, result.toString)
      result
    }

  override def setResourceOwnerPassword(user: User.Id, rawPassword: String): Future[Result] = {
    val request = UpdateOwnerCredentials(rawPassword = rawPassword)

    for {
      credentials <- identityCredentials.provide()
      entity <- marshalRequestEntity[UpdateOwnerCredentials](request)
      response <- offer(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"$identityUrl/manage/owners/by-subject/${user.toString}/credentials",
          entity = entity
        ).addCredentials(credentials)
      )
      result <- response.asResult
    } yield {
      log.debugN("Resource owner password update for user [{}] completed with [{}]", user, result.toString)
      result
    }
  }

  private def marshalRequestEntity[T](request: T)(implicit format: Format[T]): Future[RequestEntity] = {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    Marshal(request).to[RequestEntity]
  }
}

object IdentityUserCredentialsManager {
  import stasis.core.api.Formats.jsonConfig

  private implicit val createOwnerFormat: Format[CreateOwner] =
    Json.format[CreateOwner]

  private implicit val updateOwnerCredentialsFormat: Format[UpdateOwnerCredentials] =
    Json.format[UpdateOwnerCredentials]

  final case class CreateOwner(
    username: String,
    rawPassword: String,
    allowedScopes: Seq[String],
    subject: String
  )

  final case class UpdateOwnerCredentials(
    rawPassword: String
  )

  def apply(
    identityUrl: String,
    identityCredentials: CredentialsProvider,
    context: Option[EndpointContext],
    requestBufferSize: Int
  )(implicit system: ActorSystem[SpawnProtocol.Command]): IdentityUserCredentialsManager =
    new IdentityUserCredentialsManager(
      identityUrl = identityUrl,
      identityCredentials = identityCredentials,
      context = context,
      requestBufferSize = requestBufferSize
    )

  implicit class ExtendedHttpResponse(response: HttpResponse) {
    def asResult(implicit system: ActorSystem[SpawnProtocol.Command]): Future[Result] = {
      import system.executionContext

      response match {
        case response if response.status.isSuccess()             => Future.successful(Result.Success)
        case response if response.status == StatusCodes.Conflict => unmarshalResponseAsString(response).map(Result.Conflict.apply)
        case response if response.status == StatusCodes.NotFound => unmarshalResponseAsString(response).map(Result.NotFound.apply)
        case response                                            => unmarshalResponseFailure(response)
      }
    }

    private def unmarshalResponseAsString(
      response: HttpResponse
    )(implicit system: ActorSystem[SpawnProtocol.Command]): Future[String] =
      Unmarshal(response)
        .to[String]

    private def unmarshalResponseFailure[T](
      response: HttpResponse
    )(implicit system: ActorSystem[SpawnProtocol.Command]): Future[T] = {
      import system.executionContext

      unmarshalResponseAsString(response)
        .flatMap { responseContent =>
          Future.failed(
            CredentialsManagementFailure(
              message = s"Identity request failed with [${response.status.value}]: [$responseContent]"
            )
          )
        }
    }
  }
}
