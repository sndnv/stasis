import Testing
@testable import StasisClientLib

@Test func libraryExposesVersion() {
    #expect(StasisClientLib.version == "0.0.0")
}
