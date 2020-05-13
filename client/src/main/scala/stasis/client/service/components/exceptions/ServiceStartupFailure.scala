package stasis.client.service.components.exceptions

class ServiceStartupFailure(val cause: String, val message: String) extends Exception(message)

object ServiceStartupFailure {
  def apply(cause: String, e: Throwable): ServiceStartupFailure =
    new ServiceStartupFailure(
      cause = cause,
      message = s"${e.getClass.getSimpleName}: ${e.getMessage}"
    )

  def credentials(e: Throwable): ServiceStartupFailure = ServiceStartupFailure(cause = "credentials", e = e)
  def file(e: Throwable): ServiceStartupFailure = ServiceStartupFailure(cause = "file", e = e)
  def token(e: Throwable): ServiceStartupFailure = ServiceStartupFailure(cause = "token", e = e)
  def config(e: Throwable): ServiceStartupFailure = ServiceStartupFailure(cause = "config", e = e)
  def api(e: Throwable): ServiceStartupFailure = ServiceStartupFailure(cause = "api", e = e)
  def unknown(e: Throwable): ServiceStartupFailure = ServiceStartupFailure(cause = "unknown", e = e)
}
