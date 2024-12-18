package stasis.layers.service

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.layers.UnitSpec
import stasis.layers.persistence.migration.MigrationResult
import stasis.layers.service.bootstrap.mocks.TestProvider

class BootstrapProviderSpec extends UnitSpec {
  import BootstrapProviderSpec._

  "A BootstrapProvider" should "support parsing its mode from config" in {
    BootstrapProvider.BootstrapMode(mode = "off") should be(BootstrapProvider.BootstrapMode.Off)
    BootstrapProvider.BootstrapMode(mode = "init") should be(BootstrapProvider.BootstrapMode.Init)
    BootstrapProvider.BootstrapMode(mode = "init-and-start") should be(BootstrapProvider.BootstrapMode.InitAndStart)
    BootstrapProvider.BootstrapMode(mode = "drop") should be(BootstrapProvider.BootstrapMode.Drop)

    an[IllegalArgumentException] should be thrownBy BootstrapProvider.BootstrapMode(mode = "other")
  }

  it should "support providing its mode name" in {
    BootstrapProvider.BootstrapMode.Off.name should be("Off")
    BootstrapProvider.BootstrapMode.Init.name should be("Init")
    BootstrapProvider.BootstrapMode.InitAndStart.name should be("InitAndStart")
    BootstrapProvider.BootstrapMode.Drop.name should be("Drop")
  }

  "A Default BootstrapProvider" should "support running bootstrap operations (mode=off)" in {
    val persistence = new MockPersistenceProvider()

    val provider = BootstrapProvider(
      bootstrapConfig = config.getConfig("bootstrap-off"),
      persistence = persistence,
      entityProviders = Seq(new TestProvider())
    )(system.executionContext)

    provider.run().await should be(BootstrapProvider.BootstrapMode.Off)

    persistence.migrated should be(1)
    persistence.initialized should be(0)
    persistence.dropped should be(0)
  }

  it should "support running bootstrap operations (mode=init)" in {
    val persistence = new MockPersistenceProvider()

    val provider = BootstrapProvider(
      bootstrapConfig = config.getConfig("bootstrap-init"),
      persistence = persistence,
      entityProviders = Seq(new TestProvider())
    )(system.executionContext)

    provider.run().await should be(BootstrapProvider.BootstrapMode.Init)

    persistence.migrated should be(1)
    persistence.initialized should be(1)
    persistence.dropped should be(0)
  }

  it should "support running bootstrap operations (mode=init-and-start)" in {
    val persistence = new MockPersistenceProvider()

    val provider = BootstrapProvider(
      bootstrapConfig = config.getConfig("bootstrap-init-and-start"),
      persistence = persistence,
      entityProviders = Seq(new TestProvider())
    )(system.executionContext)

    provider.run().await should be(BootstrapProvider.BootstrapMode.InitAndStart)

    persistence.migrated should be(1)
    persistence.initialized should be(1)
    persistence.dropped should be(0)
  }

  it should "support running bootstrap operations (mode=drop)" in {
    val persistence = new MockPersistenceProvider()

    val provider = BootstrapProvider(
      bootstrapConfig = config.getConfig("bootstrap-drop"),
      persistence = persistence,
      entityProviders = Seq(new TestProvider())
    )(system.executionContext)

    provider.run().await should be(BootstrapProvider.BootstrapMode.Drop)

    persistence.migrated should be(0)
    persistence.initialized should be(0)
    persistence.dropped should be(1)
  }

  it should "fail if bootstrap is enabled but no config file is provided (mode=init)" in {
    val persistence = new MockPersistenceProvider()

    val provider = BootstrapProvider(
      bootstrapConfig = config.getConfig("bootstrap-init-invalid"),
      persistence = persistence,
      entityProviders = Seq(new TestProvider())
    )(system.executionContext)

    val e = provider.run().failed.await

    e.getMessage should include("Bootstrap enabled but no config file was provided")

    persistence.migrated should be(0)
    persistence.initialized should be(0)
    persistence.dropped should be(0)
  }

  it should "fail if bootstrap is enabled but no config file is provided (mode=init-and-start)" in {
    val persistence = new MockPersistenceProvider()

    val provider = BootstrapProvider(
      bootstrapConfig = config.getConfig("bootstrap-init-and-start-invalid"),
      persistence = persistence,
      entityProviders = Seq(new TestProvider())
    )(system.executionContext)

    val e = provider.run().failed.await

    e.getMessage should include("Bootstrap enabled but no config file was provided")

    persistence.migrated should be(0)
    persistence.initialized should be(0)
    persistence.dropped should be(0)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "BootstrapProviderSpec"
  )

  private val config = system.settings.config.getConfig("stasis.test.layers.service")
}

object BootstrapProviderSpec {
  class MockPersistenceProvider extends PersistenceProvider {
    private val migratedRef: AtomicInteger = new AtomicInteger(0)
    private val initializedRef: AtomicInteger = new AtomicInteger(0)
    private val droppedRef: AtomicInteger = new AtomicInteger(0)

    def migrated: Int = migratedRef.get()
    def initialized: Int = initializedRef.get()
    def dropped: Int = droppedRef.get()

    override def migrate(): Future[MigrationResult] = {
      val _ = migratedRef.incrementAndGet()
      Future.successful(MigrationResult.empty)
    }

    override def init(): Future[Done] = {
      val _ = initializedRef.incrementAndGet()
      Future.successful(Done)
    }

    override def drop(): Future[Done] = {
      val _ = droppedRef.incrementAndGet()
      Future.successful(Done)
    }
  }
}
