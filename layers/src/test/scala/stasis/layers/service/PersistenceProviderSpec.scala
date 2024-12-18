package stasis.layers.service

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.layers.UnitSpec
import stasis.layers.persistence.migration.MigrationResult

class PersistenceProviderSpec extends UnitSpec {
  import PersistenceProviderSpec._

  "A PersistenceProvider" should "combining persistence providers" in {
    val expectedResultA = MigrationResult(found = 2, executed = 1)
    val expectedResultB = MigrationResult(found = 3, executed = 3)

    val providerA = new TestProvider(expectedResultA)
    val providerB = new TestProvider(expectedResultB)

    val combined = providerA.combineWith(providerB)(ExecutionContext.global)

    providerA.migrateCalls should be(0)
    providerA.initCalls should be(0)
    providerA.dropCalls should be(0)

    providerB.migrateCalls should be(0)
    providerB.initCalls should be(0)
    providerB.dropCalls should be(0)

    combined.migrate().await should be(MigrationResult(found = 5, executed = 4))
    noException should be thrownBy combined.init().await
    noException should be thrownBy combined.drop().await

    providerA.migrateCalls should be(1)
    providerA.initCalls should be(1)
    providerA.dropCalls should be(1)

    providerB.migrateCalls should be(1)
    providerB.initCalls should be(1)
    providerB.dropCalls should be(1)
  }
}

object PersistenceProviderSpec {
  class TestProvider(expectedResult: MigrationResult) extends PersistenceProvider {
    private val migrateCounterRef: AtomicInteger = new AtomicInteger(0)
    private val initCounterRef: AtomicInteger = new AtomicInteger(0)
    private val dropCounterRef: AtomicInteger = new AtomicInteger(0)

    def migrateCalls: Int = migrateCounterRef.get()
    def initCalls: Int = initCounterRef.get()
    def dropCalls: Int = dropCounterRef.get()

    override def migrate(): Future[MigrationResult] = {
      val _ = migrateCounterRef.incrementAndGet()
      Future.successful(expectedResult)
    }

    override def init(): Future[Done] = {
      val _ = initCounterRef.incrementAndGet()
      Future.successful(Done)
    }

    override def drop(): Future[Done] = {
      val _ = dropCounterRef.incrementAndGet()
      Future.successful(Done)
    }
  }
}
