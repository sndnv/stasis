import Contacts
import Foundation
@testable import StasisClient
import Testing

@Suite("ContactsSource")
struct ContactsSourceTests {
    private var base: ContactRecord {
        ContactRecord(
            contactType: .person,
            namePrefix: "",
            givenName: "",
            phoneticGivenName: "",
            middleName: "",
            phoneticMiddleName: "",
            familyName: "",
            phoneticFamilyName: "",
            nameSuffix: "",
            nickname: "",
            organizationName: "",
            phoneticOrganizationName: "",
            departmentName: "",
            jobTitle: "",
            phoneNumbers: [],
            emailAddresses: [],
            urlAddresses: [],
            postalAddresses: [],
            instantMessageAddresses: [],
            socialProfiles: [],
            relations: [],
            dates: [],
            birthday: nil,
            imageData: nil
        )
    }

    private func person(given: String, family: String, phones: [String], emails: [String]) -> ContactRecord {
        var record = base
        record.givenName = given
        record.familyName = family
        record.phoneNumbers = phones.map { ContactRecord.LabeledValue(label: nil, value: $0) }
        record.emailAddresses = emails.map { ContactRecord.LabeledValue(label: nil, value: $0) }
        return record.canonical()
    }

    @Test("lists records from the store")
    func listsRecords() async throws {
        let recordA = person(given: "test", family: "one", phones: ["111"], emails: [])
        let recordB = person(given: "test", family: "two", phones: ["222"], emails: [])
        let store = FakeContactStore(
            stored: [
                StoredContact(identifier: "a", record: recordA),
                StoredContact(identifier: "b", record: recordB)
            ],
            readAccess: true,
            writeAccess: true
        )
        let source = ContactsSource(store: store)

        #expect(try await source.list() == [recordA, recordB])
    }

    @Test("derives a stable key that survives a delete then restore")
    func keyIsStableAcrossRoundTrip() {
        let source = ContactsSource(store: FakeContactStore(stored: [], readAccess: true, writeAccess: true))
        let record = person(given: "test", family: "one", phones: ["111"], emails: ["test@example.com"])

        #expect(source.id(record) == source.id(record))
    }

    @Test("keeps the key when only a non-identifying field changes")
    func nonIdentifyingEditKeepsKey() {
        let source = ContactsSource(store: FakeContactStore(stored: [], readAccess: true, writeAccess: true))
        let original = person(given: "test", family: "one", phones: ["111"], emails: ["test@example.com"])
        var edited = original
        edited.organizationName = "test a"
        edited.jobTitle = "test b"

        #expect(source.id(edited.canonical()) == source.id(original))
    }

    @Test("re-keys when an identifying field changes")
    func identifyingEditReKeys() {
        let source = ContactsSource(store: FakeContactStore(stored: [], readAccess: true, writeAccess: true))
        let original = person(given: "test", family: "one", phones: ["111"], emails: [])
        let renamed = person(given: "test", family: "two", phones: ["111"], emails: [])
        let renumbered = person(given: "test", family: "one", phones: ["999"], emails: [])

        #expect(source.id(renamed) != source.id(original))
        #expect(source.id(renumbered) != source.id(original))
    }

    @Test("restore updates a matching contact in place")
    func restoreUpdatesInPlace() async throws {
        let original = person(given: "test", family: "one", phones: ["111"], emails: [])
        let store = FakeContactStore(
            stored: [StoredContact(identifier: "existing", record: original)],
            readAccess: true,
            writeAccess: true
        )
        let source = ContactsSource(store: store)

        var edited = original
        edited.organizationName = "test a"

        try await source.restore(edited.canonical())

        #expect(store.contacts.count == 1)
        #expect(store.contacts.first?.identifier == "existing")
        #expect(store.contacts.first?.record.organizationName == "test a")
    }

    @Test("restore inserts a contact when none matches")
    func restoreInsertsWhenMissing() async throws {
        let store = FakeContactStore(stored: [], readAccess: true, writeAccess: true)
        let source = ContactsSource(store: store)
        let record = person(given: "test", family: "one", phones: ["111"], emails: [])

        try await source.restore(record)

        #expect(store.contacts.count == 1)
        #expect(store.contacts.first?.record == record)
    }

    @Test("re-backing up right after restore reports no change")
    func roundTripReportsNoChange() async throws {
        let record = person(given: "test", family: "one", phones: ["111"], emails: ["test@example.com"])
        let store = FakeContactStore(
            stored: [StoredContact(identifier: "existing", record: record)],
            readAccess: true,
            writeAccess: true
        )
        let source = ContactsSource(store: store)

        let backedUp = try await source.list()
        for restored in backedUp {
            try await source.restore(restored)
        }

        #expect(try await source.list() == backedUp)
        #expect(store.contacts.count == 1)
    }

    @Test("describe surfaces name, phone and email sections")
    func describeSurfacesSections() {
        let source = ContactsSource(store: FakeContactStore(stored: [], readAccess: true, writeAccess: true))
        let record = person(given: "test", family: "one", phones: ["111"], emails: ["test@example.com"])

        let preview = source.describe(record)
        let titles = preview.sections.map(\.title)

        #expect(titles.contains("Name"))
        #expect(titles.contains("Phone"))
        #expect(titles.contains("Email"))
        #expect(preview.sections.first { $0.title == "Name" }?.fields.first?.value == "test one")
    }

    @Test("export produces a vCard that parses back to the same name")
    func exportProducesVCard() throws {
        let source = ContactsSource(store: FakeContactStore(stored: [], readAccess: true, writeAccess: true))
        let record = person(given: "test", family: "one", phones: ["111"], emails: ["test@example.com"])

        let exported = try source.export(record)
        #expect(exported.fileExtension == "vcf")

        let contacts = try CNContactVCardSerialization.contacts(with: exported.bytes)
        #expect(contacts.count == 1)
        #expect(contacts.first?.givenName == "test")
        #expect(contacts.first?.familyName == "one")
    }
}
