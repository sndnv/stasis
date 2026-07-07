import Contacts
import Foundation

enum ContactVCard {
    static func data(from record: ContactRecord) throws -> Data {
        try CNContactVCardSerialization.data(with: [DeviceContactStore.makeContact(from: record)])
    }
}
