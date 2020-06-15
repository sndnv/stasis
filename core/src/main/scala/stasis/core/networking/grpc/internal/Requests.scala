package stasis.core.networking.grpc.internal

import java.util.UUID

import stasis.core.networking.grpc.proto
import stasis.core.persistence.CrateStorageRequest

object Requests {

  import Implicits._

  object Reserve {
    def marshal(storageRequest: CrateStorageRequest): proto.ReserveRequest =
      proto.ReserveRequest(
        id = Some(storageRequest.id),
        crate = Some(storageRequest.crate),
        size = storageRequest.size,
        copies = storageRequest.copies,
        origin = Some(storageRequest.origin),
        source = Some(storageRequest.source)
      )

    def unmarshal(
      reserveRequest: proto.ReserveRequest
    ): Either[IllegalArgumentException, CrateStorageRequest] =
      for {
        id <- reserveRequest.id
          .toRight(new IllegalArgumentException(s"Missing [id]: [${reserveRequest.toProtoString}]"))
          .map(uuid => uuid: UUID)
        crate <- reserveRequest.crate
          .toRight(new IllegalArgumentException(s"Missing [crate]: [${reserveRequest.toProtoString}]"))
          .map(uuid => uuid: UUID)
        origin <- reserveRequest.origin
          .toRight(new IllegalArgumentException(s"Missing [origin]: [${reserveRequest.toProtoString}]"))
          .map(uuid => uuid: UUID)
        source <- reserveRequest.source
          .toRight(new IllegalArgumentException(s"Missing [source]: [${reserveRequest.toProtoString}]"))
          .map(uuid => uuid: UUID)
      } yield {
        CrateStorageRequest(
          id = id,
          crate = crate,
          size = reserveRequest.size,
          copies = reserveRequest.copies,
          origin = origin,
          source = source
        )
      }
  }
}
