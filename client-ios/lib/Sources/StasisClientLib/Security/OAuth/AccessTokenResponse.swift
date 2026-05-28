import Foundation

public struct AccessTokenResponse: Sendable, Equatable, Codable {
    public let accessToken: String
    public let refreshToken: String?
    public let expiresIn: Int64
    public let scope: String?

    public init(accessToken: String, refreshToken: String?, expiresIn: Int64, scope: String?) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.expiresIn = expiresIn
        self.scope = scope
    }

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case expiresIn = "expires_in"
        case scope
    }

    public var claims: Result<Claims, Error> {
        Result { try Self.decodeJwtPayload(accessToken) }
            .map { Claims(claims: Self.flatten($0)) }
    }

    public var hasNotExpired: Bool {
        guard let payload = try? Self.decodeJwtPayload(accessToken),
              let exp = (payload["exp"] as? NSNumber)?.doubleValue
        else { return false }
        return Date(timeIntervalSince1970: exp) > Date()
    }

    public struct Claims: Sendable, Equatable {
        public let claims: [String: String]
        public var subject: String? { claims["sub"] }

        public init(claims: [String: String]) {
            self.claims = claims
        }
    }

    static func decodeJwtPayload(_ jwt: String) throws -> [String: Any] {
        let parts = jwt.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3 else {
            throw JwtDecodingError.malformedJwt
        }
        let payloadSegment = String(parts[1])
        guard let payloadData = Data(base64UrlEncoded: payloadSegment) else {
            throw JwtDecodingError.malformedPayloadEncoding
        }
        guard let json = try JSONSerialization.jsonObject(with: payloadData) as? [String: Any] else {
            throw JwtDecodingError.malformedPayloadJson
        }
        return json
    }

    static func flatten(_ payload: [String: Any]) -> [String: String] {
        var out: [String: String] = [:]
        for (key, value) in payload {
            out[key] = asString(value)
        }
        return out
    }

    private static func asString(_ value: Any) -> String {
        if let arr = value as? [Any] {
            return arr.map { asString($0) }.joined(separator: ", ")
        }
        if let str = value as? String {
            return str
        }
        if let num = value as? NSNumber {
            return num.stringValue
        }
        return String(describing: value)
    }
}

public enum JwtDecodingError: Error, Equatable {
    case malformedJwt
    case malformedPayloadEncoding
    case malformedPayloadJson
}
