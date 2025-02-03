package stasis.client_android.lib.ops.exceptions

import stasis.client_android.lib.ops.Operation

class OperationRestrictedFailure(val restrictions: List<Operation.Restriction>) : Exception()
