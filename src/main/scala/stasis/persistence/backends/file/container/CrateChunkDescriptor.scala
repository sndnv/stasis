package stasis.persistence.backends.file.container

import stasis.persistence.backends.file.container.headers.ChunkHeader

final case class CrateChunkDescriptor(header: ChunkHeader, dataStartOffset: Long)
