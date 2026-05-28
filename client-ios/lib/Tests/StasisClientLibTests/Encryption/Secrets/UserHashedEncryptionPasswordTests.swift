import Foundation
@testable import StasisClientLib
import Testing

@Suite("UserHashedEncryptionPassword")
struct UserHashedEncryptionPasswordTests {
    private let hashedPassword = Data(base64Encoded:
        "US794fdkdF/LvnVnSo4LjD78QhaT0iUTaQA1u78vz+z6MGagfoHbcFpgtdhTDz2IM5zTL0LFOh0cZmghGfu8jQ=="
    )!

    private var password: UserHashedEncryptionPassword {
        UserHashedEncryptionPassword(
            user: SecretsConfigFixtures.testUser,
            hashedPassword: hashedPassword,
            target: SecretsConfigFixtures.testConfig
        )
    }

    @Test("generates local encryption secrets")
    func generatesLocalEncryptionSecret() {
        let iv = Data(base64Encoded:
            "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
        )!
        let key = Data(base64Encoded: "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU=")!

        #expect(
            password.toLocalEncryptionSecret()
            == UserLocalEncryptionSecret(
                user: SecretsConfigFixtures.testUser, iv: iv, key: key,
                target: SecretsConfigFixtures.testConfig
            )
        )
    }

    @Test("generates key-store encryption secrets")
    func generatesKeyStoreEncryptionSecret() {
        let iv = Data(base64Encoded:
            "6mSjkDoXUNNK8TGbebRYWXnjfskeVHXhaMxBRKD+ITvMckUp0ZQtUeEttz9pA0vWQ4MKa8otGmyDJ7OCrdeY4g=="
        )!
        let key = Data(base64Encoded: "kROAgx70MxRKeDODRMyshRH0tswcd4jydKA60r+5knI=")!

        #expect(
            password.toKeyStoreEncryptionSecret()
            == UserKeyStoreEncryptionSecret(
                user: SecretsConfigFixtures.testUser, iv: iv, key: key,
                target: SecretsConfigFixtures.testConfig
            )
        )
    }

    @Test("does not render its content via description")
    func description() {
        #expect(password.description == "Secret(\(String(reflecting: type(of: password))))")
    }
}
