package stasis.client.model

import java.nio.file.Path
import java.nio.file.Paths

import io.github.sndnv.fsi.Schemes

sealed trait EntityRef {
  def key: String

  def mapFilesystem(f: Path => Path): EntityRef
  def mapLibrary(f: (String, String) => (String, String)): EntityRef

  def flatMap(f: EntityRef => EntityRef): EntityRef = f(this)

  def asFilesystem: EntityRef.Filesystem
  def asLibrary: EntityRef.Library
}

object EntityRef {
  def default(key: String): EntityRef = {
    val components = Schemes.extract(key)
    Option(components.component1()) match {
      case Some(scheme) => EntityRef.Library(scheme = scheme, path = components.component2())
      case None         => EntityRef.Filesystem(path = Paths.get(key))
    }
  }

  final case class Filesystem(path: Path) extends EntityRef {
    override lazy val key: String = path.toAbsolutePath.toString

    override def mapFilesystem(f: Path => Path): EntityRef =
      EntityRef.Filesystem(path = f(path))

    override def mapLibrary(f: (String, String) => (String, String)): EntityRef =
      this

    override def asFilesystem: Filesystem =
      this

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def asLibrary: Library =
      throw new IllegalArgumentException(s"Requested a library reference but [$key] found")
  }

  final case class Library(scheme: String, path: String) extends EntityRef {
    override lazy val key: String = s"$scheme${Schemes.Delimiter}$path"

    override def mapFilesystem(f: Path => Path): EntityRef =
      this

    override def mapLibrary(f: (String, String) => (String, String)): EntityRef =
      EntityRef.Library(f(scheme, path))

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def asFilesystem: Filesystem =
      throw new IllegalArgumentException(s"Requested a filesystem reference but [$key] found")

    override def asLibrary: Library =
      this
  }

  object Library {
    def apply(pair: (String, String)): Library =
      Library(scheme = pair._1, path = pair._2)
  }
}
