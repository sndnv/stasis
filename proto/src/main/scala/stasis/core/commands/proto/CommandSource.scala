package stasis.core.commands.proto

sealed trait CommandSource {
  lazy val name: String = getClass.getSimpleName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase
}

object CommandSource {
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def apply(source: String): CommandSource =
    source.trim.toLowerCase match {
      case "service" => Service
      case "user"    => User
      case other     => throw new IllegalArgumentException(s"Unexpected source provided: [$other]")
    }

  final case object Service extends CommandSource
  final case object User extends CommandSource
}
