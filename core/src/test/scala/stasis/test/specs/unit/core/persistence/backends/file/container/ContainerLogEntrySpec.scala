package stasis.test.specs.unit.core.persistence.backends.file.container

import java.nio.ByteOrder
import java.util.UUID

import stasis.core.persistence.backends.file.container.Container
import stasis.core.persistence.backends.file.container.ops.ConversionOps
import stasis.test.specs.unit.UnitSpec

class ContainerLogEntrySpec extends UnitSpec {
  private implicit val byteOrder: ByteOrder = ConversionOps.DEFAULT_BYTE_ORDER

  "A ContainerLogEntry" should "have a fixed size" in {
    Container.LogEntry.ENTRY_SIZE should be(25)
  }

  it should "convert container log entries to and from bytes" in {
    val addEntry = Container.LogEntry(
      crate = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      event = Container.LogEntry.Add
    )

    val addEntryAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, 1, 0, 0, 0, 0, -45, 97, 40, -14
    )

    val removeEntry = Container.LogEntry(
      crate = UUID.fromString("9ab8d839-04d1-48c8-bf29-c5d7a894f064"),
      event = Container.LogEntry.Remove
    )

    val removeEntryAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, 2, 0, 0, 0, 0, 74, 104, 121, 72
    )

    Container.LogEntry.toBytes(addEntry) should be(addEntryAsBytes)
    Container.LogEntry.toBytes(removeEntry) should be(removeEntryAsBytes)

    Container.LogEntry.fromBytes(addEntryAsBytes.toArray) should be(Right(addEntry))
    Container.LogEntry.fromBytes(removeEntryAsBytes.toArray) should be(Right(removeEntry))
  }

  it should "fail to convert container log entries from bytes if an invalid event is provided" in {
    val invalidEntryAsBytes = Seq[Byte](
      -102, -72, -40, 57, 4, -47, 72, -56, -65, 41, -59, -41, -88, -108, -16, 100, 3, 0, 0, 0, 0, 74, 104, 121, 72
    )

    Container.LogEntry.fromBytes(invalidEntryAsBytes.toArray) match {
      case Left(e) =>
        e.getMessage should be("Failed to convert byte to event; unexpected value provided: [3]")

      case Right(entry) =>
        fail(s"Unexpected entry returned: [$entry]")
    }
  }
}
