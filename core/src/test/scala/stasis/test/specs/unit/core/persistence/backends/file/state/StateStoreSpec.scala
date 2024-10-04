package stasis.test.specs.unit.core.persistence.backends.file.state

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.FileSystemHelpers
import stasis.test.specs.unit.core.FileSystemHelpers.FileSystemSetup

class StateStoreSpec extends AsyncUnitSpec with FileSystemHelpers with StateStoreBehaviour {
  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "StateStoreSpec"
  )

  "A StateStore on a Unix filesystem" should behave like stateStore(setup = FileSystemSetup.Unix.withEmptyDirs)

  "A StateStore on a MacOS filesystem" should behave like stateStore(setup = FileSystemSetup.MacOS.withEmptyDirs)

  "A StateStore on a Windows filesystem" should behave like stateStore(setup = FileSystemSetup.Windows.withEmptyDirs)
}
