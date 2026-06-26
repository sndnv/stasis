package stasis.test.specs.unit.client.ops.integration

import io.github.sndnv.layers.testing.FileSystemHelpers
import io.github.sndnv.layers.testing.FileSystemHelpers.FileSystemSetup
import io.github.sndnv.layers.testing.UnitSpec
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll

import stasis.client.ops.ParallelismConfig
import stasis.shared.secrets.SecretsConfig

class BackupAndRecoverySpec extends UnitSpec with BeforeAndAfterAll with FileSystemHelpers with BackupAndRecoveryBehaviour {
  "A backup and recovery process on a Unix filesystem" should behave like backupAndRecovery(setup = FileSystemSetup.Unix)

  "A backup and recovery process on a MacOS filesystem" should behave like backupAndRecovery(setup = FileSystemSetup.MacOS)

  "A backup and recovery process on a Windows filesystem" should behave like backupAndRecovery(setup = FileSystemSetup.Windows)

  private implicit lazy val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "BackupAndRecoverySpec"
  )

  private implicit lazy val parallelism: ParallelismConfig = ParallelismConfig(entities = 1, entityParts = 1)

  private implicit lazy val secretsConfig: SecretsConfig = SecretsConfig(
    derivation = SecretsConfig.Derivation(
      encryption = SecretsConfig.Derivation.Encryption(
        secretSize = 64,
        iterations = 100000,
        saltPrefix = "unit-test"
      ),
      authentication = SecretsConfig.Derivation.Authentication(
        enabled = true,
        secretSize = 64,
        iterations = 100000,
        saltPrefix = "unit-test"
      )
    ),
    encryption = SecretsConfig.Encryption(
      file = SecretsConfig.Encryption.File(keySize = 16, ivSize = 16),
      metadata = SecretsConfig.Encryption.Metadata(keySize = 24, ivSize = 32),
      deviceSecret = SecretsConfig.Encryption.DeviceSecret(keySize = 32, ivSize = 64)
    )
  )

  override protected def afterAll(): Unit =
    typedSystem.terminate()
}
