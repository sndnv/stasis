package stasis.client_android.mocks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.tracking.TrackerView

class MockTrackerView : TrackerView {
    override val state: LiveData<TrackerView.State> =
        MutableLiveData()

    override fun operationUpdates(operation: OperationId): LiveData<Operation.Progress> =
        MutableLiveData()
}
