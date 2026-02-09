import Foundation
import Network

final class NetworkMonitor {
    static let shared = NetworkMonitor()

    private let monitor: NWPathMonitor
    private let queue = DispatchQueue(label: "doompedia.network.monitor")

    private init() {
        monitor = NWPathMonitor()
        monitor.start(queue: queue)
    }

    var isOnline: Bool {
        monitor.currentPath.status == .satisfied
    }
}
