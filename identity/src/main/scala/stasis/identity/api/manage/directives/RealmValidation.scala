package stasis.identity.api.manage.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onSuccess}
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.realms.Realm

import scala.concurrent.Future

trait RealmValidation[T] extends BaseApiDirective {

  protected def log: LoggingAdapter

  protected implicit def extractor: RealmValidation.Extractor[T]

  def filterRealm[K](realm: Realm.Id, entitiesFuture: Future[Map[K, T]]): Directive1[Map[K, T]] =
    Directive { inner =>
      onSuccess(entitiesFuture) { entities =>
        inner(Tuple1(entities.filter(entity => extractor.extract(entity._2) == realm)))
      }
    }

  def validateRealm(realm: Realm.Id, entityFuture: Future[Option[T]]): Directive1[T] =
    Directive { inner =>
      onSuccess(entityFuture) {
        case Some(entity) =>
          extractor.extract(entity) match {
            case `realm` =>
              inner(Tuple1(entity))

            case unexpectedRealm: Realm.Id =>
              discardEntity {
                complete(
                  StatusCodes.BadRequest,
                  s"Expected realm [$realm] but [$unexpectedRealm] provided"
                )
              }
          }

        case None =>
          log.warning("Entity for realm [{}] was not found", realm)
          discardEntity {
            complete(StatusCodes.NotFound)
          }
      }
    }
}

object RealmValidation {
  trait Extractor[T] {
    def extract(entity: T): Realm.Id
  }
}
