package stasis.test.specs.unit.server.model.mocks

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext
import stasis.server.model.users.UserStore
import stasis.shared.model.users.User

object MockUserStore {
  def apply(
    userSaltSize: Int = 8
  )(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): UserStore = {
    val backend: MemoryStore[User.Id, User] =
      MemoryStore[User.Id, User](
        s"mock-user-store-${java.util.UUID.randomUUID()}"
      )

    UserStore(
      userSaltSize = userSaltSize,
      backend = backend
    )(system.executionContext)
  }
}
