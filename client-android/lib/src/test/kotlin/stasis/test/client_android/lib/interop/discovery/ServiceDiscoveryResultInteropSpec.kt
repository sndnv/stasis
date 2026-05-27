package stasis.test.client_android.lib.interop.discovery

import stasis.client_android.lib.discovery.ServiceApiEndpoint
import stasis.client_android.lib.discovery.ServiceDiscoveryResult
import stasis.client_android.lib.model.core.networking.EndpointAddress
import stasis.test.client_android.lib.interop.InteropSpec

class ServiceDiscoveryResultInteropSpec : InteropSpec({
    "ServiceDiscoveryResult interop" should {
        "decode and re-encode KeepExisting" {
            assert<ServiceDiscoveryResult>(
                domain = "discovery",
                resource = "ServiceDiscoveryResult.keep-existing",
                matches = ServiceDiscoveryResult.KeepExisting
            )
        }

        "decode and re-encode SwitchTo with an HTTP core endpoint" {
            assert<ServiceDiscoveryResult>(
                domain = "discovery",
                resource = "ServiceDiscoveryResult.switch-to-http",
                matches = ServiceDiscoveryResult.SwitchTo(
                    endpoints = ServiceDiscoveryResult.Endpoints(
                        api = ServiceApiEndpoint.Api(uri = "https://api.example.test"),
                        core = ServiceApiEndpoint.Core(
                            address = EndpointAddress.HttpEndpointAddress(uri = "https://core.example.test")
                        ),
                        discovery = ServiceApiEndpoint.Discovery(uri = "https://discovery.example.test")
                    ),
                    recreateExisting = false
                )
            )
        }

        "decode and re-encode SwitchTo with a gRPC core endpoint" {
            assert<ServiceDiscoveryResult>(
                domain = "discovery",
                resource = "ServiceDiscoveryResult.switch-to-grpc",
                matches = ServiceDiscoveryResult.SwitchTo(
                    endpoints = ServiceDiscoveryResult.Endpoints(
                        api = ServiceApiEndpoint.Api(uri = "https://api.example.test"),
                        core = ServiceApiEndpoint.Core(
                            address = EndpointAddress.GrpcEndpointAddress(
                                host = "core.example.test",
                                port = 9999,
                                tlsEnabled = true
                            )
                        ),
                        discovery = ServiceApiEndpoint.Discovery(uri = "https://discovery.example.test")
                    ),
                    recreateExisting = true
                )
            )
        }
    }
})
