package stasis.identity.service.bootstrap

import java.time.Instant

import scala.concurrent.Future

import com.typesafe.{config => typesafe}
import org.apache.pekko.Done

import stasis.identity.model.apis.Api
import stasis.identity.persistence.apis.ApiStore
import io.github.sndnv.layers.service.bootstrap.BootstrapEntityProvider

class ApiBootstrapEntityProvider(store: ApiStore) extends BootstrapEntityProvider[Api] {
  override val name: String = "apis"

  override val default: Seq[Api] =
    Seq(Api.create(id = Api.ManageIdentity))

  override def load(config: typesafe.Config): Api = {
    val now = Instant.now()
    Api(id = config.getString("id"), created = now, updated = now)
  }

  override def validate(entities: Seq[Api]): Future[Done] =
    requireNonDuplicateField(entities, _.id)

  override def create(entity: Api): Future[Done] =
    store.put(entity)

  override def render(entity: Api, withPrefix: String): String =
    s"""
       |$withPrefix  api:
       |$withPrefix    id:      ${entity.id}
       |$withPrefix    created: ${entity.created.toString}
       |$withPrefix    updated: ${entity.updated.toString}""".stripMargin

  override def extractId(entity: Api): String =
    entity.id
}
