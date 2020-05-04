package stasis.core.persistence.backends.slick

import slick.jdbc._

object SlickProfile {
  def apply(profile: String): JdbcProfile =
    profile match {
      case "DB2Profile"       => DB2Profile
      case "DerbyProfile"     => DerbyProfile
      case "H2Profile"        => H2Profile
      case "HsqldbProfile"    => HsqldbProfile
      case "MySQLProfile"     => MySQLProfile
      case "OracleProfile"    => OracleProfile
      case "PostgresProfile"  => PostgresProfile
      case "SQLiteProfile"    => SQLiteProfile
      case "SQLServerProfile" => SQLServerProfile
    }
}
