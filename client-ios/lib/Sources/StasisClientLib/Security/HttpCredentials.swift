import Foundation

public enum HttpCredentials: Sendable, Equatable, Hashable {
    case none
    case basic(username: String, password: String)
    case oauth2BearerToken(token: String)

    public static let authorizationHeader: String = "Authorization"
}

extension URLRequest {
    public mutating func setCredentials(_ credentials: HttpCredentials) {
        switch credentials {
        case .none:
            return
        case let .basic(username, password):
            let raw = "\(username):\(password)"
            guard let encoded = raw.data(using: .utf8)?.base64EncodedString() else { return }
            setValue("Basic \(encoded)", forHTTPHeaderField: HttpCredentials.authorizationHeader)
        case let .oauth2BearerToken(token):
            setValue("Bearer \(token)", forHTTPHeaderField: HttpCredentials.authorizationHeader)
        }
    }

    public func withCredentials(_ credentials: HttpCredentials) -> URLRequest {
        var copy = self
        copy.setCredentials(credentials)
        return copy
    }
}
