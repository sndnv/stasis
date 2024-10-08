package stasis.test.specs.unit.client.analysis

import org.apache.pekko.actor.ActorSystem

import stasis.client.analysis.Checksum
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.ResourceHelpers

class ChecksumSpec extends AsyncUnitSpec with ResourceHelpers {
  "A Checksum implementation" should "calculate digest checksums for files" in {
    val expectedChecksum = BigInt(
      "39848954327861382298906397279462496107584551024072291" +
        "193471648171519709574703666208888992159541063683939" +
        "25455196856231941899873364258769048659015726835952"
    )

    Checksum
      .digest(file = sourceFile, algorithm = "SHA-512")
      .map { actualChecksum =>
        actualChecksum should be(expectedChecksum)
      }
  }

  it should "calculate CRC32 checksums for files" in {
    val expectedChecksum = BigInt("595309308")

    Checksum.CRC32
      .calculate(file = sourceFile)
      .map { actualChecksum =>
        actualChecksum should be(expectedChecksum)
      }
  }

  it should "calculate MD5 checksums for files" in {
    val expectedChecksum = BigInt("124476216797902834426689518600270260549")

    Checksum.MD5
      .calculate(file = sourceFile)
      .map { actualChecksum =>
        actualChecksum should be(expectedChecksum)
      }
  }

  it should "calculate SHA1 checksums for files" in {
    val expectedChecksum = BigInt("545568869381376109390570303274177429634814154141")

    Checksum.SHA1
      .calculate(file = sourceFile)
      .map { actualChecksum =>
        actualChecksum should be(expectedChecksum)
      }
  }

  it should "calculate SHA256 checksums for files" in {
    val expectedChecksum = BigInt("96075381802863146919837723321013254207026023090442774587942108314962662306148")

    Checksum.SHA256
      .calculate(file = sourceFile)
      .map { actualChecksum =>
        actualChecksum should be(expectedChecksum)
      }
  }

  "Checksum" should "provide checksum implementations based on config" in {
    Checksum(checksum = "crc32") should be(Checksum.CRC32)
    Checksum(checksum = "md5") should be(Checksum.MD5)
    Checksum(checksum = "sha1") should be(Checksum.SHA1)
    Checksum(checksum = "sha256") should be(Checksum.SHA256)
  }

  private implicit val system: ActorSystem = ActorSystem(name = "ChecksumSpec")

  private val sourceFile = "/analysis/digest-source-file".asTestResource
}
