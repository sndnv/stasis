package stasis.test.specs.unit.routing.mocks

import stasis.persistence.Store
import stasis.routing.LocalRouter

class LocalMockRouter(store: Store) extends LocalRouter(store) {}
