package stasis.client.service

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import scopt.OptionParser

final case class ApplicationArguments(
  mode: ApplicationArguments.Mode
)

object ApplicationArguments {
  sealed trait Mode
  object Mode {
    case object Service extends Mode

    final case class Bootstrap(
      serverBootstrapUrl: String,
      bootstrapCode: String,
      acceptSelfSignedCertificates: Boolean,
      userName: String,
      userPassword: Array[Char],
      recreateFiles: Boolean
    ) extends Mode {
      def expand(): Bootstrap =
        expand(env = System.getenv().asScala.toMap)

      def expand(env: Map[String, String]): Bootstrap =
        copy(
          serverBootstrapUrl = env.getOrElse("STASIS_CLIENT_BOOTSTRAP_SERVER_URL", serverBootstrapUrl),
          bootstrapCode = env.getOrElse("STASIS_CLIENT_BOOTSTRAP_CODE", bootstrapCode),
          userName = env.getOrElse("STASIS_CLIENT_BOOTSTRAP_USER_NAME", userName),
          userPassword = env.get("STASIS_CLIENT_BOOTSTRAP_USER_PASSWORD").map(_.toCharArray).getOrElse(userPassword)
        )

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
          recreateFiles = false
        )
    }

    sealed trait Maintenance extends Mode {
      def expand(): Maintenance =
        expand(env = System.getenv().asScala.toMap)

      def expand(env: Map[String, String]): Maintenance = this

      def validate(): Unit =
        require(this != Maintenance.Empty, "At least one maintenance flag must be set")
    }

    object Maintenance {
      sealed trait UserCredentialsOperation extends Maintenance

      sealed trait DeviceSecretOperation extends Maintenance {
        def currentUserName: String
        def currentUserPassword: Array[Char]
      }

      case object Empty extends Maintenance

      case object RegenerateApiCertificate extends Maintenance

      final case class PushDeviceSecret(
        override val currentUserName: String,
        override val currentUserPassword: Array[Char],
        remotePassword: Option[Array[Char]]
      ) extends DeviceSecretOperation {
        override def expand(env: Map[String, String]): Maintenance =
          copy(
            currentUserName = env.getOrElse("STASIS_CLIENT_PUSH_DEVICE_SECRET_CURRENT_USER_NAME", currentUserName),
            currentUserPassword = env
              .get("STASIS_CLIENT_PUSH_DEVICE_SECRET_CURRENT_USER_PASSWORD")
              .map(_.toCharArray)
              .getOrElse(currentUserPassword),
            remotePassword = env
              .get("STASIS_CLIENT_PUSH_DEVICE_SECRET_REMOTE_PASSWORD")
              .map(_.toCharArray)
              .orElse(remotePassword)
          )
      }

      object PushDeviceSecret {
        def empty: PushDeviceSecret =
          PushDeviceSecret(
            currentUserName = "",
            currentUserPassword = Array.emptyCharArray,
            remotePassword = None
          )
      }

      final case class PullDeviceSecret(
        override val currentUserName: String,
        override val currentUserPassword: Array[Char],
        remotePassword: Option[Array[Char]]
      ) extends DeviceSecretOperation {
        override def expand(env: Map[String, String]): Maintenance =
          copy(
            currentUserName = env.getOrElse("STASIS_CLIENT_PULL_DEVICE_SECRET_CURRENT_USER_NAME", currentUserName),
            currentUserPassword = env
              .get("STASIS_CLIENT_PULL_DEVICE_SECRET_CURRENT_USER_PASSWORD")
              .map(_.toCharArray)
              .getOrElse(currentUserPassword),
            remotePassword = env
              .get("STASIS_CLIENT_PULL_DEVICE_SECRET_REMOTE_PASSWORD")
              .map(_.toCharArray)
              .orElse(remotePassword)
          )
      }

      object PullDeviceSecret {
        def empty: PullDeviceSecret =
          PullDeviceSecret(
            currentUserName = "",
            currentUserPassword = Array.emptyCharArray,
            remotePassword = None
          )
      }

