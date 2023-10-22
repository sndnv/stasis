package stasis.client.api.http.routes

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import stasis.client.api.http.Context

class User()(implicit context: Context) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  def routes(): Route =
    pathEndOrSingleSlash {
      get {
        onSuccess(context.api.user()) { user =>
          log.debug("API successfully retrieved user [{}]", user.id)
          consumeEntity & complete(user)
        }
      }
    }
}

object User {
  def apply()(implicit context: Context): User =
    new User()
}
