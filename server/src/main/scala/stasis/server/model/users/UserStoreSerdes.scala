package stasis.server.model.users

import java.nio.charset.StandardCharsets

import org.apache.pekko.util.ByteString
import play.api.libs.json.Json
import stasis.core.persistence.backends.KeyValueBackend
import stasis.shared.model.users.User

object UserStoreSerdes extends KeyValueBackend.Serdes[User.Id, User] {
  import stasis.shared.api.Formats._

  override implicit def serializeKey: User.Id => String =
    _.toString

  override implicit def deserializeKey: String => User.Id =
    java.util.UUID.fromString

  override implicit def serializeValue: User => ByteString =
    user => ByteString(Json.toJson(user).toString, StandardCharsets.UTF_8)

  override implicit def deserializeValue: ByteString => User =
    user => Json.parse(user.decodeString(StandardCharsets.UTF_8)).as[User]
}
