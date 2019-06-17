package stasis.identity.api.manage.setup

import stasis.identity.model.secrets.Secret

final case class Config(
  clientSecrets: Secret.ClientConfig,
  ownerSecrets: Secret.ResourceOwnerConfig
)
