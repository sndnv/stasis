package stasis.test.specs.unit

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{fixture, BeforeAndAfterAll, Matchers}

abstract class AkkaAsyncUnitSpec(systemName: String)
    extends TestKit(ActorSystem(s"AkkaAsyncUnitSpec-$systemName"))
    with ImplicitSender
    with fixture.AsyncFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
