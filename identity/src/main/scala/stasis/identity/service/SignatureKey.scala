package stasis.identity.service

import java.io.FileNotFoundException
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

import scala.util.Try

import com.typesafe.{config => typesafe}
import org.jose4j.jwk.EllipticCurveJsonWebKey
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jwk.OctetSequenceJsonWebKey
import org.jose4j.jwk.RsaJsonWebKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.layers.security.keys.Generators

object SignatureKey {
  def fromConfig(signatureKeyConfig: typesafe.Config): JsonWebKey =
    fromConfig(signatureKeyConfig = signatureKeyConfig, fs = FileSystems.getDefault)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def fromConfig(signatureKeyConfig: typesafe.Config, fs: FileSystem): JsonWebKey = {
    val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

    signatureKeyConfig.getString("type").toLowerCase match {
      case "generated" =>
        log.debug("Generating new in-memory signature key...")

        generated(signatureKeyConfig.getConfig("generated"))

      case "stored" =>
        val storedKeyConfig = signatureKeyConfig.getConfig("stored")
        val path = fs.getPath(storedKeyConfig.getString("path"))

        if (Files.exists(path)) {
          log.debug("Loading stored signature key from [{}]...", path.normalize().toAbsolutePath.toString)

          stored(path)
        } else if (storedKeyConfig.getBoolean("generate-if-missing")) {
          log.debug(
            "Signature key file [{}] not found; generating new stored signature key...",
            path.normalize().toAbsolutePath.toString
          )

          val json = generated(signatureKeyConfig.getConfig("generated"))
            .toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE)

          val _ = Files.writeString(path, json)

          log.debug("Loading stored signature key from [{}]...", path.normalize().toAbsolutePath.toString)
          stored(path)
        } else {
          throw new FileNotFoundException(
            s"Signature key file [${path.normalize().toAbsolutePath.toString}] is not accessible or is missing"
          )
        }
    }
  }

  def stored(path: Path): JsonWebKey =
    JsonWebKey.Factory.newJwk(Files.readString(path))

  def generated(generatedKeyConfig: typesafe.Config): JsonWebKey =
    generatedKeyConfig.getString("type").toLowerCase match {
      case "rsa"    => generatedRsaKey(generatedKeyConfig.getConfig("rsa"))
      case "ec"     => generatedEcKey(generatedKeyConfig.getConfig("ec"))
      case "secret" => generatedSecretKey(generatedKeyConfig.getConfig("secret"))
    }

  private def generatedSecretKey(secretConfig: typesafe.Config): OctetSequenceJsonWebKey =
    Generators.generateRandomSecretKey(
      keyId = Try(secretConfig.getString("id")).toOption.filter(_.nonEmpty),
      algorithm = secretConfig.getString("algorithm")
    )

  private def generatedRsaKey(rsaConfig: typesafe.Config): RsaJsonWebKey =
    Generators.generateRandomRsaKey(
      keyId = Try(rsaConfig.getString("id")).toOption.filter(_.nonEmpty),
      keySize = rsaConfig.getInt("size"),
      algorithm = rsaConfig.getString("algorithm")
    )

  private def generatedEcKey(ecConfig: typesafe.Config): EllipticCurveJsonWebKey =
    Generators.generateRandomEcKey(
      keyId = Try(ecConfig.getString("id")).toOption.filter(_.nonEmpty),
      algorithm = ecConfig.getString("algorithm")
    )
}
