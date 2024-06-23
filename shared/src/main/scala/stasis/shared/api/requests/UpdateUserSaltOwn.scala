package stasis.shared.api.requests

final case class UpdateUserSaltOwn(currentPassword: String, newSalt: String)
