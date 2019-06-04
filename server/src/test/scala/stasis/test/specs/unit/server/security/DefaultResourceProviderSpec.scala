package stasis.test.specs.unit.server.security

import scala.reflect.ClassTag
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.security.{CurrentUser, DefaultResourceProvider, Resource}
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec

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

  private implicit val currentUser: CurrentUser = CurrentUser(testUser.id)

  userStore.put(testUser.id, testUser).await

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
      .recover {
        case NonFatal(e) =>
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
      .recover {
        case NonFatal(e) =>
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
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"User [${otherUser.id}] not found")
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
