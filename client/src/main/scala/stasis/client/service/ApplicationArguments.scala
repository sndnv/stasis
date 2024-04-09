package stasis.client.service

import scopt.OptionParser
import stasis.client.service.ApplicationArguments.Mode.Maintenance

import scala.concurrent.Future

final case class ApplicationArguments(
  mode: ApplicationArguments.Mode
)

object ApplicationArguments {
  sealed trait Mode
  object Mode {
    final case object Service extends Mode

    final case class Bootstrap(
      serverBootstrapUrl: String,
      bootstrapCode: String,
      acceptSelfSignedCertificates: Boolean,
      userName: String,
      userPassword: Array[Char],
      userPasswordConfirm: Array[Char]
    ) extends Mode {
      def validate(): Unit = {
        require(
          serverBootstrapUrl.startsWith("https://"),
          "Server bootstrap URL must be provided and must use HTTPS"
        )

        require(
          bootstrapCode.trim.nonEmpty,
          "Bootstrap code must be provided"
        )
      }
    }

    object Bootstrap {
      def empty: Bootstrap =
        Bootstrap(
          serverBootstrapUrl = "",
          bootstrapCode = "",
          acceptSelfSignedCertificates = false,
          userName = "",
          userPassword = Array.emptyCharArray,
          userPasswordConfirm = Array.emptyCharArray
        )
    }

    final case class Maintenance(
      regenerateApiCertificate: Boolean,
      deviceSecretOperation: Option[Maintenance.DeviceSecretOperation],
      userName: String,
      userPassword: Array[Char]
    ) extends Mode {
      def validate(): Unit =
        require(
          Seq(regenerateApiCertificate, deviceSecretOperation.nonEmpty).exists(identity),
          "At least one maintenance flag must be set"
        )
    }

    object Maintenance {
      def empty: Maintenance = Maintenance(
        regenerateApiCertificate = false,
        deviceSecretOperation = None,
        userName = "",
        userPassword = Array.emptyCharArray
      )

      sealed trait DeviceSecretOperation
      object DeviceSecretOperation {
        case object Push extends DeviceSecretOperation
        case object Pull extends DeviceSecretOperation
      }
    }
  }

  def apply(
    applicationName: String,
    args: => Array[String]
  ): Future[ApplicationArguments] =
    parser(applicationName)
      .parse(
        args = args,
        init = ApplicationArguments(Mode.Service)
      ) match {
      case Some(arguments) =>
        Future.successful(arguments)

      case None =>
        Future.failed(new IllegalArgumentException("Invalid arguments provided"))
    }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  private def parser(applicationName: String): OptionParser[ApplicationArguments] =
    new OptionParser[ApplicationArguments](applicationName) {
      note("Stasis client.\n")
      help('h', "help").text("Show this message and exit.\n")

      cmd("service")
        .abbr("s")
        .action((_, args) => args.copy(mode = Mode.Service))
        .text("\tStarting the client in normal service mode (default).\n")

      cmd("bootstrap")
        .abbr("b")
        .action((_, args) => args.copy(mode = Mode.Bootstrap.empty))
        .text("\tStarting the client in bootstrap mode.\n")
        .children(
          opt[String]("server")
            .valueName("<url>")
            .action { case (server, args) =>
              (args.mode: @unchecked) match {
                case mode: Mode.Bootstrap => args.copy(mode = mode.copy(serverBootstrapUrl = server))
              }
            }
            .optional()
            .text("Server bootstrap URL."),
          opt[String]("code")
            .valueName("<code>")
            .action { case (code, args) =>
              (args.mode: @unchecked) match {
                case mode: Mode.Bootstrap => args.copy(mode = mode.copy(bootstrapCode = code))
              }
            }
            .optional()
            .text("Bootstrap code."),
          opt[Unit]("accept-self-signed")
            .action { case (_, args) =>
              (args.mode: @unchecked) match {
                case mode: Mode.Bootstrap => args.copy(mode = mode.copy(acceptSelfSignedCertificates = true))
              }
            }
            .optional()
            .text("Accept any self-signed server TLS certificate (NOT recommended)."),
          opt[String]("user-name")
            .valueName("<name>")
            .action { case (name, args) =>
              (args.mode: @unchecked) match {
                case mode: Mode.Bootstrap => args.copy(mode = mode.copy(userName = name))
              }
            }
            .optional()
            .text("User name (for connection to the server, when pulling secrets)."),
          opt[String]("user-password")
            .action { case (password, args) =>
              (args.mode: @unchecked) match {
                case mode: Mode.Bootstrap => args.copy(mode = mode.copy(userPassword = password.toCharArray))
              }
            }
            .optional()
            .text("User password (for encrypting new device secret).")
        )

      cmd("maintenance")
        .abbr("m")
        .action((_, args) => args.copy(mode = Mode.Maintenance.empty))
        .text("\tStarting the client in maintenance mode.\n")
        .children(
          opt[Unit]("regenerate-api-certificate")
            .action { case (_, args) =>
              (args.mode: @unchecked) match {
                case mode: Mode.Maintenance => args.copy(mode = mode.copy(regenerateApiCertificate = true))
              }
            }
            .text("Regenerate the TLS certificate for the client's own API."),
          opt[String]("secret")
            .action { case (operationArg, args) =>
              (args.mode: @unchecked) match {
                case mode: Mode.Maintenance =>
                  val operation: Maintenance.DeviceSecretOperation = (operationArg: @unchecked) match {
                    case "push" => Mode.Maintenance.DeviceSecretOperation.Push
                    case "pull" => Mode.Maintenance.DeviceSecretOperation.Pull
                  }

                  args.copy(mode = mode.copy(deviceSecretOperation = Some(operation)))
              }
            }
            .children(
              opt[String]("user-name")
                .valueName("<name>")
                .action { case (name, args) =>
                  (args.mode: @unchecked) match {
                    case mode: Mode.Maintenance => args.copy(mode = mode.copy(userName = name))
                  }
                }
                .optional()
                .text("User name (for connection to the server, when pushing or pulling secrets)."),
              opt[String]("user-password")
                .valueName("<password>")
                .action { case (password, args) =>
                  (args.mode: @unchecked) match {
                    case mode: Mode.Maintenance => args.copy(mode = mode.copy(userPassword = password.toCharArray))
                  }
                }
                .optional()
                .text(
                  "User password (for connection to the server and encrypting/decrypting the client secret, when pushing or pulling secrets)."
                )
            )
            .validate { v =>
              v.toLowerCase match {
                case "push" | "pull" => success
                case other           => failure(s"Secrets management operation must be one of [push, pull] but [$other] provided")
              }
            }
            .valueName("[push|pull]")
            .text("Use the server to store or retrieve the client's secret.")
        )
    }
}
