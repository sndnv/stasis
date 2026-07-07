import Foundation

public enum DecryptedCrates {
    public static func decrypt(
        _ crates: [RecoveryCrate],
        withPartSecret: @escaping @Sendable (String) -> DeviceFileSecret,
        decryptor: any Decrypting
    ) -> [RecoveryCrate] {
        crates.map { crate in
            let partPath = crate.partPath
            let inner = crate.source
            return RecoveryCrate(partId: crate.partId, partPath: partPath) {
                let cipherStream = try await inner()
                var ciphertext = Data()
                for try await chunk in cipherStream {
                    ciphertext.append(chunk)
                }
                let plaintext = try decryptor.decrypt(ciphertext, fileSecret: withPartSecret(partPath))
                return AsyncThrowingStream { continuation in
                    continuation.yield(plaintext)
                    continuation.finish()
                }
            }
        }
    }
}
