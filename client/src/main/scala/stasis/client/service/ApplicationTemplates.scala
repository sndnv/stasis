package stasis.client.service

import java.io.FileNotFoundException

import com.typesafe.{config => typesafe}
import play.api.libs.json.JsObject
import stasis.shared.model.devices.DeviceBootstrapParameters

import scala.util.{Failure, Success, Try}

final case class ApplicationTemplates(
  config: ApplicationTemplates.ConfigTemplate,
  rules: ApplicationTemplates.RulesTemplate
)

object ApplicationTemplates {
  final case class ConfigTemplate(content: String) {
    def expand(
      bootstrapParams: DeviceBootstrapParameters,
      trustStoreParams: TrustStoreParameters,
      keyStoreParameters: KeyStoreParameters
    ): Try[String] =
      for {
        expanded <- ApplicationTemplates.expand(content, flatten(bootstrapParams, trustStoreParams, keyStoreParameters))
        merged <- ApplicationTemplates.merge(expanded, bootstrapParams.additionalConfig)
      } yield {
        merged
      }
  }

  final case class RulesTemplate(content: String, user: String) {
    def expand(): Try[String] =
      ApplicationTemplates.expand(content, flatten(user))
  }

  final case class TrustStoreParameters(
    authenticationFile: String,
    authenticationPassword: String,
    serverApiFile: String,
    serverApiPassword: String,
    serverCoreFile: String,
    serverCorePassword: String
  )

  final case class KeyStoreParameters(
    clientApiHttpFile: String,
    clientApiHttpType: String,
    clientApiHttPassword: String,
    clientApiInitFile: String,
    clientApiInitType: String,
    clientApiInitPassword: String
  )

  def apply(): Try[ApplicationTemplates] =
    for {
      user <- getCurrentUser(System.getProperty("user.name"))
      rulesTemplateFile <- getRulesTemplateFile(System.getProperty("os.name"))
      config <- load(templateFile = components.Files.Templates.ConfigOverride)
      rules <- load(templateFile = rulesTemplateFile)
    } yield {
      ApplicationTemplates(
        config = ConfigTemplate(config),
        rules = RulesTemplate(rules, user)
      )
    }

  def getCurrentUser(property: String): Try[String] =
    Option(property) match {
      case Some(user) => Success(user)
      case None       => Failure(new IllegalArgumentException("Current user not available"))
    }

  def getRulesTemplateFile(property: String): Try[String] =
    Option(property).map(_.toLowerCase) match {
      case Some(osName) if osName.contains("mac os") =>
        Success(components.Files.Templates.RulesMacOS)

      case Some(osName) if osName.contains("windows") =>
        Failure(new IllegalArgumentException(s"Unsupported operating system found: [$property]"))

      case _ =>
        Success(components.Files.Templates.RulesLinux)
    }

  def load(templateFile: String): Try[String] = {
    val templatePath = s"templates/$templateFile"

    Option(getClass.getClassLoader.getResourceAsStream(templatePath))
      .map(resource => scala.io.Source.fromInputStream(resource).mkString) match {
      case Some(file) => Try(file)
      case None       => Failure(new FileNotFoundException(s"Template resource [$templatePath] not found"))
    }
  }

  def merge(expanded: String, additionalConfig: JsObject): Try[String] =
    Try {

      val main = typesafe.ConfigFactory.parseString(expanded)
      val additional = typesafe.ConfigFactory.parseString(additionalConfig.toString)

      main
        .withFallback(additional)
        .root()
        .render(typesafe.ConfigRenderOptions.defaults().setOriginComments(false))
        .replaceAll(" {4}", "  ")
    }

  def expand(content: String, params: Map[String, String]): Try[String] =
    params
      .foldLeft[Try[String]](Success(content)) { case (tryExpanded, (placeholder, value)) =>
        tryExpanded.flatMap { expanded =>
          ApplicationTemplates.expand(
            content = expanded,
            placeholder = placeholder,
            value = value
          )
        }
      }
      .map(content => (content, unexpanded(content)))
      .flatMap {
        case (content, Nil) =>
          Success(content)

        case (_, unexpandedPlaceholders) =>
          Failure(
            new IllegalArgumentException(
              s"Unexpanded placeholders found: [${unexpandedPlaceholders.distinct.mkString(",")}]"
            )
          )
      }

