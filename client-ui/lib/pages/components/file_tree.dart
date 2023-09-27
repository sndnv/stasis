import 'dart:collection';

import 'package:flutter/material.dart';
import 'package:flutter_fancy_tree_view/flutter_fancy_tree_view.dart';

// ignore_for_file: prefer_collection_literals

class FileTree extends StatefulWidget {
  const FileTree({
    super.key,
    required this.root,
    required this.pathStyle,
    required this.pathDetails,
    this.onTap,
  });

  final TreeNode root;
  final TreeNodeStyle Function(String path) pathStyle;
  final String? Function(String path) pathDetails;
  final void Function(String path)? onTap;

  static FileTree fromPaths({
    required List<String> paths,
    required TreeNodeStyle Function(String path) pathStyle,
    required String? Function(String path) pathDetails,
    void Function(String path)? onTap,
  }) =>
      FileTree(
        root: TreeNode.fromPaths(paths),
        pathStyle: pathStyle,
        pathDetails: pathDetails,
        onTap: onTap,
      );

  @override
  State createState() {
    return _FileTreeState();
  }
}

class _FileTreeState extends State<FileTree> {
  late final TreeController<TreeNode> controller = TreeController(
    roots: [widget.root],
    childrenProvider: (n) => n.children.values,
  );

  @override
  Widget build(BuildContext context) {
    return TreeView(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      treeController: controller,
      nodeBuilder: (context, entry) {
        final path = entry.node.id;

        return TreeNodeEntry(
          key: ValueKey(entry.node),
          entry: entry,
          onTap: (hasChildren) {
            hasChildren ? controller.toggleExpansion(entry.node) : widget.onTap?.call(entry.node.id!);
          },
          style: path != null ? widget.pathStyle(path) : TreeNodeStyle.defaultStyle,
          details: path != null ? widget.pathDetails(path) : null,
        );
      },
    );
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }
}

class TreeNode {
  const TreeNode({
    this.id,
    required this.label,
    required this.children,
  });

  static TreeNode fromPaths(List<String> paths) {
    final tree = root();

    for (var path in paths) {
      tree._put(path: path, pathParts: path.split('/').where((p) => p.isNotEmpty));
    }

    return tree;
  }

  static TreeNode root() => TreeNode(label: '/', children: LinkedHashMap());

  final String? id;
  final String label;
  final Map<String, TreeNode> children;

  TreeNode _put({required String path, required Iterable<String> pathParts}) {
    if (pathParts.isNotEmpty) {
      final next = pathParts.first;
      final remaining = pathParts.skip(1);

      final updated = (children[next] ??
              TreeNode(
                id: null,
                label: next,
                children: {},
              ))
          ._put(
        path: path,
        pathParts: remaining,
      );

      children[next] = remaining.isEmpty ? updated._withId(path) : updated;
    }
    return this;
  }

  TreeNode _withId(String id) {
    return TreeNode(id: id, label: label, children: children);
  }
}

class TreeNodeEntry extends StatelessWidget {
  const TreeNodeEntry({
    super.key,
    required this.entry,
    required this.onTap,
    required this.style,
    required this.details,
  });

  final TreeEntry<TreeNode> entry;
  final void Function(bool) onTap;
  final TreeNodeStyle style;
  final String? details;

  @override
  Widget build(BuildContext context) {
    const density = VisualDensity(
      horizontal: VisualDensity.minimumDensity,
      vertical: VisualDensity.minimumDensity,
    );

    return InkWell(
      onTap: () => onTap(entry.hasChildren),
      child: TreeIndentation(
        guide: const IndentGuide.connectingLines(thickness: 0),
        entry: entry,
        child: Row(
          children: [
            FolderButton(
              visualDensity: density,
              padding: EdgeInsets.zero,
              icon: style.leaf,
              openedIcon: style.open,
              closedIcon: style.closed,
              isOpen: entry.hasChildren ? entry.isExpanded : null,
            ),
            details != null
                ? Tooltip(
                    message: details,
                    child: style.label(entry.node.label),
                  )
                : style.label(entry.node.label),
          ],
        ),
      ),
    );
  }
}

class TreeNodeStyle {
  const TreeNodeStyle({
    required this.leaf,
    required this.open,
    required this.closed,
    required this.label,
  });

  final Widget leaf;
  final Widget open;
  final Widget closed;
  final Widget Function(String) label;

  TreeNodeStyle copyWith({
    Widget? leaf,
    Widget? open,
    Widget? closed,
    Widget Function(String)? label,
  }) =>
      TreeNodeStyle(
        leaf: leaf ?? this.leaf,
        open: open ?? this.open,
        closed: closed ?? this.closed,
        label: label ?? this.label,
      );

  static TreeNodeStyle defaultStyle = TreeNodeStyle(
    leaf: const Icon(Icons.arrow_right),
    open: const Icon(Icons.arrow_drop_down),
    closed: const Icon(Icons.arrow_right),
    label: (text) => Text(text),
  );

  static TreeNodeStyle defaultStyleWithColor(Color color) => TreeNodeStyle(
        leaf: Icon(Icons.arrow_right, color: color),
        open: Icon(Icons.arrow_drop_down, color: color),
        closed: Icon(Icons.arrow_right, color: color),
        label: (text) => Text(text),
      );
}
