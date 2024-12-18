package stasis.server.service.bootstrap

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.config.Config
import com.typesafe.{config => typesafe}
import org.apache.pekko.Done

import stasis.layers.service.bootstrap.BootstrapEntityProvider
import stasis.server.persistence.datasets.DatasetDefinitionStore
import stasis.shared.model.datasets.DatasetDefinition

class DatasetDefinitionBootstrapEntityProvider(store: DatasetDefinitionStore) extends BootstrapEntityProvider[DatasetDefinition] {
  override val name: String = "dataset-definitions"

  override def default: Seq[DatasetDefinition] =
    Seq.empty

  override def load(config: Config): DatasetDefinition = {
    val now = Instant.now()
    DatasetDefinition(
      id = UUID.fromString(config.getString("id")),
      info = config.getString("info"),
      device = UUID.fromString(config.getString("device")),
      redundantCopies = config.getInt("redundant-copies"),
      existingVersions = retentionFromConfig(config.getConfig("existing-versions")),
      removedVersions = retentionFromConfig(config.getConfig("removed-versions")),
      created = now,
      updated = now
    )
  }

  override def validate(entities: Seq[DatasetDefinition]): Future[Done] =
    requireNonDuplicateField(entities, _.id)

  override def create(entity: DatasetDefinition): Future[Done] =
    store.manage().put(entity)

  override def render(entity: DatasetDefinition, withPrefix: String): String =
    s"""
       |$withPrefix  dataset-definition:
       |$withPrefix    id:                ${entity.id.toString}
       |$withPrefix    info:              ${entity.info}
       |$withPrefix    device:            ${entity.device.toString}
       |$withPrefix    redundant-copies:  ${entity.redundantCopies.toString}
       |$withPrefix    existing-versions:
       |$withPrefix      policy:          ${entity.existingVersions.policy.toString}
       |$withPrefix      duration:        ${entity.existingVersions.duration.toCoarsest.toString()}
       |$withPrefix    removed-versions:
       |$withPrefix      policy:          ${entity.removedVersions.policy.toString}
       |$withPrefix      duration:        ${entity.removedVersions.duration.toCoarsest.toString()}
       |$withPrefix    created:           ${entity.created.toString}
       |$withPrefix    updated:           ${entity.updated.toString}""".stripMargin

  override def extractId(entity: DatasetDefinition): String =
    entity.id.toString

  private def retentionFromConfig(config: typesafe.Config): DatasetDefinition.Retention =
    DatasetDefinition.Retention(
      policy = config.getString("policy").toLowerCase match {
        case "at-most"     => DatasetDefinition.Retention.Policy.AtMost(config.getInt("policy-versions"))
        case "latest-only" => DatasetDefinition.Retention.Policy.LatestOnly
        case "all"         => DatasetDefinition.Retention.Policy.All
      },
      duration = config.getDuration("duration").toSeconds.seconds
    )
}
