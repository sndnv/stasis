package stasis.layers.service.bootstrap.mocks

import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import com.typesafe.config.Config
import org.apache.pekko.Done

import stasis.layers.service.bootstrap.BootstrapEntityProvider

class TestProvider(
  override val name: String = "test-classes"
) extends BootstrapEntityProvider[TestClass] {
  private val createdRef = new ConcurrentLinkedQueue[TestClass]()

  def created: Seq[TestClass] = createdRef.asScala.toSeq

  override def default: Seq[TestClass] =
    Seq(TestClass.Default)

  override def load(config: Config): TestClass =
    TestClass(a = config.getString("a"), b = config.getInt("b"))

  override def validate(entities: Seq[TestClass]): Future[Done] =
    requireNonDuplicateField(entities, _.a)

  override def create(entity: TestClass): Future[Done] = {
    val _ = createdRef.add(entity)
    Future.successful(Done)
  }

  override def render(entity: TestClass, withPrefix: String): String =
    s"""
       |$withPrefix  test-class:
       |$withPrefix    a: ${entity.a}
       |$withPrefix    b: ${entity.b.toString}""".stripMargin

  override def extractId(entity: TestClass): String = entity.a
}
