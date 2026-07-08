import Foundation
@testable import StasisClient
import Testing

@Suite struct CallStackTreeParserTests {
    @Test func parsesFramesDepthFirst() {
        let json = Data("""
        {"callStacks":[{"callStackRootFrames":[
          {"binaryName":"test","offsetIntoBinaryTextSegment":100,"subFrames":[
             {"binaryName":"test a","offsetIntoBinaryTextSegment":200}
          ]}
        ]}]}
        """.utf8)

        #expect(CallStackTreeParser.topFrames(fromJSON: json, limit: 10) == ["test +100", "test a +200"])
    }

    @Test func respectsTheLimit() {
        let json = Data("""
        {"callStacks":[{"callStackRootFrames":[
          {"binaryName":"test","offsetIntoBinaryTextSegment":1,"subFrames":[
             {"binaryName":"test","offsetIntoBinaryTextSegment":2,"subFrames":[
                {"binaryName":"test","offsetIntoBinaryTextSegment":3}
             ]}
          ]}
        ]}]}
        """.utf8)

        #expect(CallStackTreeParser.topFrames(fromJSON: json, limit: 2) == ["test +1", "test +2"])
    }

    @Test func usesPlaceholderForMissingBinaryName() {
        let json = Data("""
        {"callStacks":[{"callStackRootFrames":[{"offsetIntoBinaryTextSegment":5}]}]}
        """.utf8)

        #expect(CallStackTreeParser.topFrames(fromJSON: json, limit: 10) == ["? +5"])
    }

    @Test func returnsEmptyForGarbage() {
        #expect(CallStackTreeParser.topFrames(fromJSON: Data("not json".utf8), limit: 10) == [])
    }
}
