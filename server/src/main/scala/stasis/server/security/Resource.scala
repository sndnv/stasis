package stasis.server.security

trait Resource {
  def requiredPermission: Permission
}
