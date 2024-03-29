package stasis.client.service

import scopt.OptionParser

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
          userPassword = Array.emptyCharArray,
          userPasswordConfirm = Array.emptyCharArray
        )
    }

    final case class Maintenance(
      regenerateApiCertificate: Boolean
    ) extends Mode {
      def validate(): Unit =
        require(
          Seq(regenerateApiCertificate).exists(identity),
          "At least one maintenance flag must be set"
        )
    }

    object Maintenance {
      def empty: Maintenance = Maintenance(
        regenerateApiCertificate = false
      )
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
      help("help").text("Show this message and exit.\n")

      cmd("service")
        .action((_, args) => args.copy(mode = Mode.Service))
        .text("\tStarting the client in normal service mode (default).\n")

      cmd("bootstrap")
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
        .action((_, args) => args.copy(mode = Mode.Maintenance.empty))
        .text("\tStarting the client in maintenance mode.\n")
        .children(
          opt[Unit]("regenerate-api-certificate")
            .action { case (_, args) =>
              (args.mode: @unchecked) match {
                case mode: Mode.Maintenance => args.copy(mode = mode.copy(regenerateApiCertificate = true))
              }
            }
            .optional()
            .text("Regenerate the TLS certificate for the client's own API.")
        )
    }
}
