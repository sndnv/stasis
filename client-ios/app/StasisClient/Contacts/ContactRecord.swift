import Foundation

struct ContactRecord: Codable, Sendable, Equatable, Hashable {
    var contactType: ContactType
    var namePrefix: String
    var givenName: String
    var phoneticGivenName: String
    var middleName: String
    var phoneticMiddleName: String
    var familyName: String
    var phoneticFamilyName: String
    var nameSuffix: String
    var nickname: String
    var organizationName: String
    var phoneticOrganizationName: String
    var departmentName: String
    var jobTitle: String
    var phoneNumbers: [LabeledValue]
    var emailAddresses: [LabeledValue]
    var urlAddresses: [LabeledValue]
    var postalAddresses: [PostalAddress]
    var instantMessageAddresses: [InstantMessage]
    var socialProfiles: [SocialProfile]
    var relations: [LabeledValue]
    var dates: [LabeledDate]
    var birthday: DateValue?
    var imageData: Data?

    func canonical() -> ContactRecord {
        var copy = self
        copy.phoneNumbers.sort { $0.sortKey < $1.sortKey }
        copy.emailAddresses.sort { $0.sortKey < $1.sortKey }
        copy.urlAddresses.sort { $0.sortKey < $1.sortKey }
        copy.postalAddresses.sort { $0.sortKey < $1.sortKey }
        copy.instantMessageAddresses.sort { $0.sortKey < $1.sortKey }
        copy.socialProfiles.sort { $0.sortKey < $1.sortKey }
        copy.relations.sort { $0.sortKey < $1.sortKey }
        copy.dates.sort { $0.sortKey < $1.sortKey }
        return copy
    }

    enum ContactType: String, Codable, Sendable, Hashable {
        case person
        case organization
    }

    struct LabeledValue: Codable, Sendable, Equatable, Hashable {
        var label: String?
        var value: String

        var sortKey: String { "\(value)\u{1F}\(label ?? "")" }
    }

    struct PostalAddress: Codable, Sendable, Equatable, Hashable {
        var label: String?
        var street: String
        var subLocality: String
        var city: String
        var subAdministrativeArea: String
        var state: String
        var postalCode: String
        var country: String

        var sortKey: String {
            [street, subLocality, city, subAdministrativeArea, state, postalCode, country, label ?? ""]
                .joined(separator: "\u{1F}")
        }
    }

    struct InstantMessage: Codable, Sendable, Equatable, Hashable {
        var label: String?
        var service: String
        var username: String

        var sortKey: String { "\(service)\u{1F}\(username)\u{1F}\(label ?? "")" }
    }

    struct SocialProfile: Codable, Sendable, Equatable, Hashable {
        var label: String?
        var service: String
        var username: String
        var userIdentifier: String
        var urlString: String

        var sortKey: String {
            [service, username, userIdentifier, urlString, label ?? ""].joined(separator: "\u{1F}")
        }
    }

    struct LabeledDate: Codable, Sendable, Equatable, Hashable {
        var label: String?
        var date: DateValue

        var sortKey: String { "\(date.sortKey)\u{1F}\(label ?? "")" }
    }

    struct DateValue: Codable, Sendable, Equatable, Hashable {
        var year: Int?
        var month: Int?
        var day: Int?

        var sortKey: String { "\(year ?? -1)-\(month ?? -1)-\(day ?? -1)" }
    }
}
