import Foundation
@testable import StasisClient
import Testing

@Suite("Keychain")
struct KeychainTests {
    private func freshKeychain() -> Keychain {
        Keychain(service: "stasis.tests.\(UUID().uuidString)")
    }

    @Test("stores and retrieves string values")
    func storeAndRetrieveString() throws {
        let keychain = freshKeychain()
        try keychain.set("hunter2", account: "password")
        #expect(try keychain.string(account: "password") == "hunter2")
        try keychain.remove(account: "password")
    }

    @Test("overwrites existing values")
    func overwrites() throws {
        let keychain = freshKeychain()
        try keychain.set("first", account: "k")
        try keychain.set("second", account: "k")
        #expect(try keychain.string(account: "k") == "second")
        try keychain.remove(account: "k")
    }

    @Test("returns nil for missing entries")
    func missingReturnsNil() throws {
        let keychain = freshKeychain()
        #expect(try keychain.string(account: "missing") == nil)
        #expect(try keychain.data(account: "missing") == nil)
    }

    @Test("remove is idempotent")
    func removeIdempotent() throws {
        let keychain = freshKeychain()
        try keychain.remove(account: "never-set")
        try keychain.set("v", account: "k")
        try keychain.remove(account: "k")
        try keychain.remove(account: "k")
        #expect(try keychain.string(account: "k") == nil)
    }

    @Test("stores arbitrary data payloads")
    func storeData() throws {
        let keychain = freshKeychain()
        let payload = Data([0x00, 0x01, 0x02, 0xff])
        try keychain.set(payload, account: "bytes")
        #expect(try keychain.data(account: "bytes") == payload)
        try keychain.remove(account: "bytes")
    }
}

@Suite("KeychainError")
struct KeychainErrorTests {
    @Test("describes an unexpected status")
    func message() {
        #expect(KeychainError.unexpectedStatus(-25300).errorDescription == "Keychain access failed with status [-25300]")
    }
}
