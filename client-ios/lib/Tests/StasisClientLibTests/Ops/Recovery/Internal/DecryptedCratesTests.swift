import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("DecryptedCrates")
struct DecryptedCratesTests {
    @Test("decryption is deferred until the inner source is invoked")
    func lazyDecryption() async throws {
        let firstInvoked = Flag()
        let secondInvoked = Flag()
        let thirdInvoked = Flag()

        let ciphertext = Data([MockEncrypting.sentinel]) + Data("original".utf8)
        let crates: [RecoveryCrate] = [
            RecoveryCrate(partId: 0, partPath: "/tmp/file/one__part=0") {
                firstInvoked.set(); return makeDataStream(ciphertext)
            },
            RecoveryCrate(partId: 1, partPath: "/tmp/file/one__part=1") {
                secondInvoked.set(); return makeDataStream(ciphertext)
            },
            RecoveryCrate(partId: 2, partPath: "/tmp/file/one__part=2") {
                thirdInvoked.set(); return makeDataStream(ciphertext)
            }
        ]

        let decrypted = DecryptedCrates.decrypt(
            crates,
            withPartSecret: { partPath in
                DeviceFileSecret(file: partPath, iv: Data(), key: Data())
            },
            decryptor: MockDecrypting()
        )

        #expect(decrypted.count == 3)
        #expect(!firstInvoked.isSet)
        #expect(!secondInvoked.isSet)
        #expect(!thirdInvoked.isSet)

        let firstStream = try await decrypted[0].source()
        var collected = Data()
        for try await chunk in firstStream { collected.append(chunk) }

        #expect(String(data: collected, encoding: .utf8) == "original")
        #expect(firstInvoked.isSet)
        #expect(!secondInvoked.isSet)
        #expect(!thirdInvoked.isSet)
    }

    @Test("preserves partId and partPath in the returned crates")
    func preservesIdentity() async throws {
        let crates: [RecoveryCrate] = (0..<3).map { id in
            RecoveryCrate(partId: id, partPath: "/tmp/file/one__part=\(id)") {
                makeDataStream(Data([MockEncrypting.sentinel]))
            }
        }

        let decrypted = DecryptedCrates.decrypt(
            crates,
            withPartSecret: { path in DeviceFileSecret(file: path, iv: Data(), key: Data()) },
            decryptor: MockDecrypting()
        )

        #expect(decrypted.map(\.partId) == [0, 1, 2])
        #expect(decrypted.map(\.partPath) == [
            "/tmp/file/one__part=0",
            "/tmp/file/one__part=1",
            "/tmp/file/one__part=2"
        ])
    }

    @Test("decrypts every crate's payload to the underlying plaintext")
    func roundTripsAllCrates() async throws {
        let payloads = ["alpha", "beta", "gamma"]
        let crates: [RecoveryCrate] = payloads.enumerated().map { id, payload in
            RecoveryCrate(partId: id, partPath: "/tmp/file/one__part=\(id)") {
                makeDataStream(Data([MockEncrypting.sentinel]) + Data(payload.utf8))
            }
        }

        let decrypted = DecryptedCrates.decrypt(
            crates,
            withPartSecret: { path in DeviceFileSecret(file: path, iv: Data(), key: Data()) },
            decryptor: MockDecrypting()
        )

        var results: [String] = []
        for crate in decrypted {
            let stream = try await crate.source()
            var collected = Data()
            for try await chunk in stream { collected.append(chunk) }
            results.append(String(data: collected, encoding: .utf8) ?? "")
        }
        #expect(results == payloads)
    }

    @Test("propagates errors raised by the inner source closure")
    func propagatesSourceFailures() async {
        let crates: [RecoveryCrate] = [
            RecoveryCrate(partId: 0, partPath: "/tmp/file/one__part=0") {
                throw TestFailure(message: "fetch failed")
            }
        ]

        let decrypted = DecryptedCrates.decrypt(
            crates,
            withPartSecret: { path in DeviceFileSecret(file: path, iv: Data(), key: Data()) },
            decryptor: MockDecrypting()
        )

        await #expect(throws: TestFailure(message: "fetch failed")) {
            _ = try await decrypted[0].source()
        }
    }

    @Test("propagates errors raised by the decryptor")
    func propagatesDecryptorFailures() async {
        let crates: [RecoveryCrate] = [
            RecoveryCrate(partId: 0, partPath: "/tmp/file/one__part=0") {
                makeDataStream(Data("invalid-no-sentinel".utf8))
            }
        ]

        let decrypted = DecryptedCrates.decrypt(
            crates,
            withPartSecret: { path in DeviceFileSecret(file: path, iv: Data(), key: Data()) },
            decryptor: MockDecrypting()
        )

        await #expect(throws: MockDecryptingError.missingSentinel) {
            _ = try await decrypted[0].source()
        }
    }
}
