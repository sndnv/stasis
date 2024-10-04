package stasis.core.persistence

import scala.concurrent.Future

import org.apache.pekko.Done

final case class StoreInitializationResult[S](store: S, init: () => Future[Done])
