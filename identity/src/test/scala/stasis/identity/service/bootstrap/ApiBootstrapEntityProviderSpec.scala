package stasis.identity.service.bootstrap

import scala.jdk.CollectionConverters._

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.identity.model.apis.Api
import stasis.identity.persistence.mocks.MockApiStore
import io.github.sndnv.layers.testing.UnitSpec

class ApiBootstrapEntityProviderSpec extends UnitSpec {
  "An ApiBootstrapEntityProvider" should "provide its name and default entities" in {
    val provider = new ApiBootstrapEntityProvider(MockApiStore())

    provider.name should be("apis")

    provider.default.map(_.id) should be(Seq(Api.ManageIdentity))
  }

  it should "support loading entities from config" in {
    val provider = new ApiBootstrapEntityProvider(MockApiStore())

    bootstrapConfig.getConfigList("apis").asScala.map(provider.load).toList match {
      case api1 :: Nil =>
        api1.id should be("example-api")

      case other =>
        fail(s"Unexpected result received: [$other]")
    }
  }

  it should "support validating entities" in {
    val provider = new ApiBootstrapEntityProvider(MockApiStore())

    val validApis = Seq(
      Api.create(id = "api-1"),
      Api.create(id = "api-2"),
      Api.create(id = "api-3")
    )

    val invalidApis = Seq(
      Api.create(id = "api-1"),
      Api.create(id = "api-1"),
      Api.create(id = "api-3"),
      Api.create(id = "api-3")
    )

    noException should be thrownBy provider.validate(validApis).await

    val e = provider.validate(invalidApis).failed.await

    e.getMessage should be("Duplicate values [api-3,api-1] found for field [id] in [Api]")
  }

  it should "support creating entities" in {
    val store = MockApiStore()
    val provider = new ApiBootstrapEntityProvider(store)

    for {
      existingBefore <- store.all
      _ <- provider.create(Api.create(id = "api-1"))
      existingAfter <- store.all
    } yield {
      existingBefore should be(empty)
      existingAfter should not be empty
    }
  }

  it should "support rendering entities" in {
    val provider = new ApiBootstrapEntityProvider(MockApiStore())

    val api = Api.create(id = "api-i")

    provider.render(api, withPrefix = "") should be(
      s"""
         |  api:
         |    id:      ${api.id}
         |    created: ${api.created.toString}
         |    updated: ${api.updated.toString}""".stripMargin
    )
  }

  it should "support extracting entity IDs" in {
    val provider = new ApiBootstrapEntityProvider(MockApiStore())

    val api = Api.create(id = "api-i")

    provider.extractId(api) should be(api.id)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ApiBootstrapEntityProviderSpec"
  )

  private val bootstrapConfig = ConfigFactory.load("bootstrap-unit.conf").getConfig("bootstrap")
}
