package stasis.server.service.actions

import io.github.sndnv.layers.testing.UnitSpec
import org.mockito.scalatest.AsyncMockitoSugar
import org.slf4j.Logger

import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence

class ActionDefinitionProviderSpec extends UnitSpec with AsyncMockitoSugar {
  "A Default ActionDefinitionProvider" should "load all available and configured actions" in {
    implicit val logger: Logger = mock[Logger]

    val provider = new ActionDefinitionProvider.Default(
      core = mock[CorePersistence],
      server = mock[ServerPersistence],
      actions = Seq(
        TestActionWithEvent.Factory,
        TestActionWithSchedule.Factory
      ),
      config = com.typesafe.config.ConfigFactory.load("actions-unit")
    )

    provider.definitions.sortBy(_.action.name).toList match {
      case action1 :: action2 :: Nil =>
        action1.action.name should be("TestActionWithEvent")
        action1.trigger.description should be("event-name=test_event")

        action2.action.name should be("TestActionWithSchedule")
        action2.trigger.description should be("schedule-start=00:00,schedule-interval=1 hour")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "not load actions configured with the wrong triggers" in {
    implicit val logger: Logger = mock[Logger]

    val provider = new ActionDefinitionProvider.Default(
      core = mock[CorePersistence],
      server = mock[ServerPersistence],
      actions = Seq(
        TestActionWithEvent.Factory,
        TestActionWithSchedule.Factory
      ),
      config = com.typesafe.config.ConfigFactory.load("actions-invalid")
    )

    verify(logger).error(
      "Failed to create configured action [{}]; a schedule-based trigger is required but is not configured",
      "TestActionWithSchedule"
    )

    provider.definitions should be(empty)
  }

  it should "not load configured actions that do not exist" in {
    implicit val logger: Logger = mock[Logger]

    val provider = new ActionDefinitionProvider.Default(
      core = mock[CorePersistence],
      server = mock[ServerPersistence],
      actions = Seq(
        TestActionWithEvent.Factory,
        TestActionWithSchedule.Factory
      ),
      config = com.typesafe.config.ConfigFactory.load("actions-missing")
    )

    verify(logger).error(
      "Failed to create configured action [{}]; no such action exists",
      "MissingAction"
    )

    provider.definitions should be(empty)
  }

  it should "handle empty configurations" in {
    implicit val logger: Logger = mock[Logger]

    val provider = new ActionDefinitionProvider.Default(
      core = mock[CorePersistence],
      server = mock[ServerPersistence],
      actions = Seq(
        TestActionWithEvent.Factory,
        TestActionWithSchedule.Factory
      ),
      config = com.typesafe.config.ConfigFactory.load("actions-missing-file") // actions file does not exist
    )

    provider.definitions should be(empty)
  }
}
