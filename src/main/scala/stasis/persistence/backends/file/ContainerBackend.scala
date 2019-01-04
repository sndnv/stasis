package stasis.persistence.backends.file

import java.nio.ByteOrder

import scala.concurrent.{ExecutionContext, Future}

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.persistence.backends.StreamingBackend
import stasis.persistence.backends.file.container.Container
import stasis.persistence.backends.file.container.ops.ConversionOps

class ContainerBackend[K <: java.util.UUID](
  path: String,
  maxChunkSize: Int,
  maxChunks: Int
)(implicit ec: ExecutionContext)
    extends StreamingBackend[K] {

  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  private val container: Container = new Container(path, maxChunkSize, maxChunks)

  override def init(): Future[Done] =
    container.create()

  override def drop(): Future[Done] =
    container.destroy()

  override def sink(key: K): Future[Sink[ByteString, Future[Done]]] =
    container.sink(key)

  override def source(key: K): Future[Option[Source[ByteString, NotUsed]]] =
    container.source(key).map(_.map(_.mapMaterializedValue(_ => NotUsed)))

  override def delete(key: K): Future[Boolean] =
    container.delete(key)

  override def contains(key: K): Future[Boolean] =
    container.contains(key)

  override def canStore(bytes: Long): Future[Boolean] =
    container.canStore(bytes)
}
