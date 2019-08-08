package stasis.identity.service

import java.io.File

import akka.Done
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import com.typesafe.config.ConfigFactory
import com.typesafe.{config => typesafe}
import stasis.identity.model.Seconds
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

object Bootstrap {
  final case class Entities(
    apis: Seq[Api],
    clients: Seq[Client],
    owners: Seq[ResourceOwner]
  )

  def run(
    bootstrapConfig: typesafe.Config,
    persistence: Persistence
  )(
    implicit system: ActorSystem,
    log: LoggingAdapter,
    clientSecretConfig: Secret.ClientConfig,
    ownerSecretConfig: Secret.ResourceOwnerConfig
  ): Future[Done] = {
    val enabled = bootstrapConfig.getBoolean("enabled")
    val configFile = bootstrapConfig.getString("config").trim

    if (enabled && configFile.nonEmpty) {
      val config = ConfigFactory
        .parseFile(
          Option(getClass.getClassLoader.getResource(configFile))
            .map(resource => new File(resource.getFile))
            .getOrElse(new File(configFile))
        )
        .getConfig("bootstrap")

      val entities = Entities(
        apis = config.getConfigList("apis").asScala.map(apiFromConfig),
        clients = config.getConfigList("clients").asScala.map(clientFromConfig),
        owners = config.getConfigList("owners").asScala.map(ownerFromConfig)
      )

      run(entities, persistence)
    } else {
      Future.successful(Done)
    }
  }

  def run(
    entities: Bootstrap.Entities,
    persistence: Persistence
  )(implicit system: ActorSystem, log: LoggingAdapter): Future[Done] = {
    val identityApi: Api = Api(id = Api.ManageIdentity)

    implicit val ec: ExecutionContext = system.dispatcher

    for {
      _ <- persistence.init()
      _ <- logged(persistence.apis.put, identityApi)
      _ <- Future.sequence(entities.apis.map(api => logged(persistence.apis.put, api)))
      _ <- Future.sequence(entities.clients.map(client => logged(persistence.clients.put, client)))
      _ <- Future.sequence(entities.owners.map(owner => logged(persistence.resourceOwners.put, owner)))
    } yield {
      Done
    }
  }

  private def apiFromConfig(
    config: typesafe.Config
  ): Api =
    Api(id = config.getString("id"))

  private def clientFromConfig(
    config: typesafe.Config
  )(implicit secretConfig: Secret.ClientConfig): Client = {
    val salt = Secret.generateSalt()

    Client(
      id = Try(config.getString("id"))
        .flatMap(id => Try(java.util.UUID.fromString(id)))
        .getOrElse(Client.generateId()),
      redirectUri = config.getString("redirect-uri"),
      tokenExpiration = Seconds(config.getDuration("token-expiration").getSeconds),
      secret = Secret.derive(rawSecret = config.getString("raw-secret"), salt),
      salt = salt,
      active = config.getBoolean("active")
    )
  }

  private def ownerFromConfig(
    config: typesafe.Config
  )(implicit secretConfig: Secret.ResourceOwnerConfig): ResourceOwner = {
    val salt = Secret.generateSalt()

    ResourceOwner(
      username = config.getString("username"),
      password = Secret.derive(rawSecret = config.getString("raw-password"), salt),
      salt = salt,
      allowedScopes = config.getStringList("allowed-scopes").asScala,
      active = config.getBoolean("active")
    )
  }

  private def logged[T](
    put: T => Future[Done],
    entity: T
  )(implicit ec: ExecutionContext, log: LoggingAdapter): Future[Done] =
    put(entity)
      .map { result =>
        entity match {
          case api: Api             => log.info("API [{}] added", api.id)
          case client: Client       => log.info("Client [{}] added", client.id)
          case owner: ResourceOwner => log.info("Resource owner [{}]", owner.username)
        }

        result
      }
      .recoverWith {
        case NonFatal(e) =>
          log.error("Failed to add entity [{}]: [{}]", entity.getClass.getName, e.getMessage)
          Future.failed(e)
      }
}
