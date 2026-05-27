import Foundation

enum InteropResources {
    static func load(domain: String, resource: String) -> Data {
        let url = baseDirectory
            .appendingPathComponent(domain)
            .appendingPathComponent("\(resource).json")
        do {
            return try Data(contentsOf: url)
        } catch {
            fatalError("Interop resource not found: [\(url.path)]")
        }
    }

    static func tree(_ data: Data) -> NSDictionary {
        do {
            guard let dict = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                fatalError("Interop resource is not a JSON object")
            }
            guard let normalized = normalize(dict) as? NSDictionary else {
                fatalError("Failed to normalize interop resource as a dictionary")
            }
            return normalized
        } catch {
            fatalError("Failed to parse interop resource: \(error)")
        }
    }

    private static func normalize(_ value: Any) -> Any {
        switch value {
        case let dict as [String: Any]:
            return dict.reduce(into: [String: Any]()) { result, pair in
                guard !ignoredKeys.contains(pair.key), !(pair.value is NSNull) else { return }
                result[pair.key] = normalize(pair.value)
            }
        case let array as [Any]:
            return array.map(normalize)
        case let string as String:
            return uuidPattern.firstMatch(in: string, range: NSRange(string.startIndex..., in: string)) != nil
                ? string.lowercased()
                : string
        default:
            return value
        }
    }

    private static let ignoredKeys: Set<String> = [
        "next_invocation",
        "use_query_string",
        "context",
        "additional_config"
    ]

    private static let uuidPattern: NSRegularExpression = {
        do {
            return try NSRegularExpression(
                pattern: "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
            )
        } catch {
            fatalError("Invalid UUID regex: \(error)")
        }
    }()

    private static let baseDirectory: URL = {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Resources")
            .appendingPathComponent("interop")
    }()
}
