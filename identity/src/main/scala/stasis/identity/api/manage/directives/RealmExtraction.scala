package stasis.identity.api.manage.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive1}
import stasis.identity.api.directives.BaseApiDirective
import stasis.identity.model.realms.{Realm, RealmStoreView}

trait RealmExtraction extends BaseApiDirective {

  protected def log: LoggingAdapter

  protected def realmStore: RealmStoreView

  def extractRealm(realmId: Realm.Id): Directive1[Realm] =
    Directive { inner =>
      onSuccess(realmStore.get(realmId)) {
        case Some(realm) =>
          inner(Tuple1(realm))

        case None =>
          log.warning("Requested realm [{}] was not found", realmId)
          discardEntity {
            complete(StatusCodes.NotFound)
          }
      }
    }
}
