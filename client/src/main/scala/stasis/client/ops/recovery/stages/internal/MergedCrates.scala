package stasis.client.ops.recovery.stages.internal

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Concat, Source}
import org.apache.pekko.util.ByteString
import stasis.client.ops.exceptions.EntityMergeFailure

import java.nio.file.Path
import scala.concurrent.ExecutionContext

class MergedCrates(crates: Iterable[(Int, Path, Source[ByteString, NotUsed])]) {
  def merge(onPartProcessed: () => Unit)(implicit ec: ExecutionContext): Source[ByteString, NotUsed] =
    crates.toList.sortBy(_._1).map { case (_, _, source) =>
      source
        .watchTermination() { case (prevMatValue, result) =>
          result.foreach(_ => onPartProcessed())
          prevMatValue
        }
    } match {
      case singleSource :: Nil =>
        singleSource

      case firstSource :: secondSource :: otherSources =>
        Source.combine(firstSource, secondSource, otherSources: _*)(Concat(_, detachedInputs = false))

      case Nil =>
        Source.failed(new EntityMergeFailure("Expected at least one crate but none were found"))
    }
}
