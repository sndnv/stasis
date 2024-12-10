package stasis.client_android.activities.fragments.rules

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.amrdeveloper.treeview.TreeNode
import com.amrdeveloper.treeview.TreeViewAdapter
import com.amrdeveloper.treeview.TreeViewHolder
import com.amrdeveloper.treeview.TreeViewHolderFactory
import stasis.client_android.R
import stasis.client_android.activities.helpers.Common.toMinimizedString
import stasis.client_android.activities.views.dialogs.InformationDialogFragment
import stasis.client_android.activities.views.tree.FileTreeNode
import stasis.client_android.databinding.DialogRulesTreeBinding
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.Specification
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Either.Left
import stasis.client_android.lib.utils.Either.Right
import stasis.client_android.persistence.rules.RulesConfig
import stasis.client_android.utils.DynamicArguments
import stasis.client_android.utils.DynamicArguments.pullArguments
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

class RuleTreeDialogFragment : DialogFragment(), DynamicArguments.Receiver {
    override val argumentsKey: String = ArgumentsKey

    override val receiver: Fragment = this

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
        val binding = DialogRulesTreeBinding.inflate(inflater)

        binding.rulesTree.layoutManager = LinearLayoutManager(requireContext())

        pullArguments<Arguments>().observe(viewLifecycleOwner) { arguments ->
            binding.rulesTreeTitle.text = when (val d = arguments.definition) {
                null -> getString(R.string.rules_tree_title_default)
                is Left -> getString(R.string.rules_tree_title, d.value.toMinimizedString())
                is Right -> getString(R.string.rules_tree_title, d.value.info)
            }

            val fs: FileSystem = FileSystems.getDefault()

            val spec = Specification(
                rules = arguments.rules,
                onMatchIncluded = {},
                filesystem = fs
            )
            val included = spec.included.map { it.toAbsolutePath().toString() }.toSet()
            val excluded = spec.excluded.map { it.toAbsolutePath().toString() }.toSet()

            val factory = TreeViewHolderFactory { v, _ -> Holder(view = v, included = included, excluded = excluded) }

            val adapter = TreeViewAdapter(factory)
            binding.rulesTree.adapter = adapter

            val root = arguments.rules
                .map { it.directory }
                .reduceOrNull { a, b -> commonAncestor(a, b, fs) } ?: RulesConfig.DefaultStorageDirectory

            try {
                val paths = TreeFileVisitor().walk(fs, root)
                adapter.updateTreeNodes(listOf(FileTreeNode.fromPaths(root = root, paths = paths, fs = fs)))

                adapter.setTreeNodeLongClickListener { treeNode, _ ->
                    val node = (treeNode.value as FileTreeNode)
                    RuleTreeEntryContextDialogFragment(
                        definition = arguments.definition?.fold({ it }, { it.id }),
                        selectedNode = node,
                        nodeColor = nodeColor(requireContext(), node, included, excluded),
                        onRuleCreationRequested = { rule ->
                            arguments.onRuleCreationRequested(rule)
                            dialog?.dismiss()
                        }
                    ).show(childFragmentManager, RuleTreeEntryContextDialogFragment.Tag)
                    true
                }

                adapter.expandAll()

                binding.rulesTreeLoadInProgress.isVisible = false
                binding.rulesTreeError.isVisible = false
                binding.rulesTree.isVisible = true
            } catch (e: Exception) {
                when (e) {
                    is UncheckedIOException, is IOException -> {
                        binding.rulesTreeLoadInProgress.isVisible = false
                        binding.rulesTreeError.isVisible = true
                        binding.rulesTree.isVisible = false
                    }

                    else -> throw e
                }
            }
        }

        binding.rulesTreeHelpButton.setOnClickListener {
            InformationDialogFragment()
                .withTitle(getString(R.string.context_help_dialog_title))
                .withMessage(getString(R.string.context_help_rules_tree))
                .show(childFragmentManager)
        }

        binding.rulesTreeLoadInProgress.isVisible = true
        binding.rulesTreeError.isVisible = false
        binding.rulesTree.isVisible = false

        return binding.root
    }

    companion object {
        data class Arguments(
            val definition: Either<DatasetDefinitionId, DatasetDefinition>?,
            val rules: List<Rule>,
            val onRuleCreationRequested: (Rule) -> Unit
        ) : DynamicArguments.ArgumentSet

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

        class TreeFileVisitor : FileVisitor<Path> {
            private val collected = mutableListOf<String>()
            private val failures = mutableListOf<String>()

            fun walk(fs: FileSystem, start: String): List<String> {
                Files.walkFileTree(fs.getPath(start), this)
                if (collected.isNotEmpty()) {
                    return collected
                } else if (failures.isNotEmpty()) {
                    throw IOException("One or more failure encountered: [${failures.joinToString(", ")}]")
                } else {
                    throw IOException("The provided start path [$start] did not produce any results")
                }
            }

            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                dir?.let { collected.add(it.toString()) }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                file?.let { collected.add(it.toString()) }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                val failure = exc?.let { "${it.javaClass.simpleName} - ${it.message}" }
                Log.v("RuleTreeDialogFragment", "Failure encountered when visiting [$file]: [$failure]")
                failure?.let { failures.add(failure) }
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult =
                FileVisitResult.CONTINUE
        }

        private const val ArgumentsKey: String =
            "stasis.client_android.activities.fragments.rules.RuleTreeDialogFragment.arguments.key"

        const val Tag: String =
            "stasis.client_android.activities.fragments.rules.RuleTreeDialogFragment"
    }
}
