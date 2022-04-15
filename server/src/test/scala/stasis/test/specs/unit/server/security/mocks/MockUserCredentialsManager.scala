package stasis.test.specs.unit.server.security.mocks

import stasis.server.security.users.UserCredentialsManager
import stasis.shared.model.users.User

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class MockUserCredentialsManager(withResult: Try[UserCredentialsManager.Result]) extends UserCredentialsManager {
  private val resourceOwnersCreatedRef: AtomicInteger = new AtomicInteger(0)
  private val resourceOwnersActivatedRef: AtomicInteger = new AtomicInteger(0)
  private val resourceOwnersDeactivatedRef: AtomicInteger = new AtomicInteger(0)
  private val resourceOwnerPasswordsSetRef: AtomicInteger = new AtomicInteger(0)
  private val notFoundRef: AtomicInteger = new AtomicInteger(0)
  private val conflictsRef: AtomicInteger = new AtomicInteger(0)
  private val failuresRef: AtomicInteger = new AtomicInteger(0)
  private val latestPasswordRef: AtomicReference[String] = new AtomicReference[String]()

  override def id: String = "MockUserCredentialsManager"

  override def createResourceOwner(user: User, username: String, rawPassword: String): Future[UserCredentialsManager.Result] = {
    val _ = resourceOwnersCreatedRef.incrementAndGet()
    latestPasswordRef.set(rawPassword)
    result()
  }

  override def activateResourceOwner(user: User.Id): Future[UserCredentialsManager.Result] = {
    val _ = resourceOwnersActivatedRef.incrementAndGet()
    result()
  }

  override def deactivateResourceOwner(user: User.Id): Future[UserCredentialsManager.Result] = {
    val _ = resourceOwnersDeactivatedRef.incrementAndGet()
    result()
  }

  override def setResourceOwnerPassword(user: User.Id, rawPassword: String): Future[UserCredentialsManager.Result] = {
    val _ = resourceOwnerPasswordsSetRef.incrementAndGet()
    latestPasswordRef.set(rawPassword)
    result()
  }

  def resourceOwnersCreated: Int = resourceOwnersCreatedRef.get()
  def resourceOwnersActivated: Int = resourceOwnersActivatedRef.get()
  def resourceOwnersDeactivated: Int = resourceOwnersDeactivatedRef.get()
  def resourceOwnerPasswordsSet: Int = resourceOwnerPasswordsSetRef.get()
  def notFound: Int = notFoundRef.get()
  def conflicts: Int = conflictsRef.get()
  def failures: Int = failuresRef.get()
  def latestPassword: String = latestPasswordRef.get()

  private def result(): Future[UserCredentialsManager.Result] =
    withResult match {
      case Success(result: UserCredentialsManager.Result.Success.type) =>
        Future.successful(result)

      case Success(result: UserCredentialsManager.Result.NotFound) =>
        val _ = notFoundRef.incrementAndGet()
        Future.successful(result)

      case Success(result: UserCredentialsManager.Result.Conflict) =>
        val _ = conflictsRef.incrementAndGet()
        Future.successful(result)

      case Failure(e) =>
        val _ = failuresRef.incrementAndGet()
        Future.failed(e)
    }
}

object MockUserCredentialsManager {
  def apply(): MockUserCredentialsManager = MockUserCredentialsManager(
    withResult = Success(UserCredentialsManager.Result.Success)
  )

  def apply(withResult: Try[UserCredentialsManager.Result]): MockUserCredentialsManager =
    new MockUserCredentialsManager(
      withResult = withResult
    )
}
