package stasis.core.commands

import java.time.Instant
import java.util.UUID

import scalapb.TypeMapper

import stasis.common.proto.Uuid

package object proto {
  implicit val instantMapper: TypeMapper[Long, Instant] =
    TypeMapper[Long, Instant](baseToCustom = Instant.ofEpochMilli)(customToBase = _.toEpochMilli)

  implicit val uuidMapper: TypeMapper[Uuid, UUID] =
    TypeMapper[Uuid, UUID](
      baseToCustom = u => new UUID(u.mostSignificantBits, u.leastSignificantBits)
    )(
      customToBase = u => Uuid(mostSignificantBits = u.getMostSignificantBits, leastSignificantBits = u.getLeastSignificantBits)
    )

  implicit val sourceMapper: TypeMapper[String, CommandSource] =
    TypeMapper[String, CommandSource](baseToCustom = CommandSource.apply)(customToBase = _.name)

}
