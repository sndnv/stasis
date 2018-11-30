package stasis.test.specs.unit.persistence.mocks

import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.util.ByteString
import stasis.packaging.{Crate, Manifest}

class StoreActor extends Actor with ActorLogging with Stash {

  def streaming(
    store: Map[Crate.Id, (Manifest, ByteString)],
    manifest: Manifest,
    parts: ByteString
  ): Receive = {
    case element: ByteString =>
      sender ! StoreActor.CanStream
      context.become(streaming(store, manifest, parts = parts ++ element))

    case StoreActor.StreamComplete =>
      unstashAll()
      context.become(idle(store = store + (manifest.crate -> (manifest, parts))))

    case StoreActor.StreamFailed(e) =>
      unstashAll()
      log.error("Streaming for crate with manifest [{}] failed", manifest, e)
      context.become(idle(store))

    case _ =>
      stash()
  }

  def idle(store: Map[Crate.Id, (Manifest, ByteString)]): Receive = {
    case StoreActor.InitStreaming(manifest) =>
      sender ! StoreActor.CanStream
      context.become(streaming(store, manifest, parts = ByteString.empty))

    case StoreActor.PutData(manifest, content) =>
      context.become(idle(store = store + (manifest.crate -> (manifest, content))))

    case StoreActor.GetData(crate) =>
      sender ! store.get(crate)
  }

  override def receive: Receive = idle(store = Map.empty)
}

object StoreActor {
  case class InitStreaming(manifest: Manifest)
  case object CanStream
  case object StreamComplete
  case class StreamFailed(e: Throwable)

  case class PutData(manifest: Manifest, content: ByteString)
  case class GetData(crate: Crate.Id)

  def props(): Props = Props(classOf[StoreActor])
}
