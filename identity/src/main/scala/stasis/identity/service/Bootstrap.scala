package stasis.identity.service

import java.io.File
import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

import com.typesafe.config.ConfigFactory
import com.typesafe.{config => typesafe}
import org.apache.pekko.Done
import org.slf4j.Logger

import stasis.identity.model.Seconds
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret

object Bootstrap {
  final case class Entities(
    apis: Seq[Api],
    clients: Seq[Client],
    owners: Seq[ResourceOwner]
  )

  def run(
    bootstrapConfig: typesafe.Config,
    persistence: Persistence
  )(implicit
    ec: ExecutionContext,
    log: Logger,
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
        .resolve()
        .getConfig("bootstrap")

      val entities = Entities(
        apis = config.getConfigList("apis").asScala.map(apiFromConfig).toSeq,
        clients = config.getConfigList("clients").asScala.map(clientFromConfig).toSeq,
        owners = config.getConfigList("owners").asScala.map(ownerFromConfig).toSeq
      )

      run(entities, persistence)
    } else {
      persistence.migrate().map { migrationResult =>
        log.debug("Executed [{}] out of [{}] migrations", migrationResult.executed, migrationResult.found)
        Done
      }
    }
  }

  def run(
    entities: Entities,
    persistence: Persistence
  )(implicit ec: ExecutionContext, log: Logger): Future[Done] = {
    val identityApi: Api = Api.create(id = Api.ManageIdentity)

    for {
      _ <- persistence.init()
      migrationResult <- persistence.migrate()
      _ <- logged(persistence.apis.put, identityApi)
      _ <- Future.sequence(entities.apis.map(api => logged(persistence.apis.put, api)))
      _ <- Future.sequence(entities.clients.map(client => logged(persistence.clients.put, client)))
      _ <- Future.sequence(entities.owners.map(owner => logged(persistence.resourceOwners.put, owner)))
    } yield {
      log.debug("Executed [{}] out of [{}] migrations", migrationResult.executed, migrationResult.found)
      Done
    }
  }

  private def apiFromConfig(
    config: typesafe.Config
  ): Api = {
    val now = Instant.now()
    Api(id = config.getString("id"), created = now, updated = now)
  }

  private def clientFromConfig(
    config: typesafe.Config
  )(implicit secretConfig: Secret.ClientConfig): Client = {
    val salt = Secret.generateSalt()

    Client.create(
      id = Try(config.getString("id"))
        .flatMap(id => Try(java.util.UUID.fromString(id)))
        .getOrElse(Client.generateId()),
      redirectUri = config.getString("redirect-uri"),
      tokenExpiration = Seconds(config.getDuration("token-expiration").getSeconds),
      secret = Secret.derive(rawSecret = config.getString("raw-secret"), salt),
      salt = salt,
      active = config.getBoolean("active"),
      subject = Try(config.getString("subject")).toOption
    )
  }

  private def ownerFromConfig(
    config: typesafe.Config
  )(implicit secretConfig: Secret.ResourceOwnerConfig): ResourceOwner = {
    val salt = Secret.generateSalt()

    ResourceOwner.create(
      username = config.getString("username"),
      password = Secret.derive(rawSecret = config.getString("raw-password"), salt),
      salt = salt,
      allowedScopes = config.getStringList("allowed-scopes").asScala.toSeq,
      active = config.getBoolean("active"),
      subject = Try(config.getString("subject")).toOption
    )
  }

  private def logged[T](
    put: T => Future[Done],
    entity: T
  )(implicit ec: ExecutionContext, log: Logger): Future[Done] =
    put(entity)
      .map { result =>
        entity match {
          case api: Api             => log.info("API [{}] added", api.id)
          case client: Client       => log.info("Client [{}] added", client.id)
          case owner: ResourceOwner => log.info("Resource owner [{}] added", owner.username)
        }

        result
      }
      .recoverWith { case NonFatal(e) =>
        log.error(
          "Failed to add entity [{}]: [{} - {}]",
          entity.getClass.getName,
          e.getClass.getSimpleName,
          e.getMessage
        )
        Future.failed(e)
      }
}
