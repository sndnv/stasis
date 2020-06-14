package stasis.test.specs.unit.client.service.components

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import com.typesafe.{config => typesafe}
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.analysis.Checksum
import stasis.client.compression.Gzip
import stasis.client.encryption.Aes
import stasis.client.service.components.{Base, Files}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class BaseSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Base component" should "load existing override configuration" in {
    val config = Base.loadConfigOverride(
      directory = createApplicationDirectory(
        init = dir => {
          val path = dir.config.get
          java.nio.file.Files.createDirectories(path)
          java.nio.file.Files.writeString(path.resolve(Files.ConfigOverride), "{\"key\": 42}")
        }
      )
    )

    config.getInt("key") should be(42)
  }

  it should "not load missing override configuration" in {
    val config = Base.loadConfigOverride(directory = createApplicationDirectory(init = _ => ()))

    config should be(typesafe.ConfigFactory.empty())
  }

  it should "create itself from config" in {
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

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
