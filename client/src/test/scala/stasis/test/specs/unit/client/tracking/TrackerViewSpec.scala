package stasis.test.specs.unit.client.tracking

import stasis.client.tracking.TrackerView
import stasis.shared.ops.Operation
import stasis.test.specs.unit.UnitSpec

import scala.collection.immutable.Queue

class TrackerViewSpec extends UnitSpec {
  "A TrackerView State" should "support updating server state" in {
    val state = TrackerView.State.empty
    val server = "test-server"

    val actualState = state.withServer(server = server, reachable = true)

    actualState should be(
      TrackerView.State(
        operations = Map.empty,
        servers = Map(
          server -> TrackerView.ServerState(
            reachable = true,
            timestamp = actualState.servers(server).timestamp
          )
        )
      )
    )
  }

  it should "support updating operation state" in {
    val state = TrackerView.State.empty
    val operation = Operation.generateId()

    val actualState = state.withStep(
      operationId = operation,
      stage = "test-stage",
      step = "test-step"
    )

    actualState should be(
      TrackerView.State(
        operations = Map(
          operation -> Operation.Progress(
            stages = Map(
              "test-stage" -> Operation.Progress.Stage(
                steps = Queue(
                  Operation.Progress.Stage.Step(
                    name = "test-step",
                    completed = actualState.operations(operation).stages("test-stage").steps.head.completed
                  )
                )
              )
            ),
            failures = Queue.empty,
            completed = None
          )
        ),
        servers = Map.empty
      )
    )
  }

  it should "support updating step state (failed)" in {
    val state = TrackerView.State.empty
    val operation = Operation.generateId()

    val actualState = state.withFailure(
      operationId = operation,
      failure = new RuntimeException("test failure")
    )

    actualState should be(
      TrackerView.State(
        operations = Map(
          operation -> Operation.Progress(
            stages = Map.empty,
            failures = Queue("test failure"),
            completed = None
          )
        ),
        servers = Map.empty
      )
    )
  }

  it should "support updating step state (completed)" in {
    val state = TrackerView.State.empty
    val operation = Operation.generateId()

    val actualState = state.completed(operationId = operation)

    actualState should be(
      TrackerView.State(
        operations = Map(
          operation -> Operation.Progress(
            stages = Map.empty,
            failures = Queue.empty,
            completed = Some(actualState.operations(operation).completed.get)
          )
        ),
        servers = Map.empty
      )
    )
  }
}
