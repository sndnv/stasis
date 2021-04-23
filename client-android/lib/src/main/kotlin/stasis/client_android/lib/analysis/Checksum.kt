package stasis.client_android.lib.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.blackholeSink
import okio.buffer
import okio.hashingSource
import okio.source
import java.math.BigInteger
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32

interface Checksum {
    suspend fun calculate(file: Path): BigInteger

    companion object {
        fun apply(checksum: String): Checksum =
            when (checksum.toLowerCase(Locale.getDefault())) {
                "crc32" -> CRC32
                "md5" -> MD5
                "sha1" -> SHA1
                "sha256" -> SHA256
                else -> throw IllegalArgumentException("Unsupported checksum provided: [$checksum]")
            }

        object CRC32 : Checksum {
            override suspend fun calculate(file: Path): BigInteger = crc32(file)
        }

        object MD5 : Checksum {
            override suspend fun calculate(file: Path): BigInteger = md5(file)
        }

        object SHA1 : Checksum {
            override suspend fun calculate(file: Path): BigInteger = sha1(file)
        }

        object SHA256 : Checksum {
            override suspend fun calculate(file: Path): BigInteger = sha256(file)
        }

        suspend fun crc32(file: Path): BigInteger {
            val checksum = CRC32()

            withContext(Dispatchers.IO) {
                file.source().buffer().use { source ->
                    while (true) {
                        val bytes = source.readByteArray()
                        if (bytes.isNotEmpty()) {
                            checksum.update(bytes)
                        } else {
                            break
                        }
                    }
                }
            }

            return BigInteger.valueOf(checksum.value)
        }

        suspend fun md5(file: Path): BigInteger =
            digest(file, algorithm = "MD5")

        suspend fun sha1(file: Path): BigInteger =
            digest(file, algorithm = "SHA-1")

        suspend fun sha256(file: Path): BigInteger =
            digest(file, algorithm = "SHA-256")

        suspend fun digest(file: Path, algorithm: String): BigInteger {
            val checksum = MessageDigest.getInstance(algorithm)

            withContext(Dispatchers.IO) {
                file.source().hashingSource(checksum).buffer().use {
                    it.readAll(blackholeSink())
                }
            }

            return BigInteger(1, checksum.digest())
        }
    }
}
