package stasis.core.persistence

import akka.Done

import scala.concurrent.Future

final case class StoreInitializationResult[S](store: S, init: () => Future[Done])
