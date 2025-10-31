package stasis.server.service.actions

import java.time.Instant

import scala.concurrent.Future

import io.github.sndnv.layers.events.Event
import io.github.sndnv.layers.service.actions.Action

import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence

class TestActionWithSchedule(successful: Boolean) extends Action.WithSchedule {
  override def run(target: Instant, actual: Instant): Future[Option[Event.Lazy]] =
    if (successful) {
      Future.successful(Some(() => Event(name = "test_action_with_schedule_complete")))
    } else {
      Future.failed(new RuntimeException("Test failure"))
    }
}

object TestActionWithSchedule {
  object Factory extends ActionFactory[TestActionWithSchedule] {
    override val actionName: String = classOf[TestActionWithSchedule].getSimpleName

    override def create(
      core: CorePersistence,
      server: ServerPersistence,
      config: com.typesafe.config.Config
    ): TestActionWithSchedule =
      new TestActionWithSchedule(successful = true)
  }

  def apply(successful: Boolean): TestActionWithSchedule = new TestActionWithSchedule(successful)
}
