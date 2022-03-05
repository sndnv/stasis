package stasis.client_android.mocks

import stasis.client_android.lib.ops.monitoring.ServerMonitor

class MockServerMonitor : ServerMonitor {
    override suspend fun stop() = Unit
}
