import Contacts
import Foundation
import StasisClientLib

struct DeviceContactStore: ContactStore {
    func hasReadAccess() -> Bool {
        let status = CNContactStore.authorizationStatus(for: .contacts)
        return status == .authorized || status == .limited
    }

    func hasWriteAccess() -> Bool {
        hasReadAccess()
    }

    func list() async throws -> [StoredContact] {
        let store = CNContactStore()
        let request = CNContactFetchRequest(keysToFetch: Self.keys)
        var results: [StoredContact] = []
        try store.enumerateContacts(with: request) { contact, _ in
            results.append(StoredContact(identifier: contact.identifier, record: Self.record(from: contact)))
        }
        return results
    }

    func insert(_ record: ContactRecord) async throws {
        let contact = CNMutableContact()
        Self.apply(record, to: contact)
        let request = CNSaveRequest()
        request.add(contact, toContainerWithIdentifier: nil)
        try CNContactStore().execute(request)
    }

    func update(identifier: String, with record: ContactRecord) async throws {
        let store = CNContactStore()
        let existing = try store.unifiedContact(withIdentifier: identifier, keysToFetch: Self.keys)
        guard let mutable = existing.mutableCopy() as? CNMutableContact else {
            throw InvalidArgumentError("Unable to update contact [\(identifier)]")
        }
        Self.apply(record, to: mutable)
        let request = CNSaveRequest()
        request.update(mutable)
        try store.execute(request)
    }

    private static var keys: [CNKeyDescriptor] {
        ([
        CNContactTypeKey,
        CNContactNamePrefixKey,
        CNContactGivenNameKey,
        CNContactPhoneticGivenNameKey,
        CNContactMiddleNameKey,
        CNContactPhoneticMiddleNameKey,
        CNContactFamilyNameKey,
        CNContactPhoneticFamilyNameKey,
        CNContactNameSuffixKey,
        CNContactNicknameKey,
        CNContactOrganizationNameKey,
        CNContactPhoneticOrganizationNameKey,
        CNContactDepartmentNameKey,
        CNContactJobTitleKey,
        CNContactPhoneNumbersKey,
        CNContactEmailAddressesKey,
        CNContactUrlAddressesKey,
        CNContactPostalAddressesKey,
        CNContactInstantMessageAddressesKey,
        CNContactSocialProfilesKey,
        CNContactRelationsKey,
        CNContactDatesKey,
        CNContactBirthdayKey,
        CNContactImageDataKey
        ] as [String]).map { $0 as CNKeyDescriptor }
    }

    static func makeContact(from record: ContactRecord) -> CNMutableContact {
        let contact = CNMutableContact()
        apply(record, to: contact)
        return contact
    }

    private static func record(from contact: CNContact) -> ContactRecord {
        ContactRecord(
            contactType: contact.contactType == .organization ? .organization : .person,
            namePrefix: contact.namePrefix,
            givenName: contact.givenName,
            phoneticGivenName: contact.phoneticGivenName,
            middleName: contact.middleName,
            phoneticMiddleName: contact.phoneticMiddleName,
            familyName: contact.familyName,
            phoneticFamilyName: contact.phoneticFamilyName,
            nameSuffix: contact.nameSuffix,
            nickname: contact.nickname,
            organizationName: contact.organizationName,
            phoneticOrganizationName: contact.phoneticOrganizationName,
            departmentName: contact.departmentName,
            jobTitle: contact.jobTitle,
            phoneNumbers: contact.phoneNumbers.map {
                ContactRecord.LabeledValue(label: $0.label, value: $0.value.stringValue)
            },
            emailAddresses: contact.emailAddresses.map {
                ContactRecord.LabeledValue(label: $0.label, value: $0.value as String)
            },
            urlAddresses: contact.urlAddresses.map {
                ContactRecord.LabeledValue(label: $0.label, value: $0.value as String)
            },
            postalAddresses: contact.postalAddresses.map { Self.postalAddress(from: $0) },
            instantMessageAddresses: contact.instantMessageAddresses.map { Self.instantMessage(from: $0) },
            socialProfiles: contact.socialProfiles.map { Self.socialProfile(from: $0) },
            relations: contact.contactRelations.map {
                ContactRecord.LabeledValue(label: $0.label, value: $0.value.name)
            },
            dates: contact.dates.map {
                ContactRecord.LabeledDate(label: $0.label, date: Self.dateValue($0.value as DateComponents))
            },
            birthday: contact.birthday.map { Self.dateValue($0) },
            imageData: contact.imageData
        ).canonical()
    }

