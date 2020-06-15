package stasis.client.api.http.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import stasis.client.api.http.Context
import stasis.shared.api.requests.CreateDatasetDefinition

class DatasetDefinitions()(implicit override val mat: Materializer, context: Context) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  def routes(): Route =
    pathEndOrSingleSlash {
      concat(
        get {
          onSuccess(context.api.datasetDefinitions()) { definitions =>
            log.debug("API successfully retrieved [{}] definitions", definitions.size)
            discardEntity & complete(definitions)
          }
        },
        post {
          entity(as[CreateDatasetDefinition]) { request =>
            onSuccess(context.api.createDatasetDefinition(request)) { response =>
              log.debug("API successfully created definition [{}]", response.definition)
              complete(response)
            }
          }
        }
      )
    }
}

object DatasetDefinitions {
  def apply()(implicit mat: Materializer, context: Context): DatasetDefinitions =
    new DatasetDefinitions()
}
