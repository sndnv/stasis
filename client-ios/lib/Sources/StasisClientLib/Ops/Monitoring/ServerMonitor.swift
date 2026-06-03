import Foundation

public protocol ServerMonitor: Sendable {
    func stop() async
}
