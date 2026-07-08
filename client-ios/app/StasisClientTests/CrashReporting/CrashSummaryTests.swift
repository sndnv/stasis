import Foundation
@testable import StasisClient
import Testing

@Suite struct CrashSummaryTests {
    @Test func formatsAllFieldsAndFrames() {
        let summary = CrashSummary(
            exceptionType: 10,
            exceptionCode: 0,
            signal: 11,
            terminationReason: "Namespace SIGNAL, Code 0xb",
            topFrames: ["test +10", "test a +20"]
        )

        let message = summary.message()

        #expect(message.contains("exception 10"))
        #expect(message.contains("signal 11"))
        #expect(message.contains("code 0"))
        #expect(message.contains("Namespace SIGNAL, Code 0xb"))
        #expect(message.contains("test +10"))
        #expect(message.contains("test a +20"))
    }

    @Test func fallsBackWhenEmpty() {
        let summary = CrashSummary(
            exceptionType: nil,
            exceptionCode: nil,
            signal: nil,
            terminationReason: nil,
            topFrames: []
        )

        #expect(summary.message() == "Unrecoverable error")
    }

    @Test func anonymizesPaths() {
        let summary = CrashSummary(
            exceptionType: nil,
            exceptionCode: nil,
            signal: nil,
            terminationReason: "crash at /Users/test/secret/file.txt",
            topFrames: []
        )

        let message = summary.message()

        #expect(!message.contains("/Users/test/secret/file.txt"))
        #expect(message.contains("CONTENT_REMOVED"))
    }
}
