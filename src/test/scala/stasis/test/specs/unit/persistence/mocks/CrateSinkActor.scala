package stasis.test.specs.unit.persistence.mocks

import akka.Done
import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.util.ByteString
import stasis.packaging.{Crate, Manifest}

class CrateSinkActor extends Actor with ActorLogging with Stash {

  def streaming(
    store: Map[Crate.Id, (Manifest, ByteString)],
    manifest: Manifest,
    parts: ByteString
  ): Receive = {
    case element: ByteString =>
      sender ! CrateSinkActor.CanStream
      log.debug("Processing chunk with size [{}] for crate with manifest [{}]", element.size, manifest)
      context.become(streaming(store, manifest, parts = parts ++ element))

    case CrateSinkActor.StreamComplete =>
      unstashAll()
      log.debug("Streaming complete for crate with manifest [{}]", manifest)
      context.become(idle(store = store + (manifest.crate -> (manifest, parts))))

    case CrateSinkActor.StreamFailed(e) =>
      unstashAll()
      log.error("Streaming for crate with manifest [{}] failed", manifest, e)
      context.become(idle(store))

    case _ =>
      stash()
  }

  def idle(store: Map[Crate.Id, (Manifest, ByteString)]): Receive = {
    case CrateSinkActor.InitStreaming(manifest) =>
      sender ! CrateSinkActor.CanStream
      log.debug("Starting streaming for crate with manifest [{}]", manifest)
      context.become(streaming(store, manifest, parts = ByteString.empty))

    case CrateSinkActor.PutData(manifest, content) =>
      log.debug("Putting data with size [{}] for crate with manifest [{}]", content, manifest)
      sender ! Done
      context.become(idle(store = store + (manifest.crate -> (manifest, content))))

    case CrateSinkActor.GetData(crate) =>
      log.debug("Getting data for crate [{}]", crate)
      sender ! store.get(crate)
  }

  override def receive: Receive = idle(store = Map.empty)
}

object CrateSinkActor {
  case class InitStreaming(manifest: Manifest)
  case object CanStream
  case object StreamComplete
  case class StreamFailed(e: Throwable)

  case class PutData(manifest: Manifest, content: ByteString)
  case class GetData(crate: Crate.Id)

  def props(): Props = Props(classOf[CrateSinkActor])
}
