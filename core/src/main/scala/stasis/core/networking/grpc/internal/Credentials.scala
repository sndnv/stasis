package stasis.core.networking.grpc.internal

import java.nio.charset.StandardCharsets

import akka.http.scaladsl.model.HttpRequest
import akka.parboiled2.util.Base64
import stasis.core.networking.exceptions.CredentialsFailure
import stasis.core.networking.grpc.GrpcCredentials

import scala.concurrent.Future
import scala.util.matching.Regex

object Credentials {
  final val HEADER: String = "authorization"

  private val creds: Regex = """^(Bearer|Basic) (.+)$""".r

  def marshal(credentials: GrpcCredentials): String =
    credentials match {
      case GrpcCredentials.Psk(node, secret) =>
        val encoded = Base64.rfc2045().encodeToString(s"$node:$secret".getBytes(StandardCharsets.UTF_8), false)
        s"Basic $encoded"

      case GrpcCredentials.Jwt(token) =>
        s"Bearer $token"
    }

  def unmarshal(rawCredentials: String): Either[CredentialsFailure, GrpcCredentials] =
    rawCredentials match {
      case creds(scheme, credentials) =>
        scheme match {
          case "Bearer" =>
            Right(GrpcCredentials.Jwt(token = credentials))

          case "Basic" =>
            new String(
              Base64.rfc2045.decodeFast(credentials),
              StandardCharsets.UTF_8
            ).split(":").toList match {
              case node :: secret :: Nil =>
                Right(GrpcCredentials.Psk(node, secret))

              case _ =>
                Left(CredentialsFailure("Failed to extract basic auth credentials"))
            }
        }

      case _ =>
        Left(CredentialsFailure("Unexpected credentials format encountered"))
    }

  def extract(request: HttpRequest): Future[GrpcCredentials] =
    request.headers.find(_.is(HEADER)) match {
      case Some(header) =>
        unmarshal(header.value()) match {
          case Right(credentials) => Future.successful(credentials)
          case Left(failure)      => Future.failed(failure)
        }

      case None =>
        Future.failed(CredentialsFailure("No credentials provided"))
    }
}
