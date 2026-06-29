package stasis.client_android.lib.model

import io.github.sndnv.fsi.Schemes
import java.nio.file.Path
import java.nio.file.Paths

sealed class EntityRef {
    abstract val key: String

    abstract fun mapFilesystem(f: (Path) -> Path): EntityRef
    abstract fun mapLibrary(f: (String, String) -> Pair<String, String>): EntityRef

    fun flatMap(f: (EntityRef) -> EntityRef): EntityRef = f(this)

    abstract fun asFilesystem(): Filesystem
    abstract fun asLibrary(): Library

    data class Filesystem(val path: Path) : EntityRef() {
        override val key: String by lazy { path.toAbsolutePath().toString() }

        override fun mapFilesystem(f: (Path) -> Path): EntityRef =
            Filesystem(path = f(path))

        override fun mapLibrary(f: (String, String) -> Pair<String, String>): EntityRef =
            this

        override fun asFilesystem(): Filesystem =
            this

        override fun asLibrary(): Library =
            throw IllegalArgumentException("Requested a library reference but [$key] found")
    }

    data class Library(val scheme: String, val path: String) : EntityRef() {
        override val key: String by lazy { "$scheme${Schemes.Delimiter}$path" }

        override fun mapFilesystem(f: (Path) -> Path): EntityRef =
            this

        override fun mapLibrary(f: (String, String) -> Pair<String, String>): EntityRef {
            val (mappedScheme, mappedPath) = f(scheme, path)
            return Library(scheme = mappedScheme, path = mappedPath)
        }

        override fun asFilesystem(): Filesystem =
            throw IllegalArgumentException("Requested a filesystem reference but [$key] found")

        override fun asLibrary(): Library =
            this
    }

    companion object {
        fun default(key: String): EntityRef {
            val (scheme, path) = Schemes.extract(key)
            return when (scheme) {
                null -> Filesystem(path = Paths.get(key))
                else -> Library(scheme = scheme, path = path)
            }
        }
    }
}
