package stasis.client.api.http.routes

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import stasis.client.api.Context
import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.api.requests.UpdateDatasetDefinition

class DatasetDefinitions()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  def routes(): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            onSuccess(context.api.datasetDefinitions()) { definitions =>
              log.debug("API successfully retrieved [{}] definitions", definitions.size)
              context.analytics.recordEvent(name = "get_dataset_definitions")

              consumeEntity & complete(definitions)
            }
          },
          post {
            entity(as[CreateDatasetDefinition]) { request =>
              onSuccess(context.api.createDatasetDefinition(request)) { response =>
                log.debug("API successfully created definition [{}]", response.definition)
                context.analytics.recordEvent(name = "create_dataset_definition")

                complete(response)
              }
            }
          }
        )
      },
      path(JavaUUID) { definitionId =>
        concat(
          get {
            onSuccess(context.api.datasetDefinition(definitionId)) { definition =>
              log.debug("API successfully retrieved definition [{}]", definitionId)
              context.analytics.recordEvent(name = "get_dataset_definition")

              consumeEntity & complete(definition)
            }
          },
          put {
            entity(as[UpdateDatasetDefinition]) { request =>
              onSuccess(context.api.updateDatasetDefinition(definitionId, request)) { _ =>
                log.debug("API successfully updated definition [{}]", definitionId)
                context.analytics.recordEvent(name = "update_dataset_definition")

                complete(StatusCodes.OK)
              }
            }
          },
          delete {
            onSuccess(context.api.deleteDatasetDefinition(definitionId)) { _ =>
              log.debug("API successfully removed definition [{}]", definitionId)
              context.analytics.recordEvent(name = "delete_dataset_definition")

              complete(StatusCodes.OK)
            }
          }
        )
      }
    )
}

object DatasetDefinitions {
  def apply()(implicit context: Context): DatasetDefinitions =
    new DatasetDefinitions()
}
