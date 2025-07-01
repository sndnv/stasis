package stasis.server.service.bootstrap

import java.util.UUID

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import io.github.sndnv.layers.testing.UnitSpec
import stasis.server.persistence.datasets.MockDatasetDefinitionStore
import stasis.shared.model.datasets.DatasetDefinition
import stasis.test.specs.unit.shared.model.Generators

class DatasetDefinitionBootstrapEntityProviderSpec extends UnitSpec {
  "An DatasetDefinitionBootstrapEntityProvider" should "provide its name and default entities" in {
    val provider = new DatasetDefinitionBootstrapEntityProvider(MockDatasetDefinitionStore())

    provider.name should be("dataset-definitions")

    provider.default should be(empty)
  }

  it should "support loading entities from config" in {
    val provider = new DatasetDefinitionBootstrapEntityProvider(MockDatasetDefinitionStore())

    val expectedDeviceId = UUID.fromString("9b47ab81-c472-40e6-834e-6ede83f8893b")

    bootstrapConfig.getConfigList("dataset-definitions").asScala.map(provider.load).toList match {
      case definition1 :: definition2 :: Nil =>
        definition1.device should be(expectedDeviceId)
        definition1.redundantCopies should be(2)
        definition1.existingVersions should be(
          DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.AtMost(versions = 5),
            duration = 7.days
          )
        )
        definition1.removedVersions should be(
          DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.LatestOnly,
            duration = 0.days
          )
        )

        definition2.device should be(expectedDeviceId)
        definition2.redundantCopies should be(1)
        definition2.existingVersions should be(
          DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.All,
            duration = 7.days
          )
        )
        definition2.removedVersions should be(
          DatasetDefinition.Retention(
            policy = DatasetDefinition.Retention.Policy.All,
            duration = 1.day
          )
        )

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support validating entities" in {
    val provider = new DatasetDefinitionBootstrapEntityProvider(MockDatasetDefinitionStore())

    val validDefinitions = Seq(
      Generators.generateDefinition.copy(id = DatasetDefinition.generateId()),
      Generators.generateDefinition.copy(id = DatasetDefinition.generateId()),
      Generators.generateDefinition.copy(id = DatasetDefinition.generateId())
    )

    val sharedId1 = DatasetDefinition.generateId()
    val sharedId2 = DatasetDefinition.generateId()

    val invalidDefinitions = Seq(
      Generators.generateDefinition.copy(id = sharedId1),
      Generators.generateDefinition.copy(id = sharedId1),
      Generators.generateDefinition.copy(id = sharedId2),
      Generators.generateDefinition.copy(id = sharedId2)
    )

    noException should be thrownBy provider.validate(validDefinitions).await

    val e = provider.validate(invalidDefinitions).failed.await

    e.getMessage should (be(s"Duplicate values [$sharedId1,$sharedId2] found for field [id] in [DatasetDefinition]") or be(
      s"Duplicate values [$sharedId2,$sharedId1] found for field [id] in [DatasetDefinition]"
    ))
  }

  it should "support creating entities" in {
    val store = MockDatasetDefinitionStore()
    val provider = new DatasetDefinitionBootstrapEntityProvider(store)

    for {
      existingBefore <- store.view().list()
      _ <- provider.create(Generators.generateDefinition)
      existingAfter <- store.view().list()
    } yield {
      existingBefore should be(empty)
      existingAfter should not be empty
    }
  }

  it should "support rendering entities" in {
    val provider = new DatasetDefinitionBootstrapEntityProvider(MockDatasetDefinitionStore())

    val definition = Generators.generateDefinition

    provider.render(definition, withPrefix = "") should be(
      s"""
         |  dataset-definition:
         |    id:                ${definition.id}
         |    info:              ${definition.info}
         |    device:            ${definition.device}
         |    redundant-copies:  ${definition.redundantCopies}
         |    existing-versions:
         |      policy:          ${definition.existingVersions.policy}
         |      duration:        ${definition.existingVersions.duration.toCoarsest}
         |    removed-versions:
         |      policy:          ${definition.removedVersions.policy}
         |      duration:        ${definition.removedVersions.duration.toCoarsest}
         |    created:           ${definition.created.toString}
         |    updated:           ${definition.updated.toString}""".stripMargin
    )
  }

  it should "support extracting entity IDs" in {
    val provider = new DatasetDefinitionBootstrapEntityProvider(MockDatasetDefinitionStore())

    val definition = Generators.generateDefinition

    provider.extractId(definition) should be(definition.id.toString)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DatasetDefinitionBootstrapEntityProviderSpec"
  )

  private val bootstrapConfig = ConfigFactory.load("bootstrap-unit.conf").getConfig("bootstrap")
}
