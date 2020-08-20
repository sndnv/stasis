package stasis.client.service.components.bootstrap

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import java.util.concurrent.ThreadLocalRandom

import akka.Done
import stasis.client.service.components.bootstrap.internal.SelfSignedCertificateGenerator
import stasis.client.service.{components, ApplicationDirectory, ApplicationTemplates}
import stasis.shared.model.devices.DeviceBootstrapParameters

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
    final val CertificateCommonName: String = "localhost"
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
  ): Try[Done] =
    for {
      parent <- directory.configDirectory match {
        case Some(parent) => Success(parent)
        case None         => Failure(new RuntimeException("No configuration directory is available"))
      }
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
        commonName = KeyStore.CertificateCommonName,
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

  def storeExpandedTemplate(template: String, parent: Path, file: String): Try[Done] =
    Try {
      val path = parent.resolve(file)
      val _ = Files.writeString(path, template)

      Done
    }

  def storeContext(
    context: DeviceBootstrapParameters.Context,
    passwordSize: Int,
    parent: Path,
    file: String
  ): Try[(String, String)] =
    if (context.enabled) {
      Try {
        val store = DeviceBootstrapParameters.Context.decodeKeyStore(
          content = context.storeContent,
          password = context.temporaryStorePassword,
          storeType = context.storeType
        )

        val extension = context.storeType.toLowerCase match {
          case "pkcs12" => "p12"
          case "jks"    => "jks"
        }

        val path = parent.resolve(s"$file.$extension")

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
      Success(("", ""))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def createKeyStore(
    commonName: String,
    storeType: String,
    passwordSize: Int,
    parent: Path,
    file: String
  ): Try[(String, String)] =
    SelfSignedCertificateGenerator
      .generate(distinguishedName = s"CN=$commonName")
      .map {
        case (privateKey, certificate) =>
          val extension = storeType.toLowerCase match {
            case "pkcs12" => "p12"
            case "jks"    => "jks"
          }

          val path = parent.resolve(s"$file.$extension")

          val rnd: Random = ThreadLocalRandom.current()
          val password = rnd.alphanumeric.take(passwordSize).mkString

          val store = java.security.KeyStore.getInstance(storeType)
          store.load(None.orNull, None.orNull)
          store.setKeyEntry(file, privateKey, password.toCharArray, Array(certificate))

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
}
