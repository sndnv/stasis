import Foundation

struct StoredContact: Sendable, Equatable, Hashable {
    let identifier: String
    let record: ContactRecord
}