  def expand(content: String, placeholder: String, value: String): Try[String] =
    Try(content.replaceAll(s"""\\$$\\$$\\{$placeholder\\}""", value)).flatMap {
      case expanded if expanded != content =>
        Success(expanded)

      case _ =>
        Failure(new IllegalArgumentException(s"Placeholder [$placeholder] not present in template"))
    }

  private def unexpanded(content: String): List[String] =
    """\$\$\{\w+}""".r.findAllIn(content).toList

  private def flatten(
    bootstrapParams: DeviceBootstrapParameters,
    trustStoreParams: TrustStoreParameters,
    keyStoreParameters: KeyStoreParameters
  ): Map[String, String] =
    Map(
      "CLIENT_API_HTTP_CONTEXT_KEYSTORE_PATH" -> keyStoreParameters.clientApiHttpFile,
      "CLIENT_API_HTTP_CONTEXT_KEYSTORE_TYPE" -> keyStoreParameters.clientApiHttpType,
      "CLIENT_API_HTTP_CONTEXT_KEYSTORE_PASSWORD" -> keyStoreParameters.clientApiHttPassword,
      "CLIENT_API_INIT_CONTEXT_KEYSTORE_PATH" -> keyStoreParameters.clientApiInitFile,
      "CLIENT_API_INIT_CONTEXT_KEYSTORE_TYPE" -> keyStoreParameters.clientApiInitType,
      "CLIENT_API_INIT_CONTEXT_KEYSTORE_PASSWORD" -> keyStoreParameters.clientApiInitPassword,
      "AUTHENTICATION_TOKEN_ENDPOINT" -> bootstrapParams.authentication.tokenEndpoint,
      "AUTHENTICATION_CLIENT_ID" -> bootstrapParams.authentication.clientId,
      "AUTHENTICATION_CLIENT_SECRET" -> bootstrapParams.authentication.clientSecret,
      "AUTHENTICATION_USE_QUERY_STRING" -> bootstrapParams.authentication.useQueryString.toString,
      "AUTHENTICATION_SCOPES_API" -> bootstrapParams.authentication.scopes.api,
      "AUTHENTICATION_SCOPES_CORE" -> bootstrapParams.authentication.scopes.core,
      "AUTHENTICATION_CONTEXT_ENABLED" -> bootstrapParams.authentication.context.enabled.toString,
      "AUTHENTICATION_CONTEXT_PROTOCOL" -> bootstrapParams.authentication.context.protocol,
      "AUTHENTICATION_CONTEXT_TRUSTSTORE_PATH" -> trustStoreParams.authenticationFile,
      "AUTHENTICATION_CONTEXT_TRUSTSTORE_TYPE" -> bootstrapParams.authentication.context.storeType,
      "AUTHENTICATION_CONTEXT_TRUSTSTORE_PASSWORD" -> trustStoreParams.authenticationPassword,
      "SERVER_API_URL" -> bootstrapParams.serverApi.url,
      "SERVER_API_USER" -> bootstrapParams.serverApi.user,
      "SERVER_API_USER_SALT" -> bootstrapParams.serverApi.userSalt,
      "SERVER_API_DEVICE" -> bootstrapParams.serverApi.device,
      "SERVER_API_CONTEXT_ENABLED" -> bootstrapParams.serverApi.context.enabled.toString,
      "SERVER_API_CONTEXT_PROTOCOL" -> bootstrapParams.serverApi.context.protocol,
      "SERVER_API_CONTEXT_TRUSTSTORE_PATH" -> trustStoreParams.serverApiFile,
      "SERVER_API_CONTEXT_TRUSTSTORE_TYPE" -> bootstrapParams.serverApi.context.storeType,
      "SERVER_API_CONTEXT_TRUSTSTORE_PASSWORD" -> trustStoreParams.serverApiPassword,
      "SERVER_CORE_ADDRESS" -> bootstrapParams.serverCore.address,
      "SERVER_CORE_CONTEXT_ENABLED" -> bootstrapParams.serverCore.context.enabled.toString,
      "SERVER_CORE_CONTEXT_PROTOCOL" -> bootstrapParams.serverCore.context.protocol,
      "SERVER_CORE_CONTEXT_TRUSTSTORE_PATH" -> trustStoreParams.serverCoreFile,
      "SERVER_CORE_CONTEXT_TRUSTSTORE_TYPE" -> bootstrapParams.serverCore.context.storeType,
      "SERVER_CORE_CONTEXT_TRUSTSTORE_PASSWORD" -> trustStoreParams.serverCorePassword
    )

  private def flatten(user: String): Map[String, String] =
    Map(
      "USER" -> user
    )
}
