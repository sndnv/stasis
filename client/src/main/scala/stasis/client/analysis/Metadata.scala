package stasis.client.analysis

import java.nio.file.attribute.{FileTime, PosixFileAttributeView, PosixFileAttributes, PosixFilePermissions}
import java.nio.file.{Files, LinkOption, Path}
import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.Done
import akka.stream.Materializer
import stasis.client.model.{FileMetadata, SourceFile, TargetFile}
import stasis.core.packaging.Crate

import scala.concurrent.{ExecutionContext, Future}

object Metadata {
  def collectSource(
    checksum: Checksum,
    file: Path,
    existingMetadata: Option[FileMetadata]
  )(implicit mat: Materializer): Future[SourceFile] = {
    implicit val ec: ExecutionContext = mat.executionContext

    for {
      currentChecksum <- checksum.calculate(file)
      currentMetadata <- extractFileMetadata(
        file = file,
        withChecksum = currentChecksum,
        withCrate = existingMetadata match {
          case Some(metadata) if metadata.checksum == currentChecksum => metadata.crate
          case _                                                      => Crate.generateId()
        }
      )
    } yield {
      SourceFile(
        path = file,
        existingMetadata = existingMetadata,
        currentMetadata = currentMetadata
      )
    }
  }

  def collectTarget(
    checksum: Checksum,
    file: Path,
    existingMetadata: FileMetadata
  )(implicit mat: Materializer): Future[TargetFile] = {
    implicit val ec: ExecutionContext = mat.executionContext

    if (Files.exists(file)) {
      for {
        currentChecksum <- checksum.calculate(file)
        currentMetadata <- extractFileMetadata(
          file = file,
          withChecksum = currentChecksum,
          withCrate = existingMetadata.crate
        )
      } yield {
        TargetFile(
          path = file,
          existingMetadata = existingMetadata,
          currentMetadata = Some(currentMetadata)
        )
      }
    } else {
      Future.successful(
        TargetFile(
          path = file,
          existingMetadata = existingMetadata,
          currentMetadata = None
        )
      )
    }
  }

  def extractFileMetadata(
    file: Path,
    withChecksum: BigInt,
    withCrate: Crate.Id
  )(implicit ec: ExecutionContext): Future[FileMetadata] =
    Future {
      val attributes = Files.readAttributes(file, classOf[PosixFileAttributes], LinkOption.NOFOLLOW_LINKS)

      FileMetadata(
        path = file,
        size = attributes.size,
        link = if (Files.isSymbolicLink(file)) Some(Files.readSymbolicLink(file)) else None,
        isHidden = Files.isHidden(file),
        created = attributes.creationTime.toInstant.truncatedTo(ChronoUnit.SECONDS),
        updated = attributes.lastModifiedTime.toInstant.truncatedTo(ChronoUnit.SECONDS),
        owner = attributes.owner.getName,
        group = attributes.group.getName,
        permissions = PosixFilePermissions.toString(attributes.permissions()),
        checksum = withChecksum,
        crate = withCrate
      )
    }

  def applyFileMetadata(metadata: FileMetadata)(implicit ec: ExecutionContext): Future[Done] =
    applyFileMetadataTo(metadata, metadata.path)

  def applyFileMetadataTo(metadata: FileMetadata, file: Path)(implicit ec: ExecutionContext): Future[Done] = Future {
    val attributes = Files.getFileAttributeView(file, classOf[PosixFileAttributeView], LinkOption.NOFOLLOW_LINKS)

    attributes.setPermissions(PosixFilePermissions.fromString(metadata.permissions))

    val lookupService = file.getFileSystem.getUserPrincipalLookupService

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
}
