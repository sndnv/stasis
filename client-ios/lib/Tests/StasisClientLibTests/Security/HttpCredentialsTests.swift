import Foundation
@testable import StasisClientLib
import Testing

@Suite("HttpCredentials")
struct HttpCredentialsTests {
    @Test("supports extending/updating HTTP requests")
    func extendsRequests() {
        let url = URL(string: "http://localhost")!

        let plain = URLRequest(url: url)
        #expect(plain.value(forHTTPHeaderField: HttpCredentials.authorizationHeader) == nil)

        let none = plain.withCredentials(.none)
        #expect(none.value(forHTTPHeaderField: HttpCredentials.authorizationHeader) == nil)

        let basic = plain.withCredentials(.basic(username: "test-user", password: "test-password"))
        #expect(basic.value(forHTTPHeaderField: HttpCredentials.authorizationHeader)
            == "Basic dGVzdC11c2VyOnRlc3QtcGFzc3dvcmQ=")

        let bearer = plain.withCredentials(.oauth2BearerToken(token: "test-token"))
        #expect(bearer.value(forHTTPHeaderField: HttpCredentials.authorizationHeader) == "Bearer test-token")
    }
}
