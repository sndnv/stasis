import Foundation
import Photos

struct PhotoLibraryPermission: LibrarySourcePermission {
    func status() -> LibraryPermissionStatus {
        Self.map(PHPhotoLibrary.authorizationStatus(for: .readWrite))
    }

    func request() async -> Bool {
        Self.map(await PHPhotoLibrary.requestAuthorization(for: .readWrite)) == .granted
    }

    private static func map(_ status: PHAuthorizationStatus) -> LibraryPermissionStatus {
        switch status {
        case .authorized, .limited: .granted
        case .denied, .restricted: .denied
        case .notDetermined: .undetermined
        @unknown default: .denied
        }
    }
}
