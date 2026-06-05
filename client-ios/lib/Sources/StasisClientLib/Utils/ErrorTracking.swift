import Foundation

public extension Error {
    var tracked: String {
        "\(type(of: self)) - \(localizedDescription)"
    }
}
