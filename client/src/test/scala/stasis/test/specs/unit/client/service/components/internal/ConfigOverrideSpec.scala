package stasis.test.specs.unit.client.service.components.internal

import java.io.FileNotFoundException

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

  it should "replace string config values" in {
    val configA =
      """
        |a {
        |  b {
        |    c {
        |      d = "xyz"
        |    }
        |  }
        |}""".stripMargin

    val configB = """a.b.c.d = "123""""

    val configC =
      """
        |a {
        |b {
        |c {
        |d   =   ""
        |}
        |}
        |}""".stripMargin

    val configD = """{a.b.c.d: "123"}"""

    val configE = """{x.y.z: 42, a.b.c.d: "123"}"""

    val path = "a.b.c.d"
    val replacement = "updated"

    typesafe.ConfigFactory
      .parseString(ConfigOverride.replaceStringConfigValue(configA, path, replacement))
      .getString(path) should be(replacement)

    typesafe.ConfigFactory
      .parseString(ConfigOverride.replaceStringConfigValue(configB, path, replacement))
      .getString(path) should be(replacement)

    typesafe.ConfigFactory
      .parseString(ConfigOverride.replaceStringConfigValue(configC, path, replacement))
      .getString(path) should be(replacement)

    typesafe.ConfigFactory
      .parseString(ConfigOverride.replaceStringConfigValue(configD, path, replacement))
      .getString(path) should be(replacement)

    typesafe.ConfigFactory
      .parseString(ConfigOverride.replaceStringConfigValue(configE, path, replacement))
      .getString(path) should be(replacement)
  }

  it should "fail to replace string config values with invalid paths" in {
    val e = intercept[IllegalArgumentException](
      ConfigOverride.replaceStringConfigValue(
        originalContent = "{}",
        path = "  ",
        replacementValue = "test"
      )
    )

    e.getMessage should be("Invalid config path provided: [  ]")
  }

  it should "update entries in override configuration" in {
    val path = "a.b.c"

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
        java.nio.file.Files.writeString(path.resolve(Files.ConfigOverride), "a { b { c = \"test\" } }")
      }
    )

    for {
      original <- ConfigOverride.require(directory = directory).map { config => config.getString(path) }
      _ = ConfigOverride.update(directory = directory, path = path, value = "updated")
      updated <- ConfigOverride.require(directory = directory).map { config => config.getString(path) }
    } yield {
      original should be("test")
      updated should be("updated")
    }
  }

  it should "fail to update entries if override configuration is missing" in {
    val path = "a.b.c"

    val directory = createApplicationDirectory(
      init = dir => {
        val path = dir.config.get
        java.nio.file.Files.createDirectories(path)
      }
    )

    val e = intercept[FileNotFoundException](ConfigOverride.update(directory = directory, path = path, value = "updated"))

    e.getMessage should startWith(s"File [${Files.ConfigOverride}] not found")
  }
}
