package stasis.test.specs.unit.persistence.mocks

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.packaging
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.CrateStore

import scala.concurrent.{ExecutionContext, Future}

class MockCrateStore(implicit system: ActorSystem, timeout: Timeout) extends CrateStore {
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val store: ActorRef = system.actorOf(
    CrateSinkActor.props(),
    s"mock-crate-store-${java.util.UUID.randomUUID()}"
  )

  override def persist(
    manifest: packaging.Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    content
      .runFold(ByteString.empty) {
        case (folded, chunk) =>
          folded.concat(chunk)
      }
      .flatMap { data =>
        (store ? CrateSinkActor.PutData(manifest, data)).mapTo[Done]
      }

  override def retrieve(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] =
    (store ? CrateSinkActor.GetData(crate))
      .mapTo[Option[(Manifest, ByteString)]]
      .map(_.map { case (_, data) => Source.single(data) })
}
