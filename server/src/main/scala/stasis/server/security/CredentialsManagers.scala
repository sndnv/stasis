package stasis.server.security

import stasis.server.security.devices.DeviceCredentialsManager
import stasis.server.security.users.UserCredentialsManager

trait CredentialsManagers {
  def users: UserCredentialsManager
  def devices: DeviceCredentialsManager
}

object CredentialsManagers {
  final case class Default(
    override val users: UserCredentialsManager,
    override val devices: DeviceCredentialsManager
  ) extends CredentialsManagers
}
