package stasis.client.tracking

trait ServerTracker {
  def reachable(server: String): Unit
  def unreachable(server: String): Unit
}
