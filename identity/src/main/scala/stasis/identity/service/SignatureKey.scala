package stasis.identity.service

import com.typesafe.{config => typesafe}
import org.jose4j.jwk.{EllipticCurveJsonWebKey, JsonWebKey, OctetSequenceJsonWebKey, RsaJsonWebKey}
import stasis.core.security.keys.Generators

import scala.io.Source
import scala.util.Try

object SignatureKey {
  def fromConfig(signatureKeyConfig: typesafe.Config): JsonWebKey =
    signatureKeyConfig.getString("type").toLowerCase match {
      case "generated" => generated(signatureKeyConfig.getConfig("generated"))
      case "stored"    => stored(signatureKeyConfig.getConfig("stored"))
    }

  def stored(storedKeyConfig: typesafe.Config): JsonWebKey = {
    val keySource = Source.fromFile(storedKeyConfig.getString("path"))
    try {
      JsonWebKey.Factory.newJwk(keySource.mkString)
    } finally {
      keySource.close()
    }
  }

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
