package stasis.client.ops.recovery.stages.internal

import akka.NotUsed
import akka.stream.scaladsl.{Concat, Source}
import akka.util.ByteString
import stasis.client.ops.exceptions.EntityProcessingFailure

import java.nio.file.Path

class MergedCrates(crates: Iterable[(Int, Path, Source[ByteString, NotUsed])]) {
  def merge(): Source[ByteString, NotUsed] =
    crates.toList.sortBy(_._1).map(_._3) match {
      case singleSource :: Nil =>
        singleSource

      case firstSource :: secondSource :: otherSources =>
        Source.combine(firstSource, secondSource, otherSources: _*)(Concat(_, detachedInputs = false))

      case Nil =>
        Source.failed(new EntityProcessingFailure("Expected at least one crate but none were found"))
    }
}
