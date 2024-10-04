package stasis.client.api.http.routes

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.client.api.Context
import stasis.shared.api.requests.CreateDatasetDefinition

class DatasetDefinitions()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  def routes(): Route =
    pathEndOrSingleSlash {
      concat(
        get {
          onSuccess(context.api.datasetDefinitions()) { definitions =>
            log.debug("API successfully retrieved [{}] definitions", definitions.size)
            consumeEntity & complete(definitions)
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
  def apply()(implicit context: Context): DatasetDefinitions =
    new DatasetDefinitions()
}
