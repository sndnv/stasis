package stasis.test.specs.unit.persistence.mocks

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object MapStoreActor {
  sealed trait Message[K, V]
  final case class Put[K, V](key: K, value: V, replyTo: ActorRef[Done]) extends Message[K, V]
  final case class Remove[K, V](key: K, replyTo: ActorRef[Done]) extends Message[K, V]
  final case class Get[K, V](key: K, replyTo: ActorRef[Option[V]]) extends Message[K, V]
  final case class GetAll[K, V](replyTo: ActorRef[Map[K, V]]) extends Message[K, V]

  def store[K, V](map: Map[K, V]): Behavior[Message[K, V]] = Behaviors.receive { (_, message) =>
    message match {
      case Put(key, value, replyTo) =>
        replyTo ! Done
        store(map = map + (key -> value))

      case Remove(key, replyTo) =>
        replyTo ! Done
        store(map = map - key)

      case Get(key, replyTo) =>
        replyTo ! map.get(key)
        Behaviors.same

      case GetAll(replyTo) =>
        replyTo ! map
        Behaviors.same
    }
  }
}
