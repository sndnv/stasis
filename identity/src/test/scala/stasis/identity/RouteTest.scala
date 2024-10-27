package stasis.identity

import java.time.temporal.ChronoUnit

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.marshalling.Marshaller
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.util.Timeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.identity.RouteTest.FailingMemoryStore
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.StoredRefreshToken
import stasis.identity.persistence.apis.ApiStore
import stasis.identity.persistence.clients.ClientStore
import stasis.identity.persistence.codes.AuthorizationCodeStore
import stasis.identity.persistence.mocks.MockApiStore
import stasis.identity.persistence.mocks.MockAuthorizationCodeStore
import stasis.identity.persistence.mocks.MockClientStore
import stasis.identity.persistence.mocks.MockRefreshTokenStore
import stasis.identity.persistence.mocks.MockResourceOwnerStore
import stasis.identity.persistence.owners.ResourceOwnerStore
import stasis.identity.persistence.tokens.RefreshTokenStore
import stasis.layers.UnitSpec
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext

trait RouteTest extends UnitSpec with ScalatestRouteTest {
  import scala.language.implicitConversions

  implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = this.getClass.getSimpleName + "_typed"
  )

  implicit val telemetryContext: MockTelemetryContext = MockTelemetryContext()

  implicit def requestToEntity[T](request: T)(implicit m: Marshaller[T, RequestEntity]): RequestEntity =
    Marshal(request).to[RequestEntity].await

  def createLogger(): Logger = LoggerFactory.getLogger(this.getClass.getName)

  def createApiStore(): ApiStore = MockApiStore()

  def createClientStore(): ClientStore = MockClientStore()

  def createCodeStore(): AuthorizationCodeStore = MockAuthorizationCodeStore()

  def createOwnerStore(): ResourceOwnerStore = MockResourceOwnerStore()

  def createTokenStore(): RefreshTokenStore = MockRefreshTokenStore()

  def createFailingApiStore(
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false,
    failingLoad: Boolean = false
  ): ApiStore =
    new MockApiStore(
      underlying = new FailingMemoryStore[Api.Id, Api] {
        override implicit def telemetry: MockTelemetryContext = telemetryContext
        override def system: ActorSystem[Nothing] = typedSystem
        override def storeName: String = s"api-store-${java.util.UUID.randomUUID()}"
        override def putFails: Boolean = failingPut
        override def deleteFails: Boolean = failingDelete
        override def getFails: Boolean = failingGet
        override def entriesFails: Boolean = failingEntries
        override def containsFails: Boolean = failingContains
        override def loadFails: Boolean = failingLoad
      }
    )

  def createFailingClientStore(
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false,
    failingLoad: Boolean = false
  ): ClientStore =
    new MockClientStore(
      new FailingMemoryStore[Client.Id, Client] {
        override implicit def telemetry: MockTelemetryContext = telemetryContext
        override def system: ActorSystem[Nothing] = typedSystem
        override def storeName: String = s"client-store-${java.util.UUID.randomUUID()}"
        override def putFails: Boolean = failingPut
        override def deleteFails: Boolean = failingDelete
        override def getFails: Boolean = failingGet
        override def entriesFails: Boolean = failingEntries
        override def containsFails: Boolean = failingContains
        override def loadFails: Boolean = failingLoad
      }
    )

  def createFailingCodeStore(
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false,
    failingLoad: Boolean = false
  ): AuthorizationCodeStore =
    new MockAuthorizationCodeStore(
      new FailingMemoryStore[AuthorizationCode, StoredAuthorizationCode] {
        override implicit def telemetry: MockTelemetryContext = telemetryContext
        override def system: ActorSystem[Nothing] = typedSystem
        override def storeName: String = s"code-store-${java.util.UUID.randomUUID()}"
        override def putFails: Boolean = failingPut
        override def deleteFails: Boolean = failingDelete
        override def getFails: Boolean = failingGet
        override def entriesFails: Boolean = failingEntries
        override def containsFails: Boolean = failingContains
        override def loadFails: Boolean = failingLoad
      }
    )

  def createFailingOwnerStore(
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false,
    failingLoad: Boolean = false
  ): ResourceOwnerStore =
    new MockResourceOwnerStore(
      new FailingMemoryStore[ResourceOwner.Id, ResourceOwner] {
        override implicit def telemetry: MockTelemetryContext = telemetryContext
        override def system: ActorSystem[Nothing] = typedSystem
        override def storeName: String = s"owner-store-${java.util.UUID.randomUUID()}"
        override def putFails: Boolean = failingPut
        override def deleteFails: Boolean = failingDelete
        override def getFails: Boolean = failingGet
        override def entriesFails: Boolean = failingEntries
        override def containsFails: Boolean = failingContains
        override def loadFails: Boolean = failingLoad
      }
    )

  def createFailingTokenStore(
    failingPut: Boolean = false,
    failingDelete: Boolean = false,
    failingGet: Boolean = false,
    failingEntries: Boolean = false,
    failingContains: Boolean = false,
    failingLoad: Boolean = false
  ): RefreshTokenStore =
    new MockRefreshTokenStore(
      new FailingMemoryStore[RefreshToken, StoredRefreshToken] {
        override implicit def telemetry: MockTelemetryContext = telemetryContext
        override def system: ActorSystem[Nothing] = typedSystem
        override def storeName: String = s"token-store-${java.util.UUID.randomUUID()}"
        override def putFails: Boolean = failingPut
        override def deleteFails: Boolean = failingDelete
        override def getFails: Boolean = failingGet
        override def entriesFails: Boolean = failingEntries
        override def containsFails: Boolean = failingContains
        override def loadFails: Boolean = failingLoad
      }
    )

  implicit class ExtendedApi(api: Api) {
    def truncated(): Api =
      api.copy(
        created = api.created.truncatedTo(ChronoUnit.SECONDS),
        updated = api.updated.truncatedTo(ChronoUnit.SECONDS)
      )
  }

  implicit class ExtendedClient(client: Client) {
    def truncated(): Client =
      client.copy(
        created = client.created.truncatedTo(ChronoUnit.SECONDS),
        updated = client.updated.truncatedTo(ChronoUnit.SECONDS)
      )
  }

  implicit class ExtendedResourceOwner(owner: ResourceOwner) {
    def truncated(): ResourceOwner =
      owner.copy(
        created = owner.created.truncatedTo(ChronoUnit.SECONDS),
        updated = owner.updated.truncatedTo(ChronoUnit.SECONDS)
      )
  }

  implicit class ExtendedOptionalApi(api: Option[Api]) {
    def truncated(): Option[Api] = api.map(_.truncated())
  }

  implicit class ExtendedOptionalClient(client: Option[Client]) {
    def truncated(): Option[Client] = client.map(_.truncated())
  }

  implicit class ExtendedOptionalResourceOwner(owner: Option[ResourceOwner]) {
    def truncated(): Option[ResourceOwner] = owner.map(_.truncated())
  }
}

object RouteTest {
  trait FailingMemoryStore[K, V] extends KeyValueStore[K, V] {
    private val underlying = MemoryStore[K, V](storeName)

    implicit def telemetry: MockTelemetryContext
    implicit def system: ActorSystem[Nothing]
    implicit def timeout: Timeout = 3.seconds

    override def name(): String = storeName

    override def migrations(): Seq[Migration] = Seq.empty

    def storeName: String
    def putFails: Boolean
    def deleteFails: Boolean
    def getFails: Boolean
    def entriesFails: Boolean
    def containsFails: Boolean
    def loadFails: Boolean

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

    override def load(entries: Map[K, V]): Future[Done] =
      if (loadFails) Future.failed(new RuntimeException("Operation failure enabled")) else underlying.load(entries)
  }
}
