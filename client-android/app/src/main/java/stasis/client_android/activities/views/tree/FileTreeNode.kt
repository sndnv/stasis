package stasis.client_android.activities.views.tree

import com.amrdeveloper.treeview.TreeNode
import stasis.client_android.R
import java.nio.file.FileSystem
import java.nio.file.Files

data class FileTreeNode(
    val id: String,
    val parent: String?,
    val name: String,
    val root: String,
    val children: Map<String, FileTreeNode>,
    val isDirectory: Boolean
) {
    val extension: String? by lazy {
        if (isDirectory) null
        else name.split(".").lastOrNull()
    }

    fun put(id: String, path: String, parts: List<String>, fs: FileSystem): FileTreeNode =
        if (parts.isNotEmpty()) {
            val next = parts.first()
            val remaining = parts.drop(1)

            val updated = (children[next] ?: FileTreeNode(
                id = id,
                parent = id.removeSuffix(next),
                name = next,
                root = root,
                children = emptyMap(),
                isDirectory = Files.isDirectory(fs.getPath(id)),
            )).put(id = id, path = path, parts = remaining, fs = fs)

            copy(children = children.plus(next to updated))
        } else {
            this
        }

    companion object {
        fun fromPaths(root: String, paths: List<String>, fs: FileSystem): TreeNode {
            val start = FileTreeNode(
                id = root,
                parent = null,
                name = root,
                root = root,
                children = emptyMap(),
                isDirectory = Files.isDirectory(fs.getPath(root))
            )

            val tree = paths.fold(start) { tree, path ->
                val remaining = path.removePrefix(root)
                tree.put(
                    id = path,
                    path = remaining,
                    parts = remaining.split("/").filter { it.isNotEmpty() },
                    fs = fs
                )
            }

            fun convert(n: FileTreeNode): TreeNode {
                val u = TreeNode(n, R.layout.list_item_rules_tree)
                n.children.values.forEach { u.addChild(convert(it)) }
                return u
            }

            return convert(tree)
        }
    }
}