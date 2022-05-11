package stasis.client.collection.rules.internal

import stasis.client.collection.rules.Rule

final case class IndexedRule(
  index: Int,
  underlying: Rule
)
