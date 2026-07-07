import Contacts
import CryptoKit
import Foundation

struct ContactsSource: LibraryRecordSource {
    static let scheme = "contacts"

    private let store: any ContactStore

    init(store: any ContactStore) {
        self.store = store
    }

    var scheme: String { Self.scheme }

    func hasReadAccess() -> Bool {
        store.hasReadAccess()
    }

    func hasWriteAccess() -> Bool {
        store.hasWriteAccess()
    }

    func list() async throws -> [ContactRecord] {
        try await store.list().map(\.record)
    }

    func restore(_ record: ContactRecord) async throws {
        let key = id(record)
        if let match = try await store.list().first(where: { id($0.record) == key }) {
            try await store.update(identifier: match.identifier, with: record)
        } else {
            try await store.insert(record)
        }
    }

    func id(_ record: ContactRecord) -> String {
        let phones = record.phoneNumbers.map(\.value).sorted()
        let emails = record.emailAddresses.map(\.value).sorted()
        let identity = ([Self.displayName(record)] + phones + emails).joined(separator: "\n")
        return SHA256.hash(data: Data(identity.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    func displayName(_ record: ContactRecord) -> String {
        Self.displayName(record)
    }

    func attributes(_ record: ContactRecord) -> [String: String] {
        ["name": Self.displayName(record)]
    }

    func describe(_ record: ContactRecord) -> EntityPreview {
        var sections: [EntityPreview.Section] = []
        sections.append(EntityPreview.Section(title: "Name", fields: Self.nameFields(record)))
        Self.appendLabeled(&sections, title: "Phone", record.phoneNumbers, fallback: "phone")
        Self.appendLabeled(&sections, title: "Email", record.emailAddresses, fallback: "email")
        Self.appendLabeled(&sections, title: "URL", record.urlAddresses, fallback: "url")
        Self.appendAddresses(&sections, record.postalAddresses)
        Self.appendDates(&sections, record)
        return EntityPreview(sections: sections.filter { !$0.fields.isEmpty })
    }

    func export(_ record: ContactRecord) throws -> ExportedContent {
        ExportedContent(fileExtension: "vcf", mimeType: "text/vcard", bytes: try ContactVCard.data(from: record))
    }

    private static func nameFields(_ record: ContactRecord) -> [EntityPreview.Field] {
        var fields: [EntityPreview.Field] = []
        fields.append(EntityPreview.Field(label: "Name", value: displayName(record)))
        appendIfPresent(&fields, "Nickname", record.nickname)
        appendIfPresent(&fields, "Organization", record.organizationName)
        appendIfPresent(&fields, "Job Title", record.jobTitle)
        appendIfPresent(&fields, "Department", record.departmentName)
        return fields.filter { !$0.value.isEmpty }
    }

    private static func appendLabeled(
        _ sections: inout [EntityPreview.Section],
        title: String,
        _ values: [ContactRecord.LabeledValue],
        fallback: String
    ) {
        let fields = values.map { EntityPreview.Field(label: label($0.label, fallback), value: $0.value) }
        sections.append(EntityPreview.Section(title: title, fields: fields))
    }

    private static func appendAddresses(
        _ sections: inout [EntityPreview.Section],
        _ addresses: [ContactRecord.PostalAddress]
    ) {
        let fields = addresses.map { address in
            let value = [address.street, address.city, address.state, address.postalCode, address.country]
                .filter { !$0.isEmpty }
                .joined(separator: ", ")
            return EntityPreview.Field(label: label(address.label, "address"), value: value)
        }
        sections.append(EntityPreview.Section(title: "Address", fields: fields))
    }

    private static func appendDates(_ sections: inout [EntityPreview.Section], _ record: ContactRecord) {
        var fields: [EntityPreview.Field] = []
        if let birthday = record.birthday {
            fields.append(EntityPreview.Field(label: "Birthday", value: format(birthday)))
        }
        for date in record.dates {
            fields.append(EntityPreview.Field(label: label(date.label, "date"), value: format(date.date)))
        }
        sections.append(EntityPreview.Section(title: "Dates", fields: fields))
    }

    private static func format(_ date: ContactRecord.DateValue) -> String {
        [date.year, date.month, date.day]
            .map { $0.map(String.init) ?? "-" }
            .joined(separator: "-")
    }

    private static func label(_ raw: String?, _ fallback: String) -> String {
        guard let raw, !raw.isEmpty else { return fallback }
        return CNLabeledValue<NSString>.localizedString(forLabel: raw)
    }

    private static func appendIfPresent(_ fields: inout [EntityPreview.Field], _ label: String, _ value: String) {
        if !value.isEmpty { fields.append(EntityPreview.Field(label: label, value: value)) }
    }

    private static func displayName(_ record: ContactRecord) -> String {
        let name = "\(record.givenName) \(record.familyName)".trimmingCharacters(in: .whitespaces)
        if !name.isEmpty { return name }
        if !record.organizationName.isEmpty { return record.organizationName }
        if !record.nickname.isEmpty { return record.nickname }
        return record.emailAddresses.first?.value ?? record.phoneNumbers.first?.value ?? ""
    }
}
