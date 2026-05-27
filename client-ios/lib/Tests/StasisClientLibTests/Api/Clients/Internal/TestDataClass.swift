import Foundation
@testable import StasisClientLib

struct TestDataClass: Sendable, Equatable, Codable {
    let int: Int
    let bool: Bool
    let string: String
}
