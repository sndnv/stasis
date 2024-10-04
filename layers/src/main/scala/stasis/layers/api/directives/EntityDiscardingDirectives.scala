package stasis.layers.api.directives

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Directive
import org.apache.pekko.http.scaladsl.server.Directive0

import stasis.layers.streaming.Operators.ExtendedSource

trait EntityDiscardingDirectives {
  def discardEntity: Directive0 =
    Directive { inner =>
      extractRequestEntity { entity =>
        extractActorSystem { implicit system =>
          onSuccess(entity.dataBytes.cancelled()) { _ =>
            inner(())
          }
        }
      }
    }

  def consumeEntity: Directive0 =
    Directive { inner =>
      extractRequestEntity { entity =>
        extractActorSystem { implicit system =>
          onSuccess(entity.dataBytes.ignored()) { _ =>
            inner(())
          }
        }
      }
    }
}