      final case class ReEncryptDeviceSecret(
        override val currentUserName: String,
        override val currentUserPassword: Array[Char],
        oldUserPassword: Array[Char]
      ) extends DeviceSecretOperation {
        override def expand(env: Map[String, String]): Maintenance =
          copy(
            currentUserName = env.getOrElse("STASIS_CLIENT_REENCRYPT_DEVICE_SECRET_CURRENT_USER_NAME", currentUserName),
            currentUserPassword = env
              .get("STASIS_CLIENT_REENCRYPT_DEVICE_SECRET_CURRENT_USER_PASSWORD")
              .map(_.toCharArray)
              .getOrElse(currentUserPassword),
            oldUserPassword = env
              .get("STASIS_CLIENT_REENCRYPT_DEVICE_SECRET_OLD_USER_PASSWORD")
              .map(_.toCharArray)
              .getOrElse(oldUserPassword)
          )
      }

      object ReEncryptDeviceSecret {
        def empty: ReEncryptDeviceSecret =
          ReEncryptDeviceSecret(
            currentUserName = "",
            currentUserPassword = Array.emptyCharArray,
            oldUserPassword = Array.emptyCharArray
          )
      }

      final case class ResetUserCredentials(
        currentUserPassword: Array[Char],
        newUserPassword: Array[Char],
        newUserSalt: String
      ) extends UserCredentialsOperation {
        override def expand(env: Map[String, String]): Maintenance =
          copy(
            currentUserPassword = env
              .get("STASIS_CLIENT_RESET_USER_CREDENTIALS_CURRENT_USER_PASSWORD")
              .map(_.toCharArray)
              .getOrElse(currentUserPassword),
            newUserPassword = env
              .get("STASIS_CLIENT_RESET_USER_CREDENTIALS_NEW_USER_PASSWORD")
              .map(_.toCharArray)
              .getOrElse(newUserPassword),
            newUserSalt = env.getOrElse("STASIS_CLIENT_RESET_USER_CREDENTIALS_NEW_USER_SALT", newUserSalt)
          )
      }

      object ResetUserCredentials {
        def empty: ResetUserCredentials =
          ResetUserCredentials(
            currentUserPassword = Array.emptyCharArray,
            newUserPassword = Array.emptyCharArray,
            newUserSalt = ""
          )
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
            .text("User password (for encrypting new device secret)."),
          opt[Unit]("recreate-files")
            .action { case (_, args) =>
              (args.mode: @unchecked) match {
                case mode: Mode.Bootstrap => args.copy(mode = mode.copy(recreateFiles = true))
              }
            }
            .optional()
            .text("Force the bootstrap process to recreate all configuration files, even if they already exist.")
        )

