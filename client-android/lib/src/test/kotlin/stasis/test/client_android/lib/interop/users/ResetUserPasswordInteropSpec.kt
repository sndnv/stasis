package stasis.test.client_android.lib.interop.users

import stasis.client_android.lib.model.server.api.requests.ResetUserPassword
import stasis.client_android.lib.model.server.api.responses.UpdatedUserSalt
import stasis.test.client_android.lib.interop.InteropSpec

class ResetUserPasswordInteropSpec : InteropSpec({
    "ResetUserPassword interop" should {
        "decode and re-encode a request" {
            assert(
                domain = "users",
                resource = "ResetUserPassword",
                matches = ResetUserPassword(rawPassword = "new-password-value")
            )
        }

        "decode and re-encode UpdatedUserSalt" {
            assert(
                domain = "users",
                resource = "UpdatedUserSalt",
                matches = UpdatedUserSalt(salt = "new-salt-value")
            )
        }
    }
})
