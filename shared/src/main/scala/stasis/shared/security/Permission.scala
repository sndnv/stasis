package stasis.shared.security

sealed trait Permission

object Permission {
  sealed trait View extends Permission
  object View {
    case object Self extends View
    case object Privileged extends View
    case object Service extends View
  }

  sealed trait Manage extends Permission
  object Manage {
    case object Self extends Manage
    case object Privileged extends Manage
    case object Service extends Manage
  }
}
