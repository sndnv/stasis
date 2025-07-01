package stasis.test.specs.unit.client.service

import java.util.UUID

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.{config => typesafe}
import play.api.libs.json.Json

import stasis.client.service.ApplicationTemplates
import stasis.client.service.components
import stasis.core.routing.Node
import io.github.sndnv.layers.security.tls.EndpointContext
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

class ApplicationTemplatesSpec extends UnitSpec {
  "ApplicationTemplates" should "support loading templates" in {
    ApplicationTemplates() match {
      case Success(templates) =>
        templates.config.content should not be empty
        templates.rules.content should not be empty
        templates.rules.user should not be empty

      case Failure(e) =>
        fail(e.toString)
    }
  }

  they should "fail loading template files if they cannot be found" in {
    ApplicationTemplates.load(templateFile = "missing") match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("Template resource [templates/missing] not found")
    }
  }

  they should "support merging expanded and additional config" in {
    val expanded = """a {b {c: 0}}"""
    val additionalConfig = Json.obj(
      "a" -> Json.obj(
        "d" -> Json.toJson(42),
        "e" -> Json.toJson("f")
      ),
      "f" -> Json.toJson(false)
    )

    ApplicationTemplates.merge(expanded, additionalConfig) match {
      case Success(merged) =>
        merged should not be empty

        Try(typesafe.ConfigFactory.parseString(merged)) match {
          case Success(parsed) =>
            parsed should not be empty
            parsed.getInt("a.b.c") should be(0)
            parsed.getInt("a.d") should be(42)
            parsed.getString("a.e") should be("f")
            parsed.getBoolean("f") should be(false)

          case Failure(e) =>
            fail(e.getMessage)
        }

      case Failure(e) =>
        fail(e.getMessage)
    }
  }

  they should "support expanding templates" in {
    ApplicationTemplates.expand(
      content = "$${PARAM_1} || $${PARAM_2}",
      params = Map("PARAM_1" -> "value 1", "PARAM_2" -> "value 2")
    ) match {
      case Success(expanded) => expanded should be("value 1 || value 2")
      case Failure(e)        => fail(e.toString)
    }
  }

  they should "fail expanding templates if unexpanded placeholders remain" in {
    ApplicationTemplates.expand(
      content = "$${PARAM_1}",
      params = Map("PARAM_1" -> "value 1", "PARAM_2" -> "value 2")
    ) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("Placeholder [PARAM_2] not present in template")
    }
  }

  they should "fail expanding templates if placeholder is not present" in {
    ApplicationTemplates.expand(
      content = "$${PARAM_1} || $${PARAM_2}",
      params = Map("PARAM_1" -> "value 1")
    ) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("Unexpanded placeholders found: [$${PARAM_2}]")
    }
  }

  they should "support expanding config templates" in {
    ApplicationTemplates() match {
      case Success(templates) =>
        templates.config.expand(
          bootstrapParams = bootstrapParams,
          trustStoreParams = trustStoreParams,
          keyStoreParameters = keyStoreParams
        ) match {
          case Success(expanded) =>
            expanded should not be empty

            Try(typesafe.ConfigFactory.parseString(expanded)) match {
              case Success(parsed) => parsed should not be empty
              case Failure(e)      => fail(e.getMessage)
            }

          case Failure(e) =>
            fail(e.toString)
        }

      case Failure(e) =>
        fail(e.toString)
    }
  }

  they should "support expanding rules templates" in {
    ApplicationTemplates() match {
      case Success(templates) =>
        templates.rules.expand() match {
          case Success(expanded) => expanded should not be empty
          case Failure(e)        => fail(e.toString)
        }

      case Failure(e) =>
        fail(e.toString)
    }
  }

  they should "support retrieving current user" in {
    val expectedUser = "test-user"

    ApplicationTemplates.getCurrentUser(property = expectedUser) match {
      case Success(actualUser) => actualUser should be(expectedUser)
      case Failure(e)          => fail(e.getMessage)
    }
  }

  they should "handle missing current user property" in {
    ApplicationTemplates.getCurrentUser(property = None.orNull) match {
      case Success(other) => fail(s"Unexpected result received: [$other]")
      case Failure(e)     => e.getMessage should be("Current user not available")
    }
  }

  they should "support retrieving rules for MacOS systems" in {
    val os = "Mac OS"

    ApplicationTemplates.getRulesTemplateFile(property = os) match {
      case Success(rules) => rules should be(components.Files.Templates.RulesMacOS)
      case Failure(e)     => fail(e.getMessage)
    }
  }

  they should "support retrieving rules for Linux systems by default" in {
    val os = "any"

    ApplicationTemplates.getRulesTemplateFile(property = os) match {
      case Success(rules) => rules should be(components.Files.Templates.RulesLinux)
      case Failure(e)     => fail(e.getMessage)
    }
  }

  they should "not support retrieving rules for Windows systems" in {
    val os = "Windows"

    ApplicationTemplates.getRulesTemplateFile(property = os) match {
      case Success(other) => fail(s"Unexpected result received: [$other]")
      case Failure(e)     => e.getMessage should be(s"Unsupported operating system found: [$os]")
    }
  }

  private val bootstrapParams = DeviceBootstrapParameters(
    authentication = DeviceBootstrapParameters.Authentication(
      tokenEndpoint = "http://localhost:1234",
      clientId = UUID.randomUUID().toString,
      clientSecret = "test-secret",
      useQueryString = true,
      scopes = DeviceBootstrapParameters.Scopes(
        api = "urn:stasis:identity:audience:server-api",
        core = s"urn:stasis:identity:audience:${Node.generateId().toString}"
      ),
      context = EndpointContext.Encoded.disabled()
    ),
    serverApi = DeviceBootstrapParameters.ServerApi(
      url = "http://localhost:5678",
      user = User.generateId().toString,
      userSalt = "test-salt",
      device = Device.generateId().toString,
      context = EndpointContext.Encoded.disabled()
    ),
    serverCore = DeviceBootstrapParameters.ServerCore(
      address = "http://localhost:5679",
      nodeId = Node.generateId().toString,
      context = EndpointContext.Encoded.disabled()
    ),
    secrets = Fixtures.Secrets.DefaultConfig,
    additionalConfig = Json.obj(
      "a" -> Json.obj(
        "b" -> Json.obj(
          "c" -> Json.toJson(5),
          "d" -> Json.toJson("e")
        ),
        "f" -> Json.toJson(false)
      ),
      "stasis" -> Json.obj(
        "client" -> Json.obj(
          "test-key" -> "test-value"
        )
      )
    )
  )

  private val trustStoreParams = ApplicationTemplates.TrustStoreParameters(
    authenticationFile = "",
    authenticationPassword = "",
    serverApiFile = "",
    serverApiPassword = "",
    serverCoreFile = "",
    serverCorePassword = ""
  )

  private val keyStoreParams = ApplicationTemplates.KeyStoreParameters(
    clientApiHttpFile = "",
    clientApiHttpType = "",
    clientApiHttPassword = "",
    clientApiInitFile = "",
    clientApiInitType = "",
    clientApiInitPassword = ""
  )
}
