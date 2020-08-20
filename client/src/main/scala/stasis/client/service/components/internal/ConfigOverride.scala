package stasis.client.service.components.internal

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import com.typesafe.{config => typesafe}
import stasis.client.service.ApplicationDirectory
import stasis.client.service.components.Files

import scala.concurrent.{ExecutionContext, Future}

object ConfigOverride {
  def load(directory: ApplicationDirectory): typesafe.Config =
    directory.findFile(file = Files.ConfigOverride) match {
      case Some(configFile) => load(configFile)
      case None             => typesafe.ConfigFactory.empty()
    }

  def require(directory: ApplicationDirectory)(implicit ec: ExecutionContext): Future[typesafe.Config] =
    directory.requireFile(file = Files.ConfigOverride).map(load)

  private def load(configFile: Path) =
    typesafe.ConfigFactory.parseString(
      java.nio.file.Files.readString(configFile, StandardCharsets.UTF_8)
    )
}
