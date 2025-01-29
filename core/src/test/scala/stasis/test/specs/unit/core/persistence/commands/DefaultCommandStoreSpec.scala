package stasis.test.specs.unit.core.persistence.commands

import java.time.Instant
import java.util.UUID

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.persistence.commands.DefaultCommandStore
import stasis.layers.UnitSpec
import stasis.layers.persistence.SlickTestDatabase
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DefaultCommandStoreSpec extends UnitSpec with SlickTestDatabase {
  "A DefaultCommandStore" should "add, retrieve and delete commands" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultCommandStore(name = "TEST_COMMANDS", profile = profile, database = database)
      val expectedMessage = Generators.generateCommand.copy(sequenceId = 1)

      for {
        _ <- store.init()
        _ <- store.put(expectedMessage)
        someMessages <- store.list()
        _ <- store.delete(expectedMessage.sequenceId)
        noMessages <- store.list()
        _ <- store.drop()
      } yield {
        someMessages should be(Seq(expectedMessage))
        noMessages should be(Seq.empty)
      }
    }
  }

  it should "support retrieving commands for a specific entity" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultCommandStore(name = "TEST_COMMANDS", profile = profile, database = database)

      val entity1 = UUID.randomUUID()
      val entity2 = UUID.randomUUID()

      val message1 = Generators.generateCommand.copy(sequenceId = 1, target = Some(entity1))
      val message2 = Generators.generateCommand.copy(sequenceId = 2, target = Some(entity1))
      val message3 = Generators.generateCommand.copy(sequenceId = 3, target = None)
      val message4 = Generators.generateCommand.copy(sequenceId = 4, target = Some(entity2))

      for {
        _ <- store.init()
        _ <- store.put(message1)
        _ <- store.put(message2)
        _ <- store.put(message3)
        _ <- store.put(message4)
        allMessages <- store.list()
        messagesForEntity1 <- store.list(forEntity = entity1)
        messagesForEntity2 <- store.list(forEntity = entity2)
        _ <- store.drop()
      } yield {
        allMessages should be(Seq(message1, message2, message3, message4))
        messagesForEntity1 should be(Seq(message1, message2, message3))
        messagesForEntity2 should be(Seq(message3, message4))
      }
    }
  }

  it should "support retrieving unprocessed commands for a specific entity" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultCommandStore(name = "TEST_COMMANDS", profile = profile, database = database)

      val entity = UUID.randomUUID()

      val message1 = Generators.generateCommand.copy(sequenceId = 1, target = Some(entity))
      val message2 = Generators.generateCommand.copy(sequenceId = 2, target = Some(entity))
      val message3 = Generators.generateCommand.copy(sequenceId = 3, target = None)
      val message4 = Generators.generateCommand.copy(sequenceId = 4, target = Some(entity))
      val message5 = Generators.generateCommand.copy(sequenceId = 5, target = Some(entity))

      for {
        _ <- store.init()
        _ <- store.put(message1)
        _ <- store.put(message2)
        _ <- store.put(message3)
        _ <- store.put(message4)
        _ <- store.put(message5)
        allMessages <- store.list()
        remainingMessagesForEntity <- store.list(forEntity = entity, lastSequenceId = 3)
        _ <- store.drop()
      } yield {
        allMessages should be(Seq(message1, message2, message3, message4, message5))
        remainingMessagesForEntity should be(Seq(message4, message5))
      }
    }
  }

  it should "support truncating old commands" in withRetry {
    withStore { (profile, database) =>
      val store = new DefaultCommandStore(name = "TEST_COMMANDS", profile = profile, database = database)

      val message1 = Generators.generateCommand.copy(sequenceId = 1, created = Instant.now().minusSeconds(60))
      val message2 = Generators.generateCommand.copy(sequenceId = 2, created = Instant.now().minusSeconds(40))
      val message3 = Generators.generateCommand.copy(sequenceId = 3, created = Instant.now())
      val message4 = Generators.generateCommand.copy(sequenceId = 4, created = Instant.now().plusSeconds(30))

      for {
        _ <- store.init()
        _ <- store.put(message1)
        _ <- store.put(message2)
        _ <- store.put(message3)
        _ <- store.put(message4)
        allMessages <- store.list()
        _ <- store.truncate(olderThan = Instant.now().minusSeconds(10))
        remainingMessages <- store.list()
        _ <- store.drop()
      } yield {
        allMessages should be(Seq(message1, message2, message3, message4))
        remainingMessages should be(Seq(message3, message4))
      }
    }
  }

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultCommandStoreSpec"
  )
}
