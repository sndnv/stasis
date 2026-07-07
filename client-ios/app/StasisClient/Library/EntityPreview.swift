import Foundation

struct EntityPreview: Equatable, Sendable {
    var sections: [Section]

    struct Section: Equatable, Sendable {
        var title: String
        var fields: [Field]
    }

    struct Field: Equatable, Sendable {
        var label: String
        var value: String
    }
}
