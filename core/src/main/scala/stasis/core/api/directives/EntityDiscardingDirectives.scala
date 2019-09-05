package stasis.core.api.directives

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive0}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString

trait EntityDiscardingDirectives {
  protected implicit def mat: Materializer

  def discardEntity: Directive0 =
    Directive { inner =>
      extractRequestEntity { entity =>
        val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
        inner(())
      }
    }

  def consumeEntity: Directive0 =
    Directive { inner =>
      extractRequestEntity { entity =>
        val result = entity.dataBytes.runWith(Sink.ignore)
        onSuccess(result) { _ =>
          inner(())
        }
      }
    }
}
