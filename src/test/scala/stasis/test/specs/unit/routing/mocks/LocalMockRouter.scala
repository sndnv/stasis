package stasis.test.specs.unit.routing.mocks

import stasis.persistence.CrateStore
import stasis.routing.LocalRouter

class LocalMockRouter(store: CrateStore) extends LocalRouter(store) {}
