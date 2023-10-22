package stasis.core.persistence

import org.apache.pekko.Done

import scala.concurrent.Future

final case class StoreInitializationResult[S](store: S, init: () => Future[Done])
