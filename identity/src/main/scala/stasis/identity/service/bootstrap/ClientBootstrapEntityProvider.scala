package stasis.identity.service.bootstrap

import scala.concurrent.Future
import scala.util.Try

import com.typesafe.config.Config
import org.apache.pekko.Done

import stasis.identity.model.Seconds
import stasis.identity.model.clients.Client
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.clients.ClientStore
import stasis.layers.service.bootstrap.BootstrapEntityProvider

class ClientBootstrapEntityProvider(store: ClientStore)(implicit secretConfig: Secret.ClientConfig)
    extends BootstrapEntityProvider[Client] {
  override val name: String = "clients"

  override val default: Seq[Client] =
    Seq.empty

  override def load(config: Config): Client = {
    val salt = Secret.generateSalt()
    val secret = config.getString("raw-secret")

    require(secret.nonEmpty, "Raw client secret cannot be empty")

    Client.create(
      id = Try(config.getString("id"))
        .flatMap(id => Try(java.util.UUID.fromString(id)))
        .getOrElse(Client.generateId()),
      redirectUri = config.getString("redirect-uri"),
      tokenExpiration = Seconds(config.getDuration("token-expiration").getSeconds),
      secret = Secret.derive(rawSecret = secret, salt),
      salt = salt,
      active = config.getBoolean("active"),
      subject = Try(config.getString("subject")).toOption.filterNot(_.isBlank)
    )
  }

  override def validate(entities: Seq[Client]): Future[Done] =
    requireNonDuplicateField(entities, _.id)

  override def create(entity: Client): Future[Done] =
    store.put(entity)

  override def render(entity: Client, withPrefix: String): String =
    s"""
       |$withPrefix  client:
       |$withPrefix    id:               ${entity.id.toString}
       |$withPrefix    redirect-uri:     ${entity.redirectUri}
       |$withPrefix    token-expiration: ${entity.tokenExpiration.value.toString}
       |$withPrefix    secret:           ***
       |$withPrefix    salt:             ***
       |$withPrefix    active:           ${entity.active.toString}
       |$withPrefix    subject:          ${entity.subject.getOrElse("-")}
       |$withPrefix    created:          ${entity.created.toString}
       |$withPrefix    updated:          ${entity.updated.toString}""".stripMargin

  override def extractId(entity: Client): String =
    entity.id.toString
}
