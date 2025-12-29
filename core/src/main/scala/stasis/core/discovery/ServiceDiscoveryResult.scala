package stasis.core.discovery

sealed trait ServiceDiscoveryResult {
  def asString: String
}

object ServiceDiscoveryResult {
  case object KeepExisting extends ServiceDiscoveryResult {
    override lazy val asString: String = "result=keep-existing"
  }

  final case class SwitchTo(
    endpoints: Endpoints,
    recreateExisting: Boolean
  ) extends ServiceDiscoveryResult {
    override lazy val asString: String =
      s"result=switch-to," +
        s"endpoints=${endpoints.asString}," +
        s"recreate-existing=${recreateExisting.toString}"
  }

  final case class Endpoints(
    api: ServiceApiEndpoint.Api,
    core: ServiceApiEndpoint.Core,
    discovery: ServiceApiEndpoint.Discovery
  ) {
    lazy val asString: String = Seq(api.id, core.id, discovery.id).mkString(";")
  }
}
