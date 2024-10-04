package stasis.client.compression

import java.nio.file.Path

import stasis.client.model.EntityMetadata
import stasis.client.model.SourceEntity
import stasis.client.model.TargetEntity

trait Compression {
  def defaultCompression: Encoder with Decoder

  def disabledExtensions: Set[String]

  def algorithmFor(entity: Path): String =
    if (compressionAllowedFor(entity)) {
      defaultCompression.name
    } else {
      Identity.name
    }

  def encoderFor(entity: SourceEntity): Encoder =
    compressionFor(entity.currentMetadata)

  def decoderFor(entity: TargetEntity): Decoder =
    compressionFor(entity.existingMetadata)

  private def compressionAllowedFor(entity: Path): Boolean =
    !disabledExtensions.exists(extension => entity.toString.endsWith(s".$extension"))

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def compressionFor(entity: EntityMetadata): Encoder with Decoder =
    entity match {
      case file: EntityMetadata.File =>
        Compression.fromString(file.compression)

      case directory: EntityMetadata.Directory =>
        throw new IllegalArgumentException(
          s"Expected metadata for file but directory metadata for [${directory.path.toString}] provided"
        )
    }
}

object Compression {
  def apply(
    withDefaultCompression: String,
    withDisabledExtensions: String
  ): Compression =
    new Compression {
      override val defaultCompression: Encoder with Decoder =
        fromString(compression = withDefaultCompression)

      override val disabledExtensions: Set[String] = withDisabledExtensions
        .split(",")
        .map(_.trim)
        .filter(_.nonEmpty)
        .distinct
        .toSet
    }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def fromString(compression: String): Encoder with Decoder =
    compression.toLowerCase match {
      case Deflate.name  => Deflate
      case Gzip.name     => Gzip
      case Identity.name => Identity
      case other         => throw new IllegalArgumentException(s"Unexpected compression provided: [$other]")
    }
}
