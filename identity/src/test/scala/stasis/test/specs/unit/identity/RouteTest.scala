package stasis.test.specs.unit.identity

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.marshalling.{Marshal, Marshaller}
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.model.apis.{Api, ApiStore}
import stasis.identity.model.clients.{Client, ClientStore}
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore}
import stasis.identity.model.tokens.{RefreshToken, RefreshTokenStore, StoredRefreshToken}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.RouteTest.FailingMemoryBackend

import scala.concurrent.Future
import scala.concurrent.duration._

trait RouteTest extends AsyncUnitSpec with ScalatestRouteTest {
  import scala.language.implicitConversions

  implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    name = this.getClass.getSimpleName + "_typed"
  )

  implicit def requestToEntity[T](request: T)(implicit m: Marshaller[T, RequestEntity]): RequestEntity =
    Marshal(request).to[RequestEntity].await

  def createLogger(): LoggingAdapter = Logging(system, this.getClass.getName)

  def createApiStore(): ApiStore = ApiStore(
    MemoryBackend[Api.Id, Api](name = s"api-store-${java.util.UUID.randomUUID()}")
  )

  def createClientStore(): ClientStore = ClientStore(
    MemoryBackend[Client.Id, Client](name = s"client-store-${java.util.UUID.randomUUID()}")
  )

  def createCodeStore(expiration: FiniteDuration = 3.seconds): AuthorizationCodeStore = AuthorizationCodeStore(
    expiration = expiration,
    MemoryBackend[AuthorizationCode, StoredAuthorizationCode](name = s"code-store-${java.util.UUID.randomUUID()}")
  )

  def createOwnerStore(): ResourceOwnerStore = ResourceOwnerStore(
    MemoryBackend[ResourceOwner.Id, ResourceOwner](name = s"owner-store-${java.util.UUID.randomUUID()}")
  )

  def createTokenStore(expiration: FiniteDuration = 3.seconds): RefreshTokenStore = RefreshTokenStore(
    expiration = expiration,
    MemoryBackend[RefreshToken, StoredRefreshToken](name = s"token-store-${java.util.UUID.randomUUID()}"),
    MemoryBackend[(Client.Id, ResourceOwner.Id), RefreshToken](name = s"token-directory-${java.util.UUID.randomUUID()}")
  )

  def createFailingApiStore(
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false
  ): ApiStore = ApiStore(
    new FailingMemoryBackend[Api.Id, Api] {
      override def system: ActorSystem[SpawnProtocol.Command] = typedSystem
      override def storeName: String = s"api-store-${java.util.UUID.randomUUID()}"
      override def putFails: Boolean = failingPut
      override def deleteFails: Boolean = failingDelete
      override def getFails: Boolean = failingGet
      override def entriesFails: Boolean = failingEntries
      override def containsFails: Boolean = failingContains
    }
  )

  def createFailingClientStore(
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false
  ): ClientStore = ClientStore(
    new FailingMemoryBackend[Client.Id, Client] {
      override def system: ActorSystem[SpawnProtocol.Command] = typedSystem
      override def storeName: String = s"client-store-${java.util.UUID.randomUUID()}"
      override def putFails: Boolean = failingPut
      override def deleteFails: Boolean = failingDelete
      override def getFails: Boolean = failingGet
      override def entriesFails: Boolean = failingEntries
      override def containsFails: Boolean = failingContains
    }
  )

  def createFailingCodeStore(
    expiration: FiniteDuration = 3.second,
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false
  ): AuthorizationCodeStore = AuthorizationCodeStore(
    expiration = expiration,
    new FailingMemoryBackend[AuthorizationCode, StoredAuthorizationCode] {
      override def system: ActorSystem[SpawnProtocol.Command] = typedSystem
      override def storeName: String = s"code-store-${java.util.UUID.randomUUID()}"
      override def putFails: Boolean = failingPut
      override def deleteFails: Boolean = failingDelete
      override def getFails: Boolean = failingGet
      override def entriesFails: Boolean = failingEntries
      override def containsFails: Boolean = failingContains
    }
  )

  def createFailingOwnerStore(
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false
  ): ResourceOwnerStore = ResourceOwnerStore(
    new FailingMemoryBackend[ResourceOwner.Id, ResourceOwner] {
      override def system: ActorSystem[SpawnProtocol.Command] = typedSystem
      override def storeName: String = s"owner-store-${java.util.UUID.randomUUID()}"
      override def putFails: Boolean = failingPut
      override def deleteFails: Boolean = failingDelete
      override def getFails: Boolean = failingGet
      override def entriesFails: Boolean = failingEntries
      override def containsFails: Boolean = failingContains
    }
  )

  def createFailingTokenStore(
    expiration: FiniteDuration = 3.seconds,
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false
  ): RefreshTokenStore = RefreshTokenStore(
    expiration = expiration,
    new FailingMemoryBackend[RefreshToken, StoredRefreshToken] {
      override def system: ActorSystem[SpawnProtocol.Command] = typedSystem
      override def storeName: String = s"token-store-${java.util.UUID.randomUUID()}"
      override def putFails: Boolean = failingPut
      override def deleteFails: Boolean = failingDelete
      override def getFails: Boolean = failingGet
      override def entriesFails: Boolean = failingEntries
      override def containsFails: Boolean = failingContains
    },
    MemoryBackend[(Client.Id, ResourceOwner.Id), RefreshToken](name = s"token-directory-${java.util.UUID.randomUUID()}")
  )
}

object RouteTest {
  trait FailingMemoryBackend[K, V] extends KeyValueBackend[K, V] {
    private val underlying = MemoryBackend[K, V](storeName)

    implicit def system: ActorSystem[SpawnProtocol.Command]
    implicit def timeout: Timeout = 3.seconds

    def storeName: String
    def putFails: Boolean
    def deleteFails: Boolean
    def getFails: Boolean
    def entriesFails: Boolean
    def containsFails: Boolean

    override def init(): Future[Done] = Future.successful(Done)
    override def drop(): Future[Done] = Future.successful(Done)

    override def put(key: K, value: V): Future[Done] =
      if (putFails) Future.failed(new RuntimeException("Operation failure enabled")) else underlying.put(key, value)

    override def delete(key: K): Future[Boolean] =
      if (deleteFails) Future.failed(new RuntimeException("Operation failure enabled")) else underlying.delete(key)

    override def get(key: K): Future[Option[V]] =
      if (getFails) Future.failed(new RuntimeException("Operation failure enabled")) else underlying.get(key)

    override def entries: Future[Map[K, V]] =
      if (entriesFails) Future.failed(new RuntimeException("Operation failure enabled")) else underlying.entries

    override def contains(key: K): Future[Boolean] =
      if (containsFails) Future.failed(new RuntimeException("Operation failure enabled")) else underlying.contains(key)
  }
}
