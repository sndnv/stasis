package stasis.core.persistence.backends.file.container.ops

trait AutoCloseSupport {

  /**
    * Enables automatic closing of [[java.io.Closeable]] resources:
    * {{{
    * import java.nio.file.{Files, Path, Paths}
    *
    * val result: Int = using(Files.newOutputStream(Paths.get("some-file"))) { stream =>
    *   val data: Array[Byte] = ???
    *
    *   stream.write(data)
    *   stream.flush()
    *
    *   data.length
    * }
    * }}}.
    *
    * Based on https://stackoverflow.com/a/2219494
    *
    * @param closeable closeable resource
    * @param block block to execute with provided resource
    * @tparam C closeable resource type
    * @tparam R return type
    * @return the value returned by the provided block
    */
  def using[C <: java.io.Closeable, R](closeable: C)(block: C => R): R =
    try {
      block(closeable)
    } finally {
      Option(closeable).foreach(_.close())
    }
}
