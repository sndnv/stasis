package stasis.shared.api.responses

final case class Ping(id: java.util.UUID)

object Ping {
  def apply(): Ping = Ping(id = java.util.UUID.randomUUID())
}
