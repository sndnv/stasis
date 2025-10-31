package stasis.server.service.components

import scala.concurrent.duration._

import com.typesafe.config.Config
import io.github.sndnv.layers.events.DefaultEventCollector
import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.service.components.Component
import io.github.sndnv.layers.service.components.ComponentLoader
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

object EventsComponentLoader extends ComponentLoader.Optional[EventCollector, DefaultComponentContext] {
  override val name: String = "events"
  override val component: Option[String] = Some("collector")

  override protected def noop(): Component[EventCollector] =
    Component.withoutConfig(EventCollector.NoOp)

  override protected def default(
    config: Config
  )(implicit context: ComponentLoader.Context[DefaultComponentContext]): Component[EventCollector] = {
    implicit val (system, timeout, _) = context.value.components

    new DefaultEventCollectorComponent(
      config = DefaultEventCollector.Config(
        subscriberBufferSize = config.getInt("default.subscriber-buffer-size"),
        quietPeriod = config.getDuration("default.quiet-period").toMillis.millis
      )
    )
  }

  private class DefaultEventCollectorComponent(
    config: DefaultEventCollector.Config
  )(implicit system: ActorSystem[Nothing], timeout: Timeout)
      extends Component[DefaultEventCollector] {
    override def renderConfig(withPrefix: String): String =
      s"""
         |$withPrefix  subscriber-buffer-size: ${config.subscriberBufferSize.toString}
         |$withPrefix  quiet-period:           ${config.quietPeriod.toCoarsest.toString}""".stripMargin

    override val component: DefaultEventCollector =
      DefaultEventCollector(
        name = "event-collector",
        config = config
      )
  }
}
