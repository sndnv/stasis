package stasis.core.api.directives

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server.Directives.{extractRequest, mapResponse}
import akka.http.scaladsl.server.{Directive, Directive0}
import org.slf4j.Logger

import java.util.UUID

trait LoggingDirectives {
  protected def log: Logger

  def withLoggedRequestAndResponse: Directive0 = Directive { inner =>
    extractRequest { request =>
      val requestId = UUID.randomUUID().toString

      log.debug(
        "Received [{}] request for [{}] with ID [{}] and headers [{}]",
        request.method.value,
        request.uri.toString(),
        requestId,
        renderHeaders(request.headers)
      )

      mapResponse { response =>
        log.debugN(
          "Responding to [{}] request for [{}] with ID [{}]: [{}] with headers [{}]",
          request.method.value,
          request.uri.toString(),
          requestId,
          response.status.value,
          renderHeaders(response.headers)
        )

        response
      }(inner(()))
    }
  }

  private def renderHeaders(headers: Seq[HttpHeader]): String =
    if (headers.nonEmpty) {
      headers.map(_.toString()).mkString("\n\t", "\n\t", "\n")
    } else {
      "none"
    }
}
