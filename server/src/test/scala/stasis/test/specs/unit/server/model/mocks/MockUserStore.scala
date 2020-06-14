package stasis.test.specs.unit.server.model.mocks

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.users.UserStore
import stasis.shared.model.users.User

object MockUserStore {
  def apply(userSaltSize: Int = 8)(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): UserStore = {
    val backend: MemoryBackend[User.Id, User] =
      MemoryBackend[User.Id, User](
        s"mock-user-store-${java.util.UUID.randomUUID()}"
      )

    UserStore(
      userSaltSize = userSaltSize,
      backend = backend
    )(system.executionContext)
  }
}
