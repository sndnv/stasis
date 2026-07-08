import Foundation

enum CallStackTreeParser {
    static func topFrames(fromJSON data: Data, limit: Int) -> [String] {
        guard limit > 0, let tree = try? JSONDecoder().decode(CallStackTree.self, from: data) else {
            return []
        }
        var frames: [String] = []
        for stack in tree.callStacks ?? [] {
            for root in stack.callStackRootFrames ?? [] {
                append(root, into: &frames, limit: limit)
                if frames.count >= limit { return frames }
            }
        }
        return frames
    }

    private static func append(_ frame: Frame, into frames: inout [String], limit: Int) {
        guard frames.count < limit else { return }
        frames.append(format(frame))
        for sub in frame.subFrames ?? [] {
            if frames.count >= limit { return }
            append(sub, into: &frames, limit: limit)
        }
    }

    private static func format(_ frame: Frame) -> String {
        let name = frame.binaryName ?? "?"
        if let offset = frame.offsetIntoBinaryTextSegment {
            return "\(name) +\(offset)"
        }
        return name
    }

    private struct CallStackTree: Decodable {
        let callStacks: [CallStack]?
    }

    private struct CallStack: Decodable {
        let callStackRootFrames: [Frame]?
    }

    private struct Frame: Decodable {
        let binaryName: String?
        let offsetIntoBinaryTextSegment: Int?
        let subFrames: [Frame]?
    }
}
