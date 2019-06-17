package stasis.identity.model.codes.generators

import stasis.identity.model.codes.AuthorizationCode

trait AuthorizationCodeGenerator {
  def generate(): AuthorizationCode
}
