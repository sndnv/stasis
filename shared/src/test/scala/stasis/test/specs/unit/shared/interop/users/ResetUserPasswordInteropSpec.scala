package stasis.test.specs.unit.shared.interop.users

import stasis.shared.api.Formats._
import stasis.shared.api.requests.ResetUserPassword
import stasis.shared.api.responses.UpdatedUserSalt
import stasis.test.specs.unit.shared.interop.InteropSpec

class ResetUserPasswordInteropSpec extends InteropSpec {
  "ResetUserPassword interop" should "decode and re-encode a request" in {
    assert(
      domain = "users",
      resource = "ResetUserPassword",
      matches = ResetUserPassword(rawPassword = "new-password-value")
    )
  }

  it should "decode and re-encode UpdatedUserSalt" in {
    assert(
      domain = "users",
      resource = "UpdatedUserSalt",
      matches = UpdatedUserSalt(salt = "new-salt-value")
    )
  }
}
