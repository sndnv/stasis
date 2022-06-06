package stasis.test.specs.unit.server.security

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.telemetry.TelemetryContext
import stasis.server.model.users.UserStore
import stasis.server.security.{CurrentUser, DefaultResourceProvider, Resource}
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.reflect.ClassTag
import scala.util.control.NonFatal

class DefaultResourceProviderSpec extends AsyncUnitSpec {
  import DefaultResourceProviderSpec._

  "A DefaultResourceProvider" should "successfully provide resources for authorized users" in {
    provider.provide[ManageSelfResource].map { resource =>
      resource should be(manageSelfResource)
    }
  }

  it should "fail to provide resources for unauthorized users" in {
    provider
      .provide[ViewPrivilegedResource]
      .map { response =>
        fail(s"Received unexpected response from provider: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"User [${testUser.id}] does not have permission [${viewPrivilegedResource.requiredPermission}] for resource [$viewPrivilegedResource]"
        )
      }
  }

  it should "fail to provide missing resources" in {
    provider
      .provide[ManageServiceResource]
      .map { response =>
        fail(s"Received unexpected response from provider: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(
          s"Resource [${classOf[ManageServiceResource].getName}] requested by user [${testUser.id}] was not found"
        )
      }
  }

  it should "fail to provide resources for missing users" in {
    val otherUser: CurrentUser = CurrentUser(User.generateId())

    provider
      .provide[viewPrivilegedResource.type](otherUser, implicitly[ClassTag[viewPrivilegedResource.type]])
      .map { response =>
        fail(s"Received unexpected response from provider: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(s"User [${otherUser.id}] not found")
      }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultResourceProviderSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val userStore: UserStore = UserStore(
    userSaltSize = 8,
    backend = MemoryBackend[User.Id, User](s"mock-user-store-${java.util.UUID.randomUUID()}")
  )

  private val manageSelfResource = new ManageSelfResource
  private val viewPrivilegedResource = new ViewPrivilegedResource

  private val provider = new DefaultResourceProvider(
    resources = Set(viewPrivilegedResource, manageSelfResource),
    users = userStore.view()
  )

  private val testUser = User(
    id = User.generateId(),
    salt = "test-salt",
    active = true,
    limits = None,
    permissions = Set(Permission.Manage.Self)
  )

  private implicit val currentUser: CurrentUser = CurrentUser(testUser.id)

  userStore.manage().create(testUser).await
}

object DefaultResourceProviderSpec {
  private class ViewPrivilegedResource extends Resource {
    override def requiredPermission: Permission = Permission.View.Privileged
  }

  private class ManageSelfResource extends Resource {
    override def requiredPermission: Permission = Permission.Manage.Self
  }

  private class ManageServiceResource extends Resource {
    override def requiredPermission: Permission = Permission.Manage.Service
  }
}
