package stasis.layers.persistence

import scala.concurrent.Future

import slick.jdbc.H2Profile
import slick.jdbc.JdbcProfile

trait SlickTestDatabase {
  def withStore[T](name: String)(f: (JdbcProfile, JdbcProfile#Backend#Database) => Future[T]): Future[T] = {
    val profile: JdbcProfile = H2Profile

    val database: JdbcProfile#Backend#Database =
      profile.api.Database.forURL(url = s"jdbc:h2:mem:$name", keepAliveConnection = true)

    import scala.concurrent.ExecutionContext.Implicits.global
    val result = f(profile, database)
    result.onComplete(_ => database.close())
    result
  }

  def withStore[T](f: (JdbcProfile, JdbcProfile#Backend#Database) => Future[T]): Future[T] =
    withStore(name = getClass.getSimpleName)(f)
}
