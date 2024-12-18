package stasis.layers.service.bootstrap

import scala.concurrent.Future

import com.typesafe.config.Config
import org.apache.pekko.Done

import stasis.layers.UnitSpec

class BootstrapEntityProviderSpec extends UnitSpec {
  import BootstrapEntityProviderSpec._

  "A BootstrapEntityProvider" should "support extracting field names from entities" in {
    BootstrapEntityProvider.fieldNameFrom[TestClass, String](entities, _.a) should be("a")
    BootstrapEntityProvider.fieldNameFrom[TestClass, Int](entities, _.b) should be("b")
  }

  it should "fail to extract field names from empty entity lists" in {
    an[IllegalArgumentException] should be thrownBy BootstrapEntityProvider.fieldNameFrom[TestClass, String](Seq.empty, _.a)
    an[IllegalArgumentException] should be thrownBy BootstrapEntityProvider.fieldNameFrom[TestClass, Int](Seq.empty, _.b)
  }

  it should "support extracting class names from entities" in {
    BootstrapEntityProvider.classNameFrom[TestClass](entities) should be("TestClass")
  }

  it should "fail to extract class names from empty entity lists" in {
    an[IllegalArgumentException] should be thrownBy BootstrapEntityProvider.classNameFrom[TestClass](Seq.empty)
  }

  it should "support requiring no duplicates in entities" in {
    noException should be thrownBy BootstrapEntityProvider.requireNonDuplicateField[TestClass, String](entities, _.a).await
    noException should be thrownBy BootstrapEntityProvider.requireNonDuplicateField[TestClass, Int](entities, _.b).await

    val duplicateEntities = Seq(
      TestClass(a = "x", b = 100),
      TestClass(a = "x", b = 200),
      TestClass(a = "y", b = 300),
      TestClass(a = "z", b = 300)
    )

    BootstrapEntityProvider
      .requireNonDuplicateField[TestClass, String](duplicateEntities, _.a)
      .failed
      .await
      .getMessage should be(
      "Duplicate values [x] found for field [a] in [TestClass]"
    )

    BootstrapEntityProvider
      .requireNonDuplicateField[TestClass, Int](duplicateEntities, _.b)
      .failed
      .await
      .getMessage should be(
      "Duplicate values [300] found for field [b] in [TestClass]"
    )
  }

  private val entities: Seq[TestClass] = Seq(
    TestClass(a = "1", b = 2),
    TestClass(a = "3", b = 4),
    TestClass(a = "5", b = 6),
    TestClass(a = "7", b = 8)
  )
}

object BootstrapEntityProviderSpec {
  final case class TestClass(a: String, b: Int)

  object TestProvider extends BootstrapEntityProvider[TestClass] {
    override val name: String = "test"
    override def default: Seq[TestClass] = Seq.empty
    override def load(config: Config): TestClass = TestClass(a = "1", b = 2)
    override def validate(entities: Seq[TestClass]): Future[Done] = Future.successful(Done)
    override def create(entity: TestClass): Future[Done] = Future.successful(Done)
    override def render(entity: TestClass, withPrefix: String): String = ""
    override def extractId(entity: TestClass): String = ""
  }
}
