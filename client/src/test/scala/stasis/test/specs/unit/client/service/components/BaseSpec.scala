package stasis.test.specs.unit.client.service.components

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.analysis.Checksum
import stasis.client.compression.Gzip
import stasis.client.encryption.Aes
import stasis.client.service.components.Base
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class BaseSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Base component" should "create itself from config" in {
    Base(applicationDirectory = createApplicationDirectory(init = _ => ()), terminate = () => ()).map { base =>
      base.checksum should be(Checksum.CRC32)
      base.compression should be(Gzip)
      base.encryption should be(Aes)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "BaseSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
