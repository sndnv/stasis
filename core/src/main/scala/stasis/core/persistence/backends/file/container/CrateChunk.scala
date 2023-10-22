package stasis.core.persistence.backends.file.container

import org.apache.pekko.util.ByteString
import stasis.core.persistence.backends.file.container.headers.ChunkHeader

final case class CrateChunk(header: ChunkHeader, data: ByteString)
