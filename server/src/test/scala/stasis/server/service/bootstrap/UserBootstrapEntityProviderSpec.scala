package stasis.server.service.bootstrap

import java.util.UUID

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import io.github.sndnv.layers.testing.UnitSpec
import stasis.server.persistence.users.MockUserStore
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.shared.model.Generators

class UserBootstrapEntityProviderSpec extends UnitSpec {
  "An UserBootstrapEntityProvider" should "provide its name and default entities" in {
    val provider = new UserBootstrapEntityProvider(MockUserStore())

    provider.name should be("users")

    provider.default should be(empty)
  }

  it should "support loading entities from config" in {
    val provider = new UserBootstrapEntityProvider(MockUserStore())

    val expectedUserId = UUID.fromString("749d8c0e-6105-4022-ae0e-39bd77752c5d")

    bootstrapConfig.getConfigList("users").asScala.map(provider.load).toList match {
      case user1 :: user2 :: Nil =>
        user1.active should be(true)
        user1.permissions should be(Set(Permission.View.Self, Permission.View.Public, Permission.Manage.Self))
        user1.limits should be(None)

        user2.id should be(expectedUserId)
        user2.active should be(true)
        user2.permissions should be(
          Set(
            Permission.View.Self,
            Permission.View.Privileged,
            Permission.View.Public,
            Permission.View.Service,
            Permission.Manage.Self,
            Permission.Manage.Privileged,
            Permission.Manage.Service
          )
        )
        user2.limits should be(
          Some(
            User.Limits(
              maxDevices = 10,
              maxCrates = 100000,
              maxStorage = 536870912000L,
              maxStoragePerCrate = 1073741824L,
              maxRetention = 90.days,
              minRetention = 3.days
            )
          )
        )

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support validating entities" in {
    val provider = new UserBootstrapEntityProvider(MockUserStore())

    val validUsers = Seq(
      Generators.generateUser,
      Generators.generateUser,
      Generators.generateUser
    )

    val sharedId1 = User.generateId()
    val sharedId2 = User.generateId()

    val invalidUsers = Seq(
      Generators.generateUser.copy(id = sharedId1),
      Generators.generateUser.copy(id = sharedId1),
      Generators.generateUser.copy(id = sharedId2),
      Generators.generateUser.copy(id = sharedId2)
    )

    noException should be thrownBy provider.validate(validUsers).await

    val e = provider.validate(invalidUsers).failed.await

    e.getMessage should (be(s"Duplicate values [$sharedId1,$sharedId2] found for field [id] in [User]") or be(
      s"Duplicate values [$sharedId2,$sharedId1] found for field [id] in [User]"
    ))
  }

  it should "support creating entities" in {
    val store = MockUserStore()
    val provider = new UserBootstrapEntityProvider(store)

    for {
      existingBefore <- store.view().list()
      _ <- provider.create(Generators.generateUser)
      existingAfter <- store.view().list()
    } yield {
      existingBefore should be(empty)
      existingAfter should not be empty
    }
  }

  it should "support rendering entities" in {
    val provider = new UserBootstrapEntityProvider(MockUserStore())

    val permissions: Set[Permission] = Set(
      Permission.View.Self,
      Permission.View.Privileged,
      Permission.View.Public,
      Permission.View.Service,
      Permission.Manage.Self,
      Permission.Manage.Privileged,
      Permission.Manage.Service
    )

    val userWithLimits = Generators.generateUser.copy(
      limits = Some(
        User.Limits(
          maxDevices = 1,
          maxCrates = 2,
          maxStorage = 3,
          maxStoragePerCrate = 4,
          maxRetention = 5.millis,
          minRetention = 6.millis
        )
      ),
      permissions = permissions
    )

    provider.render(userWithLimits, withPrefix = "") should be(
      s"""
         |  user:
         |    id:                      ${userWithLimits.id}
         |    salt:                    ***
         |    active:                  ${userWithLimits.active}
         |    limits:
         |      max-devices:           1
         |      max-crates:            2
         |      max-storage:           3
         |      max-storage-per-crate: 4
         |      max-retention:         5 milliseconds
         |      min-retention:         6 milliseconds
         |    permissions:             manage-service, view-public, view-service, view-privileged, view-self, manage-privileged, manage-self
         |    created:                 ${userWithLimits.created.toString}
         |    updated:                 ${userWithLimits.updated.toString}""".stripMargin
    )

    val userWithoutLimits = Generators.generateUser.copy(limits = None, permissions = Set.empty)

    provider.render(userWithoutLimits, withPrefix = "") should be(
      s"""
         |  user:
         |    id:                      ${userWithoutLimits.id}
         |    salt:                    ***
         |    active:                  ${userWithoutLimits.active}
         |    limits:
         |      max-devices:           -
         |      max-crates:            -
         |      max-storage:           -
         |      max-storage-per-crate: -
         |      max-retention:         -
         |      min-retention:         -
         |    permissions:             none
         |    created:                 ${userWithoutLimits.created.toString}
         |    updated:                 ${userWithoutLimits.updated.toString}""".stripMargin
    )
  }

  it should "support extracting entity IDs" in {
    val provider = new UserBootstrapEntityProvider(MockUserStore())

    val user = Generators.generateUser

    provider.extractId(user) should be(user.id.toString)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "UserBootstrapEntityProviderSpec"
  )

  private val bootstrapConfig = ConfigFactory.load("bootstrap-unit.conf").getConfig("bootstrap")
}
