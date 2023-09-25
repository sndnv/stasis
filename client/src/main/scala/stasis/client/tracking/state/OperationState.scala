package stasis.client.tracking.state

import java.time.Instant

import stasis.shared.ops.Operation

trait OperationState {
  def `type`: Operation.Type
  def started: Instant
  def isCompleted: Boolean
  def asProgress: Operation.Progress
}
