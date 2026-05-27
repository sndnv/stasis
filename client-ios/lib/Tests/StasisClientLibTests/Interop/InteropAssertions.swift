import Foundation
@testable import StasisClientLib
import Testing

func assert<T: Codable & Equatable>(
    domain: String,
    resource: String,
    matches: T,
    sourceLocation: SourceLocation = #_sourceLocation
) throws {
    let data = InteropResources.load(domain: domain, resource: resource)

    let decoded = try JSONCoders.decoder().decode(T.self, from: data)
    #expect(decoded == matches, sourceLocation: sourceLocation)

    let encoded = try JSONCoders.encoder().encode(matches)
    #expect(InteropResources.tree(encoded) == InteropResources.tree(data), sourceLocation: sourceLocation)
}
