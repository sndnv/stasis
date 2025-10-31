package stasis.server.service.actions

import scala.concurrent.Future

import io.github.sndnv.layers.events.Event
import io.github.sndnv.layers.service.actions.Action

import stasis.server.service.CorePersistence
import stasis.server.service.ServerPersistence

class TestActionWithEvent(successful: Boolean) extends Action.WithEvent {
  override val trigger: String = "test_event"

  override def run(event: Event): Future[Option[Event.Lazy]] =
    if (successful) {
      Future.successful(Some(() => Event(name = "test_action_with_event_complete")))
    } else {
      Future.failed(new RuntimeException("Test failure"))
    }
}

object TestActionWithEvent {
  object Factory extends ActionFactory[TestActionWithEvent] {
    override val actionName: String = classOf[TestActionWithEvent].getSimpleName

    override def create(
      core: CorePersistence,
      server: ServerPersistence,
      config: com.typesafe.config.Config
    ): TestActionWithEvent =
      new TestActionWithEvent(successful = true)
  }

  def apply(successful: Boolean): TestActionWithEvent = new TestActionWithEvent(successful)
}
