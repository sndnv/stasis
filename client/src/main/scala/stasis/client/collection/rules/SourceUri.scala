package stasis.client.collection.rules

import io.github.sndnv.fsi.Schemes

object SourceUri {
  def scheme(source: String): Option[String] = Option(Schemes.extract(source).component1())
}
