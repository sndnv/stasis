package stasis.core.persistence.backends.file

import java.nio.ByteOrder
import java.util.UUID

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.core.persistence.backends.StreamingBackend
import stasis.core.persistence.backends.file.container.Container
import stasis.core.persistence.backends.file.container.ops.ConversionOps

import scala.concurrent.{ExecutionContext, Future}

class ContainerBackend(
  val path: String,
  val maxChunkSize: Int,
  val maxChunks: Int
)(implicit ec: ExecutionContext)
    extends StreamingBackend {

  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  private val container: Container = new Container(path, maxChunkSize, maxChunks)

  override def init(): Future[Done] =
    container.create()

  override def drop(): Future[Done] =
    container.destroy()

  override def sink(key: UUID): Future[Sink[ByteString, Future[Done]]] =
    container.sink(key)

  override def source(key: UUID): Future[Option[Source[ByteString, NotUsed]]] =
    container.source(key).map(_.map(_.mapMaterializedValue(_ => NotUsed)))

  override def delete(key: UUID): Future[Boolean] =
    container.delete(key)

  override def contains(key: UUID): Future[Boolean] =
    container.contains(key)

  override def canStore(bytes: Long): Future[Boolean] =
    container.canStore(bytes)
}
