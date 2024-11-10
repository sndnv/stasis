package stasis.client_android.activities.fragments.rules

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amrdeveloper.treeview.TreeNode
import com.amrdeveloper.treeview.TreeViewAdapter
import com.amrdeveloper.treeview.TreeViewHolder
import com.amrdeveloper.treeview.TreeViewHolderFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.views.tree.FileTreeNode
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.Specification
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Either.Left
import stasis.client_android.lib.utils.Either.Right
import stasis.client_android.persistence.rules.RulesConfig
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.stream.Collectors

class RuleTreeDialogFragment(
    private val definition: Either<DatasetDefinitionId, DatasetDefinition>?,
    private val rules: List<Rule>,
    private val onRuleCreationRequested: (Rule) -> Unit
) : DialogFragment() {
    override fun onStart() {
        super.onStart()
        val width = ViewGroup.LayoutParams.MATCH_PARENT
        val height = ViewGroup.LayoutParams.MATCH_PARENT
        dialog?.window?.setLayout(width, height)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_rules_tree, container, false)

        val rulesTreeTitle = view.findViewById<TextView>(R.id.rules_tree_title)
        val rulesTreeHelpButton = view.findViewById<ImageView>(R.id.rules_tree_help_button)
        val rulesTreeLoadInProgress = view.findViewById<CircularProgressIndicator>(R.id.rules_tree_load_in_progress)
        val rulesTreeError = view.findViewById<TextView>(R.id.rules_tree_error)
        val rulesTree = view.findViewById<RecyclerView>(R.id.rules_tree)
        rulesTree.layoutManager = LinearLayoutManager(requireContext())

        rulesTreeTitle.text = when (definition) {
            null -> getString(R.string.rules_tree_title_default)
            is Left -> getString(R.string.rules_tree_title, definition.value.toMinimizedString())
            is Right -> getString(R.string.rules_tree_title, definition.value.info)
        }

        rulesTreeHelpButton.setOnClickListener {
            MaterialAlertDialogBuilder(view.context)
                .setTitle(getString(R.string.context_help_dialog_title))
                .setMessage(getString(R.string.context_help_rules_tree))
                .show()
        }

        rulesTreeLoadInProgress.isVisible = true
        rulesTreeError.isVisible = false
        rulesTree.isVisible = false

        val fs: FileSystem = FileSystems.getDefault()

        val spec = Specification(
            rules = rules,
            onMatchIncluded = {},
            filesystem = fs
        )
        val included = spec.included.map { it.toAbsolutePath().toString() }.toSet()
        val excluded = spec.excluded.map { it.toAbsolutePath().toString() }.toSet()

        val factory = TreeViewHolderFactory { v, _ -> Holder(view = v, included = included, excluded = excluded) }

        val adapter = TreeViewAdapter(factory)
        rulesTree.adapter = adapter

        val root = rules
            .map { it.directory }
            .reduceOrNull { a, b -> commonAncestor(a, b, fs) } ?: RulesConfig.DefaultStorageDirectory

        try {
            val paths = Files.walk(fs.getPath(root)).map { it.toString() }.collect(Collectors.toList())
            adapter.updateTreeNodes(listOf(FileTreeNode.fromPaths(root = root, paths = paths, fs = fs)))

            adapter.setTreeNodeLongClickListener { treeNode, _ ->
                val node = (treeNode.value as FileTreeNode)
                RuleTreeEntryContextDialogFragment(
                    definition = definition?.fold({ it }, { it.id }),
                    selectedNode = node,
                    nodeColor = nodeColor(view.context, node, included, excluded),
                    onRuleCreationRequested = { rule ->
                        onRuleCreationRequested(rule)
                        dialog?.dismiss()
                    }
                ).show(parentFragmentManager, RuleTreeEntryContextDialogFragment.Tag)
                true
            }

            adapter.expandAll()

            rulesTreeLoadInProgress.isVisible = false
            rulesTreeError.isVisible = false
            rulesTree.isVisible = true
        } catch (e: Exception) {
            when (e) {
                is UncheckedIOException, is IOException -> {
                    rulesTreeLoadInProgress.isVisible = false
                    rulesTreeError.isVisible = true
                    rulesTree.isVisible = false
                }

                else -> throw e
            }
        }

        return view
    }

    companion object {
        class Holder(val view: View, val included: Set<String>, val excluded: Set<String>) : TreeViewHolder(view) {
            val name: TextView = itemView.findViewById(R.id.rule_tree_node_name)
            val icon: ImageView = itemView.findViewById(R.id.rule_tree_node_icon)
            val state: ImageView = itemView.findViewById(R.id.rule_tree_node_state)

            override fun bindTreeNode(node: TreeNode?) {
                super.bindTreeNode(node)

                val underlying = (node?.value as FileTreeNode)

                name.text = if (underlying.isDirectory) {
                    view.context.getString(
                        R.string.rules_tree_node_name_directory,
                        underlying.name,
                        underlying.children.size.toString()
                    )
                } else {
                    view.context.getString(
                        R.string.rules_tree_node_name_file,
                        underlying.name
                    )
                }

                name.setTextColor(nodeColor(view.context, underlying, included, excluded))

                icon.setImageResource(
                    if (underlying.isDirectory) R.drawable.ic_tree_directory
                    else R.drawable.ic_tree_file
                )

                icon.contentDescription = view.context.getString(
                    if (underlying.isDirectory) R.string.rules_tree_node_icon_hint_directory
                    else R.string.rules_tree_node_icon_hint_file
                )

                if (node.children?.isEmpty() == true) {
                    state.visibility = View.INVISIBLE
                } else {
                    state.visibility = View.VISIBLE
                    state.setImageResource(
                        if (node.isExpanded) R.drawable.ic_tree_arrow_down else R.drawable.ic_tree_arrow_right
                    )
                }
            }
        }

        private fun commonAncestor(a: String, b: String, fs: FileSystem): String {
            val pathA =
                fs.getPath(a).normalize().toAbsolutePath().toString().split(fs.separator).filter { it.isNotBlank() }
            val pathB =
                fs.getPath(b).normalize().toAbsolutePath().toString().split(fs.separator).filter { it.isNotBlank() }

            val common = pathA.withIndex().takeWhile { e ->
                e.value == pathB.getOrNull(e.index)
            }.joinToString(fs.separator) { it.value }

            return "/$common"
        }

        private fun nodeColor(context: Context, node: FileTreeNode, included: Set<String>, excluded: Set<String>): Int =
            if (nodeIsIncluded(node, included)) context.getColor(R.color.launcher_tertiary_2)
            else if (nodeIsExcluded(node, excluded)) context.getColor(R.color.design_default_color_error)
            else Color.GRAY

        private fun nodeIsIncluded(node: FileTreeNode, included: Set<String>): Boolean =
            included.contains(node.id)

        private fun nodeIsExcluded(node: FileTreeNode, excluded: Set<String>): Boolean =
            excluded.any { node.id == it || node.id.startsWith("$it/") }

        const val Tag: String = "stasis.client_android.activities.fragments.rules.RulesFragmentRuleTreeDialogFragment"
    }
}