    private static func apply(_ record: ContactRecord, to contact: CNMutableContact) {
        contact.contactType = record.contactType == .organization ? .organization : .person
        contact.namePrefix = record.namePrefix
        contact.givenName = record.givenName
        contact.phoneticGivenName = record.phoneticGivenName
        contact.middleName = record.middleName
        contact.phoneticMiddleName = record.phoneticMiddleName
        contact.familyName = record.familyName
        contact.phoneticFamilyName = record.phoneticFamilyName
        contact.nameSuffix = record.nameSuffix
        contact.nickname = record.nickname
        contact.organizationName = record.organizationName
        contact.phoneticOrganizationName = record.phoneticOrganizationName
        contact.departmentName = record.departmentName
        contact.jobTitle = record.jobTitle
        contact.phoneNumbers = record.phoneNumbers.map {
            CNLabeledValue(label: $0.label, value: CNPhoneNumber(stringValue: $0.value))
        }
        contact.emailAddresses = record.emailAddresses.map {
            CNLabeledValue(label: $0.label, value: $0.value as NSString)
        }
        contact.urlAddresses = record.urlAddresses.map {
            CNLabeledValue(label: $0.label, value: $0.value as NSString)
        }
        contact.postalAddresses = record.postalAddresses.map { Self.postalAddress(from: $0) }
        contact.instantMessageAddresses = record.instantMessageAddresses.map { Self.instantMessage(from: $0) }
        contact.socialProfiles = record.socialProfiles.map { Self.socialProfile(from: $0) }
        contact.contactRelations = record.relations.map {
            CNLabeledValue(label: $0.label, value: CNContactRelation(name: $0.value))
        }
        contact.dates = record.dates.map {
            CNLabeledValue(label: $0.label, value: Self.dateComponents(from: $0.date) as NSDateComponents)
        }
        contact.birthday = record.birthday.map { Self.dateComponents(from: $0) }
        contact.imageData = record.imageData
    }

    private static func postalAddress(from value: CNLabeledValue<CNPostalAddress>) -> ContactRecord.PostalAddress {
        let address = value.value
        return ContactRecord.PostalAddress(
            label: value.label,
            street: address.street,
            subLocality: address.subLocality,
            city: address.city,
            subAdministrativeArea: address.subAdministrativeArea,
            state: address.state,
            postalCode: address.postalCode,
            country: address.country
        )
    }

    private static func postalAddress(from record: ContactRecord.PostalAddress) -> CNLabeledValue<CNPostalAddress> {
        let address = CNMutablePostalAddress()
        address.street = record.street
        address.subLocality = record.subLocality
        address.city = record.city
        address.subAdministrativeArea = record.subAdministrativeArea
        address.state = record.state
        address.postalCode = record.postalCode
        address.country = record.country
        return CNLabeledValue(label: record.label, value: address)
    }

    private static func instantMessage(
        from value: CNLabeledValue<CNInstantMessageAddress>
    ) -> ContactRecord.InstantMessage {
        ContactRecord.InstantMessage(
            label: value.label,
            service: value.value.service,
            username: value.value.username
        )
    }

    private static func instantMessage(
        from record: ContactRecord.InstantMessage
    ) -> CNLabeledValue<CNInstantMessageAddress> {
        CNLabeledValue(
            label: record.label,
            value: CNInstantMessageAddress(username: record.username, service: record.service)
        )
    }

    private static func socialProfile(from value: CNLabeledValue<CNSocialProfile>) -> ContactRecord.SocialProfile {
        ContactRecord.SocialProfile(
            label: value.label,
            service: value.value.service,
            username: value.value.username,
            userIdentifier: value.value.userIdentifier,
            urlString: value.value.urlString
        )
    }

    private static func socialProfile(from record: ContactRecord.SocialProfile) -> CNLabeledValue<CNSocialProfile> {
        CNLabeledValue(
            label: record.label,
            value: CNSocialProfile(
                urlString: record.urlString,
                username: record.username,
                userIdentifier: record.userIdentifier,
                service: record.service
            )
        )
    }

    private static func dateValue(_ components: DateComponents) -> ContactRecord.DateValue {
        ContactRecord.DateValue(year: components.year, month: components.month, day: components.day)
    }

    private static func dateComponents(from value: ContactRecord.DateValue) -> DateComponents {
        var components = DateComponents()
        components.year = value.year
        components.month = value.month
        components.day = value.day
        return components
    }
}
