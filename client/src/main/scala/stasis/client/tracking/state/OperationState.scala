package stasis.client.tracking.state

import stasis.shared.ops.Operation

trait OperationState {
  def `type`: Operation.Type
  def isCompleted: Boolean
  def asProgress: Operation.Progress
}
