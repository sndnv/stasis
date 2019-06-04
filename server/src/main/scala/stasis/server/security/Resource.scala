package stasis.server.security

import stasis.shared.security.Permission

trait Resource {
  def requiredPermission: Permission
}
