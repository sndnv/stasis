import Foundation
@testable import StasisClientLib
import Testing

@Suite("AccessTokenResponse")
struct AccessTokenResponseTests {
    @Test("supports providing JWT claims")
    func supportsProvidingJwtClaims() throws {
        let validResponse = AccessTokenResponse(
            accessToken: TestJwt.create(payload: ["sub": "test-subject", "a": "b"]),
            refreshToken: nil,
            expiresIn: 1,
            scope: nil
        )

        let invalidResponse = AccessTokenResponse(
            accessToken: "test-token",
            refreshToken: nil,
            expiresIn: 1,
            scope: nil
        )

        let claims = try validResponse.claims.get()
        #expect(claims == AccessTokenResponse.Claims(claims: ["sub": "test-subject", "a": "b"]))

        #expect(throws: JwtDecodingError.self) {
            try invalidResponse.claims.get()
        }
    }

    @Test("supports checking if a token has expired")
    func supportsCheckingIfTokenHasExpired() {
        let now = Date().timeIntervalSince1970
        let validResponse = AccessTokenResponse(
            accessToken: TestJwt.create(payload: ["exp": now + 60]),
            refreshToken: nil,
            expiresIn: 1,
            scope: nil
        )

        let expiredResponse = AccessTokenResponse(
            accessToken: TestJwt.create(payload: ["exp": now - 60]),
            refreshToken: nil,
            expiresIn: 1,
            scope: nil
        )

        #expect(validResponse.hasNotExpired)
        #expect(!expiredResponse.hasNotExpired)
    }

    @Test("treats a token without an expiry as expired")
    func treatsMissingExpiryAsExpired() {
        let response = AccessTokenResponse(
            accessToken: TestJwt.create(payload: ["sub": "test-subject"]),
            refreshToken: nil,
            expiresIn: 1,
            scope: nil
        )
        #expect(!response.hasNotExpired)
    }

    @Test("treats a malformed token as expired")
    func treatsMalformedTokenAsExpired() {
        let response = AccessTokenResponse(
            accessToken: "not-a-jwt",
            refreshToken: nil,
            expiresIn: 1,
            scope: nil
        )
        #expect(!response.hasNotExpired)
    }

    @Test("supports providing token subject from claims")
    func supportsProvidingTokenSubjectFromClaims() {
        let validClaims = AccessTokenResponse.Claims(claims: ["sub": "test-subject", "a": "b"])
        let invalidClaims = AccessTokenResponse.Claims(claims: [:])

        #expect(validClaims.subject == "test-subject")
        #expect(invalidClaims.subject == nil)
    }

    @Test("round-trips via JSON snake_case fields")
    func roundTripsJson() throws {
        let original = AccessTokenResponse(
            accessToken: "abc",
            refreshToken: "def",
            expiresIn: 42,
            scope: "test-scope"
        )

        let encoded = try JSONEncoder().encode(original)
        let json = try #require(String(data: encoded, encoding: .utf8))
        #expect(json.contains("\"access_token\""))
        #expect(json.contains("\"refresh_token\""))
        #expect(json.contains("\"expires_in\""))

        let decoded = try JSONDecoder().decode(AccessTokenResponse.self, from: encoded)
        #expect(decoded == original)
    }
}

enum TestJwt {
    static func create(payload: [String: Any]) -> String {
        let header: [String: Any] = ["alg": "none", "typ": "JWT"]
        let headerData = (try? JSONSerialization.data(withJSONObject: header)) ?? Data()
        let payloadData = (try? JSONSerialization.data(withJSONObject: payload)) ?? Data()
        let signature = Data("fake-sig".utf8)
        return [
            headerData.base64UrlEncodedString(),
            payloadData.base64UrlEncodedString(),
            signature.base64UrlEncodedString()
        ].joined(separator: ".")
    }
}
