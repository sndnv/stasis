package stasis.client.service.components.bootstrap

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.slf4j.Logger
import stasis.client.service.components.bootstrap.internal.SelfSignedCertificateGenerator
import stasis.client.service.{components, ApplicationDirectory, ApplicationTemplates}
import stasis.shared.model.devices.DeviceBootstrapParameters

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import java.util.concurrent.ThreadLocalRandom
import scala.concurrent.Future
import scala.util.{Failure, Random, Success, Try}

trait Parameters {
  def apply(): Future[Done]
}

object Parameters {
  object TrustStore {
    final val PasswordSize: Int = 32
  }

  object KeyStore {
    final val PasswordSize: Int = 32
    final val Type: String = "PKCS12"
    final val CertificateName: String = "localhost"
  }

  def apply(base: Base, bootstrap: Bootstrap): Future[Parameters] = {
    import base._

    Future.successful(
      new Parameters {
        override def apply(): Future[Done] =
          for {
            params <- bootstrap.execute()
            _ <- Future.fromTry(process(directory, templates, params))
          } yield {
            Done
          }
      }
    )
  }

  def process(
    directory: ApplicationDirectory,
    templates: ApplicationTemplates,
    bootstrapParams: DeviceBootstrapParameters
  )(implicit log: Logger): Try[Done] =
    for {
      parent <- directory.configDirectory match {
        case Some(parent) => Success(parent)
        case None         => Failure(new RuntimeException("No configuration directory is available"))
      }
      _ <- Try { Files.createDirectories(parent) }
      (authenticationFile, authenticationPassword) <- storeContext(
        context = bootstrapParams.authentication.context,
        passwordSize = TrustStore.PasswordSize,
        parent = parent,
        file = components.Files.TrustStores.Authentication
      )
      (serverApiFile, serverApiPassword) <- storeContext(
        context = bootstrapParams.serverApi.context,
        passwordSize = TrustStore.PasswordSize,
        parent = parent,
        file = components.Files.TrustStores.ServerApi
      )
      (serverCoreFile, serverCorePassword) <- storeContext(
        context = bootstrapParams.serverCore.context,
        passwordSize = TrustStore.PasswordSize,
        parent = parent,
        file = components.Files.TrustStores.ServerCore
      )
      (clientApiFile, clientApiPassword) <- createKeyStore(
        name = KeyStore.CertificateName,
        storeType = KeyStore.Type,
        passwordSize = KeyStore.PasswordSize,
        parent = parent,
        file = components.Files.KeyStores.ClientApi
      )
      trustStoreParams = ApplicationTemplates.TrustStoreParameters(
        authenticationFile = authenticationFile,
        authenticationPassword = authenticationPassword,
        serverApiFile = serverApiFile,
        serverApiPassword = serverApiPassword,
        serverCoreFile = serverCoreFile,
        serverCorePassword = serverCorePassword
      )
      keyStoreParams = ApplicationTemplates.KeyStoreParameters(
        clientApiHttpFile = clientApiFile,
        clientApiHttpType = KeyStore.Type,
        clientApiHttPassword = clientApiPassword,
        clientApiInitFile = clientApiFile,
        clientApiInitType = KeyStore.Type,
        clientApiInitPassword = clientApiPassword
      )
      config <- templates.config.expand(bootstrapParams, trustStoreParams, keyStoreParams)
      rules <- templates.rules.expand()
      schedules = ""
      _ <- storeExpandedTemplate(config, parent, components.Files.ConfigOverride)
      _ <- storeExpandedTemplate(rules, parent, components.Files.Default.ClientRules)
      _ <- storeExpandedTemplate(schedules, parent, components.Files.Default.ClientSchedules)
    } yield {
      Done
    }

  def storeExpandedTemplate(
    template: String,
    parent: Path,
    file: String
  )(implicit log: Logger): Try[Done] =
    Try {
      val path = parent.resolve(file)
      log.info("Creating [{}] from template...", path)
      val _ = Files.writeString(path, template)

      Done
    }

  def storeContext(
    context: DeviceBootstrapParameters.Context,
    passwordSize: Int,
    parent: Path,
    file: String
  )(implicit log: Logger): Try[(String, String)] =
    if (context.enabled) {
      Try {
        val store = DeviceBootstrapParameters.Context.decodeKeyStore(
          content = context.storeContent,
          password = context.temporaryStorePassword,
          storeType = context.storeType
        )

        val extension = storeTypeAsExtension(context.storeType)

        val path = parent.resolve(s"$file.$extension")

        log.infoN("Creating trust store [{}]...", path)

        val rnd: Random = ThreadLocalRandom.current()
        val password = rnd.alphanumeric.take(passwordSize).mkString

        val out = Files.newOutputStream(path)
        try {
          store.store(out, password.toCharArray)
        } finally {
          out.close()
        }

        (path.toString, password)
      }
    } else {
      log.debugN("Context not enabled for trust store [{}]; skipping...", file)
      Success(("", ""))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def createKeyStore(
    name: String,
    storeType: String,
    passwordSize: Int,
    parent: Path,
    file: String
  )(implicit log: Logger): Try[(String, String)] =
    SelfSignedCertificateGenerator
      .generate(name)
      .map { case (privateKey, certificate) =>
        val extension = storeTypeAsExtension(storeType)

        val path = parent.resolve(s"$file.$extension")

        log.infoN("Creating key store [{}]...", path)

        val rnd: Random = ThreadLocalRandom.current()
        val password = rnd.alphanumeric.take(passwordSize).mkString

        val store = java.security.KeyStore.getInstance(storeType)
        store.load(None.orNull, None.orNull)
        store.setKeyEntry(file, privateKey, password.toCharArray, Array(certificate))

        val _ = Files.deleteIfExists(path)
        val permissions = PosixFilePermissions.fromString(ApplicationDirectory.Default.CreatedFilePermissions)
        val _ = Files.createFile(path, PosixFilePermissions.asFileAttribute(permissions))

        val out = Files.newOutputStream(path)
        try {
          store.store(out, password.toCharArray)
        } finally {
          out.close()
        }

        (path.toString, password)
      }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def storeTypeAsExtension(storeType: String): String =
    storeType.trim.toLowerCase match {
      case "pkcs12" => "p12"
      case "jks"    => "jks"
      case other    => throw new IllegalArgumentException(s"Unexpected store type provided: [$other]")
    }
}
