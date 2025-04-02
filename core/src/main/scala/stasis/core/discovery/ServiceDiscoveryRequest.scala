package stasis.core.discovery

final case class ServiceDiscoveryRequest(
  isInitialRequest: Boolean,
  attributes: Map[String, String]
) {
  lazy val id: String = attributes.toList
    .sortBy(_._1)
    .map { case (k, v) => s"$k=$v" }
    .mkString("::")
}
