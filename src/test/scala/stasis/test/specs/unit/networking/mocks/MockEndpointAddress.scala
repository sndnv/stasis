package stasis.test.specs.unit.networking.mocks

import java.util.UUID

import stasis.networking.EndpointAddress

case class MockEndpointAddress(id: UUID = UUID.randomUUID()) extends EndpointAddress
