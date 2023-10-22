package stasis.core.api.directives

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{Directive, Directive0}
import stasis.core.streaming.Operators.ExtendedSource

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
