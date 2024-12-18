package stasis.layers.service.bootstrap

import scala.concurrent.Future
import scala.reflect.ClassTag

import com.typesafe.config.Config
import org.apache.pekko.Done

trait BootstrapEntityProvider[T <: Product] {
  type EntityType = T

  def name: String
  def default: Seq[EntityType]

  def load(config: Config): EntityType
  def validate(entities: Seq[EntityType]): Future[Done]
  def create(entity: EntityType): Future[Done]

  def render(entity: EntityType, withPrefix: String): String
  def extractId(entity: EntityType): String

  protected def requireNonDuplicateField[V](entities: Seq[T], field: T => V)(implicit tag: ClassTag[T]): Future[Done] =
    BootstrapEntityProvider.requireNonDuplicateField[T, V](entities, field)
}

object BootstrapEntityProvider {
  def requireNonDuplicateField[T <: Product, V](entities: Seq[T], field: T => V)(implicit tag: ClassTag[T]): Future[Done] =
    entities.groupBy(field).filter(_._2.size > 1).keys.toList match {
      case Nil =>
        Future.successful(Done)

      case other =>
        val fieldName = fieldNameFrom(entities, field)
        val className = tag.runtimeClass.getSimpleName

        Future.failed(
          new IllegalArgumentException(
            s"Duplicate values [${other.mkString(",")}] found for field [$fieldName] in [$className]"
          )
        )
    }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def classNameFrom[T](entities: Seq[T]): String =
    entities.headOption.map(_.getClass.getSimpleName) match {
      case Some(name) => name
      case None => throw new IllegalArgumentException(s"Failed to find class name from [${entities.length.toString}] entities")
    }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def fieldNameFrom[T <: Product, V](entities: Seq[T], field: T => V): String = {
    val fieldName = for {
      entity <- entities.headOption
      fieldValue = field(entity)
      fieldIndex <- entity.productIterator.zipWithIndex.find(e => e._1 == fieldValue).map(_._2)
    } yield {
      entity.productElementName(fieldIndex)
    }

    fieldName match {
      case Some(name) => name
      case None => throw new IllegalArgumentException(s"Failed to find field name from [${entities.length.toString}] entities")
    }
  }
}
