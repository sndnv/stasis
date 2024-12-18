package stasis.identity.service.bootstrap

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try

import com.typesafe.config.Config
import org.apache.pekko.Done

import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.owners.ResourceOwnerStore
import stasis.layers.service.bootstrap.BootstrapEntityProvider

class ResourceOwnerBootstrapEntityProvider(store: ResourceOwnerStore)(implicit secretConfig: Secret.ResourceOwnerConfig)
    extends BootstrapEntityProvider[ResourceOwner] {
  override val name: String = "owners"

  override val default: Seq[ResourceOwner] =
    Seq.empty

  override def load(config: Config): ResourceOwner = {
    val salt = Secret.generateSalt()
    val password = config.getString("raw-password")

    require(password.nonEmpty, "Raw resource owner password cannot be empty")

    ResourceOwner.create(
      username = config.getString("username"),
      password = Secret.derive(rawSecret = password, salt),
      salt = salt,
      allowedScopes = config.getStringList("allowed-scopes").asScala.toSeq,
      active = config.getBoolean("active"),
      subject = Try(config.getString("subject")).toOption.filterNot(_.isBlank)
    )
  }

  override def validate(entities: Seq[ResourceOwner]): Future[Done] =
    requireNonDuplicateField(entities, _.username)

  override def create(entity: ResourceOwner): Future[Done] =
    store.put(entity)

  override def render(entity: ResourceOwner, withPrefix: String): String =
    s"""
       |$withPrefix  resource-owner:
       |$withPrefix    username:       ${entity.username}
       |$withPrefix    password:       ***
       |$withPrefix    salt:           ***
       |$withPrefix    allowed-scopes: ${entity.allowedScopes.mkString(", ")}
       |$withPrefix    active:         ${entity.active.toString}
       |$withPrefix    subject:        ${entity.subject.getOrElse("-")}
       |$withPrefix    created:        ${entity.created.toString}
       |$withPrefix    updated:        ${entity.updated.toString}""".stripMargin

  override def extractId(entity: ResourceOwner): String =
    entity.username
}
