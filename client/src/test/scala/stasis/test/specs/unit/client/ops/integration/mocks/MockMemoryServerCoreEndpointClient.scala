package stasis.test.specs.unit.client.ops.integration.mocks

import java.util.concurrent.atomic.AtomicInteger

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.routing.Node
import stasis.test.specs.unit.client.mocks.MockServerCoreEndpointClient

class MockMemoryServerCoreEndpointClient()(implicit mat: Materializer)
    extends MockServerCoreEndpointClient(self = Node.generateId(), crates = Map.empty) {
  import mat.executionContext

  private val stored: TrieMap[Crate.Id, ByteString] = TrieMap.empty

  private val pushedCount = new AtomicInteger(0)
  private val pulledCount = new AtomicInteger(0)

  override def push(manifest: Manifest, content: Source[ByteString, NotUsed]): Future[Done] =
    content.runFold(ByteString.empty)(_ concat _).map { bytes =>
      stored.put(manifest.crate, bytes)
      val _ = pushedCount.incrementAndGet()
      Done
    }

  override def pull(crate: Crate.Id): Future[Option[Source[ByteString, NotUsed]]] =
    Future.successful(
      stored.get(crate).map { bytes =>
        val _ = pulledCount.incrementAndGet()
        Source.single(bytes)
      }
    )

  def content(crate: Crate.Id): Option[Source[ByteString, NotUsed]] =
    stored.get(crate).map(Source.single)

  def pushed: Int = pushedCount.get()
  def pulled: Int = pulledCount.get()
}

object MockMemoryServerCoreEndpointClient {
  def apply()(implicit mat: Materializer): MockMemoryServerCoreEndpointClient =
    new MockMemoryServerCoreEndpointClient()
}
