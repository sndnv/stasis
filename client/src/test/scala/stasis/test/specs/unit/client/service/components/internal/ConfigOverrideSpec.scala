package stasis.test.specs.unit.client.service.components.internal

import com.typesafe.{config => typesafe}
import stasis.client.service.components.Files
import stasis.client.service.components.internal.ConfigOverride
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class ConfigOverrideSpec extends AsyncUnitSpec with ResourceHelpers {
  "ConfigOverride" should "load existing override configuration" in {
    val config = ConfigOverride.load(
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
    val config = ConfigOverride.load(directory = createApplicationDirectory(init = _ => ()))

    config should be(typesafe.ConfigFactory.empty())
  }

  it should "require and load existing override configuration" in {
    ConfigOverride
      .require(
        directory = createApplicationDirectory(
          init = dir => {
            val path = dir.config.get
            java.nio.file.Files.createDirectories(path)
            java.nio.file.Files.writeString(path.resolve(Files.ConfigOverride), "{\"key\": 42}")
          }
        )
      )
      .map { config =>
        config.getInt("key") should be(42)
      }
  }

  it should "fail when existing override configuration is required but missing" in {
    ConfigOverride
      .require(directory = createApplicationDirectory(init = _ => ()))
      .failed
      .map { e =>
        e.getMessage should startWith(s"File [${Files.ConfigOverride}] not found")
      }
  }
}
