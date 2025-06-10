package stasis.layers.telemetry

trait ApplicationInformation {
  def name: String
  def version: String
  def buildTime: Long

  def asString(): String =
    s"$name;$version;${buildTime.toString}"
}

object ApplicationInformation {
  def none: ApplicationInformation = new ApplicationInformation {
    override val name: String = "none"
    override val version: String = "none"
    override val buildTime: Long = 0L
  }
}
