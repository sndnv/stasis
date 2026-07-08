import Foundation
import StasisClientLib

enum EntryDisplay {
    static func attributes(_ metadata: EntityMetadata?) -> [String: String] {
        guard case .library(let library) = metadata,
              let decoded = try? JSONDecoder().decode([String: String].self, from: library.attributes)
        else { return [:] }
        return decoded
    }

    static func displayName(path: String, metadata: EntityMetadata) -> String {
        if let name = attributes(metadata)["name"], !name.isEmpty {
            return name
        }
        let component = (path as NSString).lastPathComponent
        return component.isEmpty ? path : component
    }

    static func secondary(path: String, metadata: EntityMetadata) -> String {
        switch SourceUri.scheme(path) {
        case CalendarSource.scheme?:
            if let name = attributes(metadata)["calendar"], !name.isEmpty {
                return "Calendar · \(name)"
            }
            return "Calendar event"
        case ContactsSource.scheme?:
            return "Contact"
        case .some(let scheme):
            return scheme
        case .none:
            return parentPath(path)
        }
    }

    static func kindLabel(path: String, metadata: EntityMetadata) -> String {
        switch SourceUri.scheme(path) {
        case CalendarSource.scheme?:
            return "Calendar event"
        case ContactsSource.scheme?:
            return "Contact"
        case .some(let scheme):
            return LibrarySource.all.first { $0.scheme == scheme }?.displayName ?? scheme
        case .none:
            if case .directory = metadata { return "Directory" }
            return "File"
        }
    }

    static func kindIcon(path: String, metadata: EntityMetadata) -> String {
        switch SourceUri.scheme(path) {
        case CalendarSource.scheme?:
            return "calendar"
        case ContactsSource.scheme?:
            return "person.crop.circle"
        case .some(let scheme):
            return LibrarySource.all.first { $0.scheme == scheme }?.systemImage ?? "shippingbox"
        case .none:
            if case .directory = metadata { return "folder" }
            return "doc"
        }
    }

    private static func parentPath(_ path: String) -> String {
        let trimmed = path.hasSuffix("/") && path.count > 1 ? String(path.dropLast()) : path
        let parent = (trimmed as NSString).deletingLastPathComponent
        return parent == "/" ? "" : parent
    }
}
