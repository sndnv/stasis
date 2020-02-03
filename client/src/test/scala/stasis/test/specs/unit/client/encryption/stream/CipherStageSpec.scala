package stasis.test.specs.unit.client.encryption.stream

import java.security.KeyFactory
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}

import stasis.client.encryption.stream.CipherStage
import stasis.client.encryption.Aes.TagSize
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class CipherStageSpec extends AsyncUnitSpec with EncodingHelpers {
  private implicit val system: ActorSystem = ActorSystem(name = "CipherStageSpec")
  private implicit val mat: ActorMaterializer = ActorMaterializer()

  private val aesEncryptionKey = "PeKeM+i2PY1zTYrj52HnPg=="
  private val aesEncryptionIv = "i+/GSKy4En8O5nj9U4z7tA=="

  private val rsaPublicKey =
    "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKlrDdKZZwhC" +
      "dh1d+bPVigS5dcQYTlSmDdF659DzUsXvGEzMyeicr6" +
      "3bjw0WvZIgmMuRCYQ8uY1czvytNn1XO9UCAwEAAQ=="

  private val rsaPrivateKey =
    "MIIBVgIBADANBgkqhkiG9w0BAQEFAASCAUAwggE8AgEA" +
      "AkEAqWsN0plnCEJ2HV35s9WKBLl1xBhOVKYN0Xrn0P" +
      "NSxe8YTMzJ6JyvrduPDRa9kiCYy5EJhDy5jVzO/K02" +
      "fVc71QIDAQABAkEAgt9B5EfAQa8lkvX/aJ4yagfiI0" +
      "Mrb1R/JC0vWkg8zszr+kGTxVflYwTkMNWgZqwQuf4C" +
      "NLXKzO5Z3qdDzDI4mQIhANpMzFJNyLC4UDUnLoERjs" +
      "mL/6EoBBLn6ulYsF1CGTWzAiEAxq0nv1guv+2NqTf9" +
      "AMLPUnqv5gPudjB86qRBSJUcFFcCIHrJA4uvkJhFs2" +
      "eSOEgElimrAwekOFZh9/F0Hw71ZLSdAiEAvrpONRfv" +
      "0Vq5KyFPpiJeq3ySToupqhbEZPGIpqhWy4MCIQCrLL" +
      "yQvN0ycPmMDgDfj3MGPEeT0l8wDebrwom1/HSkFw=="

  private val plaintextData = "some-plaintext-data"

  private val aesEncryptedData =
    "DW/YzX/aXwio9P25x8XgNKHaDgKcEHyjBgIkVXPY2Acxt/s="

  private val rsaEncryptedData =
    "OJznDc2ETEv873roPfgZBt1Z+JiDhiluvzzxo9SWSC4uTbIMIFf1qR4j9J2zaRX8UWNmv+bFjhkIPIp7++Lx7w=="

  "A CipherStage" should "encrypt streaming data (AES)" in {
    val stage = new CipherStage(
      algorithm = "AES",
      cipherMode = "GCM",
      padding = "NoPadding",
      operationMode = Cipher.ENCRYPT_MODE,
      key = new SecretKeySpec(aesEncryptionKey.decodeFromBase64.toArray, "AES"),
      spec = Some(new GCMParameterSpec(TagSize, aesEncryptionIv.decodeFromBase64.toArray))
    )

    Source
      .single(ByteString(plaintextData))
      .via(stage)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualEncryptedData =>
        actualEncryptedData should be(aesEncryptedData.decodeFromBase64)
      }
  }

  it should "decrypt streaming data (AES)" in {
    val stage = new CipherStage(
      algorithm = "AES",
      cipherMode = "GCM",
      padding = "NoPadding",
      operationMode = Cipher.DECRYPT_MODE,
      key = new SecretKeySpec(aesEncryptionKey.decodeFromBase64.toArray, "AES"),
      spec = Some(new GCMParameterSpec(TagSize, aesEncryptionIv.decodeFromBase64.toArray))
    )

    Source
      .single(aesEncryptedData.decodeFromBase64)
      .via(stage)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualPlaintextData =>
        actualPlaintextData should be(ByteString(plaintextData))
      }
  }

  it should "handle empty stream elements (AES / default)" in {
    val stage = new CipherStage(
      algorithm = "AES",
      cipherMode = "GCM",
      padding = "NoPadding",
      operationMode = Cipher.ENCRYPT_MODE,
      key = new SecretKeySpec(aesEncryptionKey.decodeFromBase64.toArray, "AES"),
      spec = Some(new GCMParameterSpec(TagSize, aesEncryptionIv.decodeFromBase64.toArray))
    )

    Source(List(ByteString(""), ByteString(plaintextData), ByteString("")))
      .via(stage)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualEncryptedData =>
        actualEncryptedData should be(aesEncryptedData.decodeFromBase64)
      }
  }

  it should "encrypt data (AES / ECB)" in {
    // warning: for testing purposes only; do not use AES in ECB mode!

    val stage = new CipherStage(
      algorithm = "AES",
      cipherMode = "ECB",
      padding = "NoPadding",
      operationMode = Cipher.ENCRYPT_MODE,
      key = new SecretKeySpec(aesEncryptionKey.decodeFromBase64.toArray, "AES"),
      spec = None
    )

    Source
      .single(ByteString("1234567890ABCDEF")) // 16 bytes in size, to avoid using padding
      .via(stage)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualEncryptedData =>
        actualEncryptedData should be("5BgTzTE1hYHU95bcG7udNA==".decodeFromBase64)
      }
  }

  it should "decrypt data (AES / ECB)" in {
    // warning: for testing purposes only; do not use AES in ECB mode!

    val stage = new CipherStage(
      algorithm = "AES",
      cipherMode = "ECB",
      padding = "NoPadding",
      operationMode = Cipher.DECRYPT_MODE,
      key = new SecretKeySpec(aesEncryptionKey.decodeFromBase64.toArray, "AES"),
      spec = None
    )

    Source
      .single("5BgTzTE1hYHU95bcG7udNA==".decodeFromBase64)
      .via(stage)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualPlaintextData =>
        actualPlaintextData should be(ByteString("1234567890ABCDEF"))
      }
  }

  it should "encrypt data (RSA)" in {
    // warning: for testing purposes only; do not use RSA in ECB(None) mode!
    // warning: for testing purposes only; do not use RSA for encrypting file content / metadata!

    val keyFactory = KeyFactory.getInstance("RSA")
    val privateKeySpec = new PKCS8EncodedKeySpec(rsaPrivateKey.decodeFromBase64.toArray)
    val privateKey = keyFactory.generatePrivate(privateKeySpec)

    val stage = new CipherStage(
      algorithm = "RSA",
      cipherMode = "ECB",
      padding = "PKCS1Padding",
      operationMode = Cipher.ENCRYPT_MODE,
      key = privateKey,
      spec = None
    )

    Source
      .single(ByteString(plaintextData))
      .via(stage)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualEncryptedData =>
        actualEncryptedData should be(rsaEncryptedData.decodeFromBase64)
      }
  }

  it should "decrypt data (RSA)" in {
    // warning: for testing purposes only; do not use RSA in ECB(None) mode!
    // warning: for testing purposes only; do not use RSA for encrypting file content / metadata!

    val keyFactory = KeyFactory.getInstance("RSA")
    val publicKeySpec = new X509EncodedKeySpec(rsaPublicKey.decodeFromBase64.toArray)
    val publicKey = keyFactory.generatePublic(publicKeySpec)

    val stage = new CipherStage(
      algorithm = "RSA",
      cipherMode = "ECB",
      padding = "PKCS1Padding",
      operationMode = Cipher.DECRYPT_MODE,
      key = publicKey,
      spec = None
    )

    Source
      .single(rsaEncryptedData.decodeFromBase64)
      .via(stage)
      .runFold(ByteString.empty)(_ concat _)
      .map { actualPlaintextData =>
        actualPlaintextData should be(ByteString(plaintextData))
      }
  }
}
