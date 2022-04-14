package stasis.core.api.directives

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive0}
import akka.stream.scaladsl.Sink
import akka.util.ByteString

trait EntityDiscardingDirectives {
  def discardEntity: Directive0 =
    Directive { inner =>
      extractRequestEntity { entity =>
        extractActorSystem { implicit system =>
          val _ = entity.dataBytes.runWith(Sink.cancelled[ByteString])
          inner(())
        }
      }
    }

  def consumeEntity: Directive0 =
    Directive { inner =>
      extractRequestEntity { entity =>
        extractActorSystem { implicit system =>
          val result = entity.dataBytes.runWith(Sink.ignore)
          onSuccess(result) { _ =>
            inner(())
          }
        }
      }
    }
}
