package stasis.client.analysis

import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant

final case class BaseEntityMetadata(
  path: Path,
  isDirectory: Boolean,
  link: Option[Path],
  isHidden: Boolean,
  created: Instant,
  updated: Instant,
  owner: String,
  group: String,
  permissions: String,
  attributes: BasicFileAttributes
)
