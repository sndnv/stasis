package stasis.client.service.components.internal

import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.{config => typesafe}
import stasis.client.service.ApplicationDirectory
import stasis.client.service.components.Files

object ConfigOverride {
  def load(directory: ApplicationDirectory): typesafe.Config =
    directory.findFile(file = Files.ConfigOverride) match {
      case Some(configFile) => load(configFile)
      case None             => typesafe.ConfigFactory.empty()
    }

  def require(directory: ApplicationDirectory)(implicit ec: ExecutionContext): Future[typesafe.Config] =
    directory.requireFile(file = Files.ConfigOverride).map(load)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def update(directory: ApplicationDirectory, path: String, value: String): Unit = {
    val configFile = directory.findFile(file = Files.ConfigOverride) match {
      case Some(file) => file
      case None       => throw new FileNotFoundException(s"File [${Files.ConfigOverride}] not found")
    }

    val originalContent = java.nio.file.Files.readString(configFile, StandardCharsets.UTF_8)

    val updatedContent = replaceStringConfigValue(originalContent = originalContent, path = path, replacementValue = value)

    val _ = java.nio.file.Files.writeString(configFile, updatedContent, StandardCharsets.UTF_8)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def replaceStringConfigValue(
    originalContent: String,
    path: String,
    replacementValue: String
  ): String = {
    val key = path.split('.').map(_.trim).filter(_.nonEmpty).lastOption match {
      case Some(key) => key
      case None      => throw new IllegalArgumentException(s"Invalid config path provided: [$path]")
    }

    val existingValue = load(originalContent).getString(path)

    val regex = s"""$key(\\s*[=:]\\s*)"($existingValue)"""".r

    regex.replaceFirstIn(originalContent, s"$key$$1\"$replacementValue\"")
  }

  private def load(configFile: Path): typesafe.Config =
    load(java.nio.file.Files.readString(configFile, StandardCharsets.UTF_8))

  private def load(content: String): typesafe.Config =
    typesafe.ConfigFactory.parseString(content)
}
