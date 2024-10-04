package stasis.layers.persistence.migration

final case class MigrationResult(found: Int, executed: Int) {
  def +(other: MigrationResult): MigrationResult =
    MigrationResult(found = found + other.found, executed = executed + other.executed)
}
