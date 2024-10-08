package stasis.client.service.components.maintenance

import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.{config => typesafe}
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.LoggerOps

import stasis.client.service.ApplicationArguments
import stasis.client.service.components

trait Certificates {
  def apply(): Future[Done]
}

object Certificates {
  def apply(base: Base): Future[Certificates] = {
    import base._

    Future.successful(
      new Certificates {
        override def apply(): Future[Done] =
          for {
            _ <- regenerateApiCertificate(base)
          } yield {
            Done
          }
      }
    )
  }

  def regenerateApiCertificate(base: Base): Future[Done] = {
    import base._

    args match {
      case ApplicationArguments.Mode.Maintenance.RegenerateApiCertificate =>
        log.infoN("Generating a new client API certificate...")

        for {
          parent <- directory.configDirectory match {
            case Some(parent) => Future.successful(parent)
            case None         => Future.failed(new RuntimeException("No configuration directory is available"))
          }
          currentPassword <- rawConfig.getString("api.http.context.keystore.password").future
          _ = require(
            currentPassword.nonEmpty,
            "Client API certificate regeneration failed; could not retrieve existing certificate password"
          )
          (_, newPassword) <- components.bootstrap.Parameters
            .createKeyStore(
              name = components.bootstrap.Parameters.KeyStore.CertificateName,
              storeType = components.bootstrap.Parameters.KeyStore.Type,
              passwordSize = components.bootstrap.Parameters.KeyStore.PasswordSize,
              parent = parent,
              file = components.Files.KeyStores.ClientApi
            )
            .future
          _ <- replacePassword(parent = parent, config = configOverride, newPassword = newPassword)
        } yield {
          Done
        }

      case _ =>
        log.debugN("Client API certificate regeneration flag not set; skipping...")
        Future.successful(Done)
    }
  }

  private def replacePassword(
    parent: Path,
    config: typesafe.Config,
    newPassword: String
  )(implicit ec: ExecutionContext): Future[Done] = {
    val configPath = parent.resolve(components.Files.ConfigOverride)

    val updatedConfig = config
      .withValue(
        "stasis.client.api.http.context.keystore.password",
        typesafe.ConfigValueFactory.fromAnyRef(newPassword)
      )
      .withValue(
        "stasis.client.api.init.context.keystore.password",
        typesafe.ConfigValueFactory.fromAnyRef(newPassword)
      )
      .root()
      .render(typesafe.ConfigRenderOptions.defaults().setOriginComments(false))
      .replaceAll(" {4}", "  ")

    Future {
      val _ = Files.writeString(configPath, updatedConfig)
      Done
    }
  }
}
