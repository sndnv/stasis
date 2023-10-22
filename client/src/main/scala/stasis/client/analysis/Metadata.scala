package stasis.client.analysis

import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import stasis.client.compression.Compression
import stasis.client.model.{EntityMetadata, SourceEntity, TargetEntity}
import stasis.core.packaging.Crate

import java.nio.file.attribute.{FileTime, PosixFileAttributeView, PosixFileAttributes, PosixFilePermissions}
import java.nio.file.{Files, LinkOption, Path}
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.{ExecutionContext, Future}

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
    collectCrates: BigInt => Future[Map[Path, Crate.Id]],
    collectCompression: () => Future[String]
  )(implicit mat: Materializer): Future[EntityMetadata] =
    if (currentMetadata.isDirectory) {
      Future.successful(
        EntityMetadata.Directory(
          path = currentMetadata.path,
          link = currentMetadata.link,
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
          path = currentMetadata.path,
          size = currentMetadata.attributes.size,
          link = currentMetadata.link,
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
  ): Future[Map[Path, Crate.Id]] =
    existingMetadata match {
      case Some(file: EntityMetadata.File) if file.checksum == currentChecksum =>
        Future.successful(file.crates)

      case Some(directory: EntityMetadata.Directory) =>
        Future.failed(
          new IllegalArgumentException(
            s"Expected metadata for file but directory metadata for [${directory.path.toString}] provided"
          )
        )

      case _ =>
        Future.successful(Map.empty)
    }

  def collectCratesForTargetFile(existingMetadata: EntityMetadata): Future[Map[Path, Crate.Id]] =
    existingMetadata match {
      case file: EntityMetadata.File =>
        Future.successful(file.crates)

      case directory: EntityMetadata.Directory =>
        Future.failed(
          new IllegalArgumentException(
            s"Expected metadata for file but directory metadata for [${directory.path.toString}] provided"
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
            s"Expected metadata for file but directory metadata for [${directory.path.toString}] provided"
          )
        )
    }

  def extractBaseEntityMetadata(
    entity: Path
  )(implicit ec: ExecutionContext): Future[BaseEntityMetadata] =
    Future {
      val attributes = Files.readAttributes(entity, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)

      val isDirectory = attributes.isDirectory
      val link = if (Files.isSymbolicLink(entity)) Some(Files.readSymbolicLink(entity)) else None
      val isHidden = Files.isHidden(entity)
      val created = attributes.creationTime.toInstant.truncatedTo(ChronoUnit.SECONDS)
      val updated = attributes.lastModifiedTime.toInstant.truncatedTo(ChronoUnit.SECONDS)
      val owner = attributes.owner.getName
      val group = attributes.group.getName
      val permissions = PosixFilePermissions.toString(attributes.permissions())

      BaseEntityMetadata(
        path = entity,
        isDirectory = isDirectory,
        link = link,
        isHidden = isHidden,
        created = created,
        updated = updated,
        owner = owner,
        group = group,
        permissions = permissions,
        attributes = attributes
      )
    }

  def applyEntityMetadataTo(metadata: EntityMetadata, entity: Path)(implicit ec: ExecutionContext): Future[Done] =
    Future {
      val attributes = Files.getFileAttributeView(entity, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS)

      attributes.setPermissions(PosixFilePermissions.fromString(metadata.permissions))

      val lookupService = entity.getFileSystem.getUserPrincipalLookupService

      val owner = lookupService.lookupPrincipalByName(metadata.owner)
      val group = lookupService.lookupPrincipalByGroupName(metadata.group)

      attributes.setOwner(owner)
      attributes.setGroup(group)

      attributes.setTimes(
        /* lastModifiedTime */ FileTime.from(metadata.updated),
        /* lastAccessTime */ FileTime.from(Instant.now()),
        /* createTime */ FileTime.from(metadata.created)
      )

      Done
    }

  final case class BaseEntityMetadata(
    path: Path,
    isDirectory: Boolean,
    link: Option[Path],
    isHidden: Boolean,
    created: Instant,
    updated: Instant,
    owner: String,
    group: String,
    permissions: String,
    attributes: PosixFileAttributes
  )
}
