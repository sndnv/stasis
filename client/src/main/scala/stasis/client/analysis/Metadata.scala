package stasis.client.analysis

import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer

import stasis.client.compression.Compression
import stasis.client.model.EntityMetadata
import stasis.client.model.SourceEntity
import stasis.client.model.TargetEntity
import stasis.core.packaging.Crate

object Metadata {
  def collectSource(
    checksum: Checksum,
    compression: Compression,
    entity: Path,
    existingMetadata: Option[EntityMetadata]
  )(implicit mat: Materializer): Future[SourceEntity] = {
    implicit val ec: ExecutionContext = mat.executionContext

    for {
      baseMetadata <- extractBaseEntityMetadata(entity)
      entityMetadata <- collectEntityMetadata(
        currentMetadata = baseMetadata,
        collectCrates = checksum => collectCratesForSourceFile(existingMetadata, checksum),
        collectCompression = () => Future.successful(compression.algorithmFor(entity)),
        checksum = checksum
      )
    } yield {
      SourceEntity(
        path = entity,
        existingMetadata = existingMetadata,
        currentMetadata = entityMetadata
      )
    }
  }

  def collectTarget(
    checksum: Checksum,
    entity: Path,
    destination: TargetEntity.Destination,
    existingMetadata: EntityMetadata
  )(implicit mat: Materializer): Future[TargetEntity] = {
    implicit val ec: ExecutionContext = mat.executionContext

    val targetEntity = TargetEntity(
      path = entity,
      destination = destination,
      existingMetadata = existingMetadata,
      currentMetadata = None
    )

    val destinationPath = targetEntity.destinationPath

    if (Files.exists(destinationPath)) {
      for {
        baseMetadata <- extractBaseEntityMetadata(destinationPath)
        entityMetadata <- collectEntityMetadata(
          currentMetadata = baseMetadata,
          collectCrates = _ => collectCratesForTargetFile(existingMetadata),
          checksum = checksum,
          collectCompression = () => collectCompressionForTargetFile(existingMetadata)
        )
      } yield {
        targetEntity.copy(currentMetadata = Some(entityMetadata))
      }
    } else {
      Future.successful(targetEntity)
    }
  }

  def collectEntityMetadata(
    currentMetadata: BaseEntityMetadata,
    checksum: Checksum,
    collectCrates: BigInt => Future[Map[String, Crate.Id]],
    collectCompression: () => Future[String]
  )(implicit mat: Materializer): Future[EntityMetadata] =
    if (currentMetadata.isDirectory) {
      Future.successful(
        EntityMetadata.Directory(
          path = currentMetadata.path.toAbsolutePath.toString,
          link = currentMetadata.link.map(_.toAbsolutePath.toString),
          isHidden = currentMetadata.isHidden,
          created = currentMetadata.created,
          updated = currentMetadata.updated,
          owner = currentMetadata.owner,
          group = currentMetadata.group,
          permissions = currentMetadata.permissions
        )
      )
    } else {
      implicit val ec: ExecutionContext = mat.executionContext

      for {
        currentChecksum <- checksum.calculate(currentMetadata.path)
        crates <- collectCrates(currentChecksum)
        compression <- collectCompression()
      } yield {
        EntityMetadata.File(
          path = currentMetadata.path.toAbsolutePath.toString,
          size = currentMetadata.attributes.size,
          link = currentMetadata.link.map(_.toAbsolutePath.toString),
          isHidden = currentMetadata.isHidden,
          created = currentMetadata.created,
          updated = currentMetadata.updated,
          owner = currentMetadata.owner,
          group = currentMetadata.group,
          permissions = currentMetadata.permissions,
          checksum = currentChecksum,
          crates = crates,
          compression = compression
        )
      }
    }

  def collectCratesForSourceFile(
    existingMetadata: Option[EntityMetadata],
    currentChecksum: BigInt
  ): Future[Map[String, Crate.Id]] =
    existingMetadata match {
      case Some(file: EntityMetadata.File) if file.checksum == currentChecksum =>
        Future.successful(file.crates)

      case Some(directory: EntityMetadata.Directory) =>
        Future.failed(
          new IllegalArgumentException(
            s"Expected metadata for file but directory metadata for [${directory.path}] provided"
          )
        )

      case _ =>
        Future.successful(Map.empty)
    }

  def collectCratesForTargetFile(existingMetadata: EntityMetadata): Future[Map[String, Crate.Id]] =
    existingMetadata match {
      case file: EntityMetadata.File =>
        Future.successful(file.crates)

      case directory: EntityMetadata.Directory =>
        Future.failed(
          new IllegalArgumentException(
            s"Expected metadata for file but directory metadata for [${directory.path}] provided"
          )
        )
    }

  def collectCompressionForTargetFile(existingMetadata: EntityMetadata): Future[String] =
    existingMetadata match {
      case file: EntityMetadata.File =>
        Future.successful(file.compression)

      case directory: EntityMetadata.Directory =>
        Future.failed(
          new IllegalArgumentException(
            s"Expected metadata for file but directory metadata for [${directory.path}] provided"
          )
        )
    }

  def extractBaseEntityMetadata(entity: Path)(implicit ec: ExecutionContext): Future[BaseEntityMetadata] =
    PlatformMetadata.current.extractFrom(entity)

  def applyEntityMetadataTo(metadata: EntityMetadata, entity: Path)(implicit
    ec: ExecutionContext,
    defaults: PlatformMetadata.Defaults
  ): Future[Done] =
    PlatformMetadata.current.applyTo(entity, metadata)
}
