package stasis.server.security

import java.time.Instant

import scala.reflect.ClassTag
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.server.persistence.users.UserStore
import stasis.server.security.CurrentUser
import stasis.server.security.DefaultResourceProvider
import stasis.server.security.Resource
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.server.persistence.users.MockUserStore

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

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DefaultResourceProviderSpec"
  )

  private val userStore: UserStore = MockUserStore()

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
    permissions = Set(Permission.Manage.Self),
    created = Instant.now(),
    updated = Instant.now()
  )

  private implicit val currentUser: CurrentUser = CurrentUser(testUser.id)

  userStore.manage().put(testUser).await
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
