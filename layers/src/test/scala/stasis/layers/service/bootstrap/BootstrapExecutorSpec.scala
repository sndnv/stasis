package stasis.layers.service.bootstrap

import scala.concurrent.Future

import com.typesafe.config.ConfigFactory
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.layers.UnitSpec
import stasis.layers.service.bootstrap.mocks._

class BootstrapExecutorSpec extends UnitSpec {
  "A Default BootstrapExecutor" should "run bootstrap for individual providers" in {
    val provider = new TestProvider()

    val result = BootstrapExecutor.run(bootstrapConfig, provider)(log, system.executionContext).await

    val expectedEntitiesCount = configuredEntities.length + 1 // configured + default
    result should be(BootstrapResult(found = expectedEntitiesCount, created = expectedEntitiesCount))

    provider.created.sortBy(_.a) should be((configuredEntities :+ TestClass.Default).sortBy(_.a))
  }

  it should "handle failures during entity loading" in {
    val provider = new TestProvider(name = "test-classes-with-invalid-values")

    val e = BootstrapExecutor.run(bootstrapConfig, provider)(log, system.executionContext).failed.await

    e.getMessage should include("b has type BOOLEAN rather than NUMBER")

    provider.created should be(empty)
  }

  it should "handle failures during entity validation" in {
    val provider = new TestProvider(name = "test-classes-with-duplicates")

    val e = BootstrapExecutor.run(bootstrapConfig, provider)(log, system.executionContext).failed.await

    e.getMessage should be("Duplicate values [x,z] found for field [a] in [TestClass]")

    provider.created should be(empty)
  }

  it should "handle failures during entity creation" in {
    val provider = new TestProvider() {
      override def create(entity: TestClass): Future[Done] = Future.failed(new RuntimeException("Test failure"))
    }

    val result = BootstrapExecutor.run(bootstrapConfig, provider)(log, system.executionContext).await

    val expectedEntitiesCount = configuredEntities.length + 1 // configured + default
    result should be(BootstrapResult(found = expectedEntitiesCount, created = 0))

    provider.created should be(empty)
  }

  it should "execute bootstrap for all configured providers" in {
    val executor = BootstrapExecutor(
      entityProviders = Seq(
        new TestProvider(name = "test-classes"),
        new TestProvider(name = "test-classes-with-invalid-values"),
        new TestProvider(name = "test-classes"),
        new TestProvider(name = "test-classes-with-duplicates")
      )
    )(system.executionContext)

    val result = executor.execute(bootstrapConfig).await

    val expectedEntitiesCount = configuredEntities.length + 1 // configured + default
    // only two of the providers should succeed
    result should be(BootstrapResult(found = expectedEntitiesCount * 2, created = expectedEntitiesCount * 2))
  }

  private val configuredEntities: Seq[TestClass] = Seq(
    TestClass(a = "x", b = 1),
    TestClass(a = "y", b = 2),
    TestClass(a = "z", b = 3)
  )

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "BootstrapExecutorSpec"
  )

  private val bootstrapConfig = ConfigFactory.load("bootstrap.conf").getConfig("bootstrap")

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
