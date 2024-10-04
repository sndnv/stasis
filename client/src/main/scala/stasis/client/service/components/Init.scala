package stasis.client.service.components

import java.io.Console

import scala.concurrent.Future
import scala.concurrent.Promise

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.Http

import stasis.layers.security.tls.EndpointContext

trait Init {
  def credentials(): Future[(String, Array[Char])]
}

object Init {
  def apply(base: Base, startup: Future[Done], console: Option[Console]): Future[Init] =
    console match {
      case Some(console) =>
        base.log.debug("Console available; using stdin-based initialization...")
        viaStdIn(console)

      case None =>
        base.log.debug("Console not available; using API-based initialization...")
        viaApi(base, startup)
    }

  def viaStdIn(console: Console): Future[Init] =
    Future.successful(
      new Init {
        override def credentials(): Future[(String, Array[Char])] = init.ViaStdIn.retrieve(console)
      }
    )

  def viaApi(base: Base, startup: Future[Done]): Future[Init] = {
    import base._

    for {
      interface <- rawConfig.getString("api.init.interface").future
      port <- rawConfig.getInt("api.init.port").future
      context <- EndpointContext(rawConfig.getConfig("api.init.context")).future
    } yield {
      val credentialsPromise = Promise[(String, Array[Char])]()

      log.debug("Client init API starting on [{}:{}]...", interface, port)

      import EndpointContext._

      val binding = Http()
        .newServerAt(interface = interface, port = port)
        .withContext(context = context)
        .bindFlow(
          handlerFlow = init.ViaApi.routes(credentials = credentialsPromise, startup = startup)
        )

      startup.onComplete { _ =>
        binding.flatMap {
          log.debug("Client init API stopping...")
          _.terminate(terminationDelay).map { _ =>
            Done
          }
        }
      }

      new Init {
        override def credentials(): Future[(String, Array[Char])] = credentialsPromise.future
      }
    }
  }
}
