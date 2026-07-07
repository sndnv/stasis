import Foundation
@testable import StasisClient
import Testing

@Suite("ContactRecord")
struct ContactRecordTests {
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

    private func sortedEncode(_ record: ContactRecord) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        return try encoder.encode(record)
    }

    @Test("round-trips through Codable")
    func codableRoundTrip() throws {
        var record = base
        record.givenName = "test"
        record.familyName = "one"
        record.organizationName = "test a"
        record.phoneNumbers = [ContactRecord.LabeledValue(label: "home", value: "111")]
        record.emailAddresses = [ContactRecord.LabeledValue(label: nil, value: "test@example.com")]
        record.postalAddresses = [
            ContactRecord.PostalAddress(
                label: "home",
                street: "1 test",
                subLocality: "",
                city: "test city",
                subAdministrativeArea: "",
                state: "ts",
                postalCode: "0000",
                country: "test land"
            )
        ]
        record.birthday = ContactRecord.DateValue(year: 1990, month: 1, day: 2)
        record.imageData = Data("test image".utf8)

        let decoded = try JSONDecoder().decode(ContactRecord.self, from: try sortedEncode(record))
        #expect(decoded == record)
    }

    @Test("canonical sorts multi-value arrays regardless of input order")
    func canonicalSortsArrays() {
        var forward = base
        forward.phoneNumbers = [
            ContactRecord.LabeledValue(label: nil, value: "111"),
            ContactRecord.LabeledValue(label: nil, value: "222")
        ]
        forward.emailAddresses = [
            ContactRecord.LabeledValue(label: nil, value: "a@example.com"),
            ContactRecord.LabeledValue(label: nil, value: "b@example.com")
        ]

        var reversed = base
        reversed.phoneNumbers = Array(forward.phoneNumbers.reversed())
        reversed.emailAddresses = Array(forward.emailAddresses.reversed())

        #expect(forward.canonical() == reversed.canonical())
    }

    @Test("canonical records with equal data encode to identical bytes")
    func canonicalEncodingIsDeterministic() throws {
        var forward = base
        forward.givenName = "test"
        forward.phoneNumbers = [
            ContactRecord.LabeledValue(label: "work", value: "222"),
            ContactRecord.LabeledValue(label: "home", value: "111")
        ]

        var reversed = base
        reversed.givenName = "test"
        reversed.phoneNumbers = Array(forward.phoneNumbers.reversed())

        #expect(try sortedEncode(forward.canonical()) == (try sortedEncode(reversed.canonical())))
    }
}
