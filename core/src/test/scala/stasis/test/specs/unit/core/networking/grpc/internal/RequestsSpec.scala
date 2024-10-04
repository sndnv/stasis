package stasis.test.specs.unit.core.networking.grpc.internal

import stasis.core.networking.grpc.internal.Implicits
import stasis.core.networking.grpc.internal.Requests
import stasis.core.networking.grpc.proto
import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageRequest
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec

class RequestsSpec extends AsyncUnitSpec {

  import Implicits._

  private val storageRequest = CrateStorageRequest(
    crate = Crate.generateId(),
    size = 42,
    copies = 3,
    origin = Node.generateId(),
    source = Node.generateId()
  )

  private val reserveRequest =
    proto.ReserveRequest(
      id = Some(storageRequest.id),
      crate = Some(storageRequest.crate),
      size = storageRequest.size,
      copies = storageRequest.copies,
      origin = Some(storageRequest.origin),
      source = Some(storageRequest.source)
    )

  it should "marshal reserve requests" in {
    Requests.Reserve.marshal(storageRequest) should be(reserveRequest)
  }

  it should "unmarshal reserve requests" in {
    Requests.Reserve.unmarshal(reserveRequest) should be(Right(storageRequest))
  }

  it should "fail to unmarshal reserve requests with a missing id" in {
    Requests.Reserve.unmarshal(reserveRequest.copy(id = None)) match {
      case Right(result) => fail(s"Received unexpected result: [$result]")
      case Left(error)   => error.getMessage should startWith("Missing [id]")
    }
  }

  it should "fail to unmarshal reserve requests with a missing crate" in {
    Requests.Reserve.unmarshal(reserveRequest.copy(crate = None)) match {
      case Right(result) => fail(s"Received unexpected result: [$result]")
      case Left(error)   => error.getMessage should startWith("Missing [crate]")
    }
  }

  it should "fail to unmarshal reserve requests with a missing origin" in {
    Requests.Reserve.unmarshal(reserveRequest.copy(origin = None)) match {
      case Right(result) => fail(s"Received unexpected result: [$result]")
      case Left(error)   => error.getMessage should startWith("Missing [origin]")
    }
  }

  it should "fail to unmarshal reserve requests with a missing source" in {
    Requests.Reserve.unmarshal(reserveRequest.copy(source = None)) match {
      case Right(result) => fail(s"Received unexpected result: [$result]")
      case Left(error)   => error.getMessage should startWith("Missing [source]")
    }
  }
}
