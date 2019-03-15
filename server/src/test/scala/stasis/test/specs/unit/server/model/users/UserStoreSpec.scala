package stasis.test.specs.unit.server.model.users

import akka.Done
import akka.actor.ActorSystem
import stasis.server.model.users.User
import stasis.server.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.mocks.MockUserStore

import scala.util.control.NonFatal

class UserStoreSpec extends AsyncUnitSpec {

  private implicit val system: ActorSystem = ActorSystem(name = "UserStoreSpec")

  private val mockUser = User(
    id = User.generateId(),
    isActive = true,
    limits = None,
    permissions = Set.empty
  )

  private val self = User.generateId()

  "A UserStore" should "provide a view resource (privileged)" in {
    val store = new MockUserStore()
    store.view().requiredPermission should be(Permission.View.Privileged)
  }

  it should "return existing users via view resource (privileged)" in {
    val store = new MockUserStore()

    store.manage().create(mockUser).await

    store.view().get(mockUser.id).map(result => result should be(Some(mockUser)))
  }

  it should "return a list of users via view resource (privileged)" in {
    val store = new MockUserStore()

    store.manage().create(mockUser).await
    store.manage().create(mockUser.copy(id = User.generateId())).await
    store.manage().create(mockUser.copy(id = User.generateId())).await

    store.view().list().map { result =>
      result.size should be(3)
      result.get(mockUser.id) should be(Some(mockUser))
    }
  }

  it should "provide a view resource (self)" in {
    val store = new MockUserStore()
    store.viewSelf().requiredPermission should be(Permission.View.Self)
  }

  it should "return existing users for current user via view resource (self)" in {
    val store = new MockUserStore()

    val ownUser = mockUser.copy(id = self)
    store.manage().create(ownUser).await

    store.viewSelf().get(ownUser.id).map(result => result should be(Some(ownUser)))
  }

  it should "provide management resource (privileged)" in {
    val store = new MockUserStore()
    store.manage().requiredPermission should be(Permission.Manage.Privileged)
  }

  it should "allow creating users via management resource (privileged)" in {
    val store = new MockUserStore()

    for {
      createResult <- store.manage().create(mockUser)
      getResult <- store.view().get(mockUser.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockUser))
    }
  }

  it should "allow updating users via management resource (privileged)" in {
    val store = new MockUserStore()

    val updatedIsActive = false

    for {
      createResult <- store.manage().create(mockUser)
      getResult <- store.view().get(mockUser.id)
      updateResult <- store.manage().update(mockUser.copy(isActive = updatedIsActive))
      updatedGetResult <- store.view().get(mockUser.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockUser))
      updateResult should be(Done)
      updatedGetResult should be(Some(mockUser.copy(isActive = updatedIsActive)))
    }
  }

  it should "allow deleting users via management resource (privileged)" in {
    val store = new MockUserStore()

    for {
      createResult <- store.manage().create(mockUser)
      getResult <- store.view().get(mockUser.id)
      deleteResult <- store.manage().delete(mockUser.id)
      deletedGetResult <- store.view().get(mockUser.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(mockUser))
      deleteResult should be(true)
      deletedGetResult should be(None)
    }
  }

  it should "provide management resource (self)" in {
    val store = new MockUserStore()
    store.manageSelf().requiredPermission should be(Permission.Manage.Self)
  }

  it should "allow deactivating own user via management resource (self)" in {
    val store = new MockUserStore()

    val ownUser = mockUser.copy(id = self)

    for {
      createResult <- store.manage().create(ownUser)
      getResult <- store.viewSelf().get(ownUser.id)
      updateResult <- store.manageSelf().deactivate(self)
      updatedGetResult <- store.viewSelf().get(ownUser.id)
    } yield {
      createResult should be(Done)
      getResult should be(Some(ownUser))
      updateResult should be(Done)
      updatedGetResult should be(Some(ownUser.copy(isActive = false)))
    }
  }

  it should "fail to deactivate a missing user via management resource (self)" in {
    val store = new MockUserStore()

    store
      .manageSelf()
      .deactivate(self)
      .map { response =>
        fail(s"Received unexpected response from store: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(
            s"Expected user [$self] not found"
          )
      }
  }
}
