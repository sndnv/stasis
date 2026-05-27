@testable import StasisClientLib
import Testing

@Suite("Either")
struct EitherTests {
    @Test("supports mapping")
    func supportsMapping() {
        #expect(Either<String, String>.right("test").map { $0.uppercased() } == .right("TEST"))
        #expect(Either<String, String>.left("test").map { $0.uppercased() } == .left("test"))
    }

    @Test("supports flat-mapping")
    func supportsFlatMapping() {
        let right = Either<String, String>.right("test")
        let left = Either<String, String>.left("test")
        #expect(right.flatMap { .right($0.uppercased()) } == .right("TEST"))
        #expect(right.flatMap { Either<String, String>.left($0.uppercased()) } == .left("TEST"))
        #expect(left.flatMap { .right($0.uppercased()) } == .left("test"))
        #expect(left.flatMap { Either<String, String>.left($0.uppercased()) } == .left("test"))
    }

    @Test("supports folding")
    func supportsFolding() {
        let right = Either<String, String>.right("tESt")
        let left = Either<String, String>.left("tESt")
        #expect(right.fold(left: { $0.lowercased() }, right: { $0.uppercased() }) == "TEST")
        #expect(left.fold(left: { $0.lowercased() }, right: { $0.uppercased() }) == "test")
    }

    @Test("supports retrieving 'left' on a 'Left'")
    func supportsRetrievingLeftOnLeft() {
        let either = Either<String, String>.left("test")
        #expect(either.isLeft)
        #expect(!either.isRight)
        #expect(either.leftValue == "test")
    }

    @Test("returns nil when retrieving 'right' on a 'Left'")
    func nilRightOnLeft() {
        #expect(Either<String, String>.left("test").rightValue == nil)
    }

    @Test("supports retrieving 'right' on a 'Right'")
    func supportsRetrievingRightOnRight() {
        let either = Either<String, String>.right("test")
        #expect(!either.isLeft)
        #expect(either.isRight)
        #expect(either.rightValue == "test")
    }

    @Test("returns nil when retrieving 'left' on a 'Right'")
    func nilLeftOnRight() {
        #expect(Either<String, String>.right("test").leftValue == nil)
    }
}
