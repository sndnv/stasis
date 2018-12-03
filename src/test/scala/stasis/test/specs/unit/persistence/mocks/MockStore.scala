package stasis.test.specs.unit.persistence.mocks

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import stasis.packaging
import stasis.packaging.{Crate, Manifest}
import stasis.persistence.Store

import scala.concurrent.{ExecutionContext, Future}

class MockStore(implicit system: ActorSystem, timeout: Timeout) extends Store {
  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val store: ActorRef = system.actorOf(StoreActor.props())

  override def persist(
    manifest: packaging.Manifest,
    content: Source[ByteString, NotUsed]
  ): Future[Done] =
    Future {
      val _ = content.runWith(
        Sink.actorRefWithAck(
          store,
          onInitMessage = StoreActor.InitStreaming(manifest),
          ackMessage = StoreActor.CanStream,
          onCompleteMessage = StoreActor.StreamComplete,
          onFailureMessage = e => StoreActor.StreamFailed(e)
        )
      )

      Done
    }

  override def retrieve(
    crate: Crate.Id
  ): Future[Option[Source[ByteString, NotUsed]]] =
    (store ? StoreActor.GetData(crate))
      .mapTo[Option[(Manifest, ByteString)]]
      .map(_.map { case (_, data) => Source.single(data) })
}
