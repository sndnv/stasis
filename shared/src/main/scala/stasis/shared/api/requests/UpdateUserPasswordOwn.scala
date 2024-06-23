package stasis.shared.api.requests

final case class UpdateUserPasswordOwn(currentPassword: String, newPassword: String)
