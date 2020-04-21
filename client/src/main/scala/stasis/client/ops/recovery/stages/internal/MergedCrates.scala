package stasis.client.ops.recovery.stages.internal

import java.nio.file.Path

import akka.NotUsed
import akka.stream.scaladsl.{Concat, Source}
import akka.util.ByteString
import stasis.client.ops.exceptions.EntityProcessingFailure

class MergedCrates(crates: Iterable[(Path, Source[ByteString, NotUsed])]) {
  def merge(): Source[ByteString, NotUsed] =
    crates.toList.sortBy(_._1).map(_._2) match {
      case singleSource :: Nil =>
        singleSource

      case firstSource :: secondSource :: otherSources =>
        Source.combine(firstSource, secondSource, otherSources: _*)(Concat(_))

      case Nil =>
        Source.failed(new EntityProcessingFailure("Expected at least one crate but none were found"))
    }
}
