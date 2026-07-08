import Contacts
import Foundation
@testable import StasisClient
import Testing

@Suite("ContactVCard")
struct ContactVCardTests {
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

    @Test("renders a contact as vCard data")
    func rendersVCard() throws {
        var record = base
        record.givenName = "test"
        record.familyName = "one"
        record.organizationName = "test co"
        record.jobTitle = "test"
        record.phoneNumbers = [ContactRecord.LabeledValue(label: nil, value: "111")]
        record.emailAddresses = [ContactRecord.LabeledValue(label: nil, value: "test@example.com")]

        let data = try ContactVCard.data(from: record)
        let text = try #require(String(bytes: data, encoding: .utf8))
        #expect(text.contains("BEGIN:VCARD"))
        #expect(text.contains("END:VCARD"))

        let parsed = try #require(try CNContactVCardSerialization.contacts(with: data).first)
        #expect(parsed.givenName == "test")
        #expect(parsed.familyName == "one")
        #expect(parsed.organizationName == "test co")
        #expect(parsed.jobTitle == "test")
        #expect(parsed.phoneNumbers.map { $0.value.stringValue } == ["111"])
        #expect(parsed.emailAddresses.map { String($0.value) } == ["test@example.com"])
    }
}
