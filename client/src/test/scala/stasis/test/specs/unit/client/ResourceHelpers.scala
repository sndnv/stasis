package stasis.test.specs.unit.client

import java.nio.file.{Path, Paths}

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.{ByteString, Timeout}
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.model.FileMetadata
import stasis.core.packaging.Crate

import scala.concurrent.{Await, ExecutionContext, Future}

trait ResourceHelpers {
  implicit class StringToTestResourcePath(resourcePath: String) {
    def asTestResource: Path =
      Paths.get(getClass.getResource(resourcePath).getPath)
  }

  implicit class PathWithIO(resourcePath: Path) {
    def write(content: String)(implicit mat: Materializer): Future[Done] =
      Source
        .single(ByteString(content))
        .runWith(FileIO.toPath(resourcePath))
        .map(_ => Done)(mat.executionContext)

    def content(implicit mat: Materializer): Future[String] =
      FileIO
        .fromPath(resourcePath)
        .runFold(ByteString.empty)(_ concat _)
        .map(_.utf8String)(mat.executionContext)
  }

  implicit class PathWithMetadataExtraction(resourcePath: Path) {
    def extractMetadata(
      withChecksum: BigInt,
      withCrate: Crate.Id
    )(implicit mat: Materializer, timeout: Timeout): FileMetadata =
      Await.result(
        Metadata
          .extractFileMetadata(
            file = resourcePath,
            withChecksum = withChecksum,
            withCrate = withCrate
          )(mat.executionContext),
        timeout.duration
      )

    def extractMetadata(checksum: Checksum)(implicit mat: Materializer, timeout: Timeout): FileMetadata = {
      implicit val ec: ExecutionContext = mat.executionContext

      val result = for {
        calculatedChecksum <- checksum.calculate(resourcePath)
        extractedMetadata <- Metadata
          .extractFileMetadata(
            file = resourcePath,
            withChecksum = calculatedChecksum,
            withCrate = Crate.generateId()
          )
      } yield {
        extractedMetadata
      }

      Await.result(result, timeout.duration)
    }
  }
}
