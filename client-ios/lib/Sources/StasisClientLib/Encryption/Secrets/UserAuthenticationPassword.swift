import CryptoKit
import Foundation

public enum UserAuthenticationPassword: Secret, Equatable {
    case hashed(user: UserId, hashedPassword: Data, extractionGuard: ExtractionGuard = ExtractionGuard())
    case unhashed(user: UserId, rawPassword: Data, extractionGuard: ExtractionGuard = ExtractionGuard())

    public var user: UserId {
        switch self {
        case let .hashed(user, _, _): return user
        case let .unhashed(user, _, _): return user
        }
    }

    public func extract() throws -> String {
        switch self {
        case let .hashed(_, hashedPassword, extractionGuard):
            try extractionGuard.consume()
            return hashedPassword.base64UrlEncodedString()
        case let .unhashed(_, rawPassword, extractionGuard):
            try extractionGuard.consume()
            guard let decoded = String(bytes: rawPassword, encoding: .utf8) else {
                throw SecretError.invalidArgument("raw password is not valid UTF-8")
            }
            return decoded
        }
    }

    public func digested() -> String {
        switch self {
        case let .hashed(_, hashedPassword, _):
            return Data(SHA512.hash(data: hashedPassword)).base64UrlEncodedString()
        case let .unhashed(_, rawPassword, _):
            return Data(SHA512.hash(data: rawPassword)).base64UrlEncodedString()
        }
    }

    public static func == (lhs: UserAuthenticationPassword, rhs: UserAuthenticationPassword) -> Bool {
        switch (lhs, rhs) {
        case let (.hashed(lUser, lPwd, _), .hashed(rUser, rPwd, _)):
            return lUser == rUser && lPwd == rPwd
        case let (.unhashed(lUser, lPwd, _), .unhashed(rUser, rPwd, _)):
            return lUser == rUser && lPwd == rPwd
        default:
            return false
        }
    }
}

public final class ExtractionGuard: @unchecked Sendable {
    private let lock = NSLock()
    private var consumed = false

    public init() {}

    func consume() throws {
        lock.lock()
        defer { lock.unlock() }
        if consumed {
            throw SecretError.passwordAlreadyExtracted
        }
        consumed = true
    }
}
