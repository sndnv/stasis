import Foundation

struct Toast: Equatable, Identifiable {
    let id: UUID
    let message: String

    init(message: String) {
        id = UUID()
        self.message = message
    }
}
