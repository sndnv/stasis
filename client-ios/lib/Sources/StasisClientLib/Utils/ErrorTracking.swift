import Foundation

extension Error {
    var tracked: String {
        "\(type(of: self)) - \(localizedDescription)"
    }
}
