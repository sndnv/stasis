package stasis.test.specs.unit.server.security

import akka.actor.ActorSystem
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.users.User
import stasis.server.security.{DefaultResourceProvider, Permission, Resource}
import stasis.test.specs.unit.AsyncUnitSpec

import scala.util.control.NonFatal

class DefaultResourceProviderSpec extends AsyncUnitSpec {
  import DefaultResourceProviderSpec._

  private implicit val system: ActorSystem = ActorSystem(name = "DefaultResourceProviderSpec")

  private val userStore: MemoryBackend[User.Id, User] =
    MemoryBackend.untyped[User.Id, User](s"mock-user-store-${java.util.UUID.randomUUID()}")

  private val manageSelfResource = new ManageSelfResource
  private val viewPrivilegedResource = new ViewPrivilegedResource

  private val provider = new DefaultResourceProvider(
    resources = Set(viewPrivilegedResource, manageSelfResource),
    users = userStore
  )

  private val testUser = User(
    id = User.generateId(),
    isActive = true,
    limits = None,
    permissions = Set(Permission.Manage.Self)
  )

  userStore.put(testUser.id, testUser).await

  "A DefaultResourceProvider" should "successfully provide resources for authorized users" in {
    provider.provide[ManageSelfResource](testUser.id).map { resource =>
      resource should be(manageSelfResource)
    }
  }

  it should "fail to provide resources for unauthorized users" in {
    provider
      .provide[ViewPrivilegedResource](testUser.id)
      .map { response =>
        fail(s"Received unexpected response from provider: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"User [${testUser.id}] does not have permission [${viewPrivilegedResource.requiredPermission}] for resource [$viewPrivilegedResource]"
          )
      }
  }

  it should "fail to provide missing resources" in {
    provider
      .provide[ManageServiceResource](testUser.id)
      .map { response =>
        fail(s"Received unexpected response from provider: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Resource [${classOf[ManageServiceResource].getName}] requested by user [${testUser.id}] was not found"
          )
      }
  }

  it should "fail to provide resources for missing users" in {
    val otherUser = User.generateId()

    provider
      .provide[viewPrivilegedResource.type](otherUser)
      .map { response =>
        fail(s"Received unexpected response from provider: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"User [$otherUser] not found")
      }
  }
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