      cmd("maintenance")
        .abbr("m")
        .action((_, args) => args.copy(mode = Mode.Maintenance.Empty))
        .text("\tStarting the client in maintenance mode.\n")
        .children(
          cmd("regenerate-api-certificate")
            .action { case (_, args) =>
              (args.mode: @unchecked) match {
                case _: Mode.Maintenance => args.copy(mode = Mode.Maintenance.RegenerateApiCertificate)
              }
            }
            .text("Regenerate the TLS certificate for the client's own API."),
          cmd("credentials")
            .children(
              cmd("reset")
                .action((_, args) => args.copy(mode = Mode.Maintenance.ResetUserCredentials.empty))
                .children(
                  opt[String]("current-user-password")
                    .valueName("<password>")
                    .action { case (password, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.ResetUserCredentials =>
                          args.copy(mode = mode.copy(currentUserPassword = password.toCharArray))
                      }
                    }
                    .optional()
                    .text("Current user password (for decrypting the device secret before resetting the credentials)."),
                  opt[String]("new-user-password")
                    .valueName("<password>")
                    .action { case (password, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.ResetUserCredentials =>
                          args.copy(mode = mode.copy(newUserPassword = password.toCharArray))
                      }
                    }
                    .optional()
                    .text("New user password (for re-encrypting device secret after resetting the credentials)."),
                  opt[String]("new-user-salt")
                    .valueName("<salt>")
                    .action { case (salt, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.ResetUserCredentials =>
                          args.copy(mode = mode.copy(newUserSalt = salt))
                      }
                    }
                    .optional()
                    .text("New user salt (for re-encrypting device secret after resetting the credentials).")
                )
                .text("Reset user credentials.")
            )
            .text("Manage the current user credentials"),
          cmd("secret")
            .children(
              cmd("push")
                .action((_, args) => args.copy(mode = Mode.Maintenance.PushDeviceSecret.empty))
                .children(
                  opt[String]("current-user-name")
                    .valueName("<name>")
                    .action { case (name, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.PushDeviceSecret =>
                          args.copy(mode = mode.copy(currentUserName = name))
                      }
                    }
                    .optional()
                    .text("Current user name (for connection to the server)."),
                  opt[String]("current-user-password")
                    .valueName("<password>")
                    .action { case (password, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.PushDeviceSecret =>
                          args.copy(mode = mode.copy(currentUserPassword = password.toCharArray))
                      }
                    }
                    .optional()
                    .text("Current user password (for connection to the server)."),
                  opt[String]("remote-password")
                    .valueName("<password>")
                    .action { case (password, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.PushDeviceSecret =>
                          args.copy(mode = mode.copy(remotePassword = Some(password.toCharArray)))
                      }
                    }
                    .optional()
                    .text("Password override if the remote secret should have a different password.")
                ),
              cmd("pull")
                .action((_, args) => args.copy(mode = Mode.Maintenance.PullDeviceSecret.empty))
                .children(
                  opt[String]("current-user-name")
                    .valueName("<name>")
                    .action { case (name, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.PullDeviceSecret =>
                          args.copy(mode = mode.copy(currentUserName = name))
                      }
                    }
                    .optional()
                    .text("Current user name (for connection to the server)."),
                  opt[String]("current-user-password")
                    .valueName("<password>")
                    .action { case (password, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.PullDeviceSecret =>
                          args.copy(mode = mode.copy(currentUserPassword = password.toCharArray))
                      }
                    }
                    .optional()
                    .text("Current user password (for connection to the server)."),
                  opt[String]("remote-password")
                    .valueName("<password>")
                    .action { case (password, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.PullDeviceSecret =>
                          args.copy(mode = mode.copy(remotePassword = Some(password.toCharArray)))
                      }
                    }
                    .optional()
                    .text("Password override if the remote secret has a different password.")
                ),
              cmd("re-encrypt")
                .action((_, args) => args.copy(mode = Mode.Maintenance.ReEncryptDeviceSecret.empty))
                .children(
                  opt[String]("current-user-name")
                    .valueName("<name>")
                    .action { case (name, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.ReEncryptDeviceSecret =>
                          args.copy(mode = mode.copy(currentUserName = name))
                      }
                    }
                    .optional()
                    .text("Current user name (for connection to the server)."),
                  opt[String]("current-user-password")
                    .valueName("<password>")
                    .action { case (password, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.ReEncryptDeviceSecret =>
                          args.copy(mode = mode.copy(currentUserPassword = password.toCharArray))
                      }
                    }
                    .optional()
                    .text("Current user password (for connection to the server)."),
                  opt[String]("old-user-password")
                    .valueName("<password>")
                    .action { case (password, args) =>
                      (args.mode: @unchecked) match {
                        case mode: Mode.Maintenance.ReEncryptDeviceSecret =>
                          args.copy(mode = mode.copy(oldUserPassword = password.toCharArray))
                      }
                    }
                    .optional()
                    .text("User password previously used for encrypting the local device secret.")
                )
            )
            .text(
              "Manage the current client secret - Use the server to store or retrieve the secret, or re-encrypt it with a new user password."
            )
        )
    }
}
