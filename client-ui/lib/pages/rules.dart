import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/config/app_files.dart';
import 'package:stasis_client_ui/model/operations/specification_rules.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/file_tree.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:flutter/material.dart';

class Rules extends StatelessWidget {
  const Rules({
    super.key,
    required this.client,
    required this.files,
  });

  final ClientApi client;
  final AppFiles files;

  @override
  Widget build(BuildContext context) {
    return buildPage<SpecificationRules>(
      of: () => client.getBackupRules(),
      builder: (context, rules) {
        final theme = Theme.of(context);

        final includedStyle = TreeNodeStyle.defaultStyleWithColor(Colors.green).copyWith(
          leaf: const Icon(Icons.add, color: Colors.green),
        );

        final excludedStyle = TreeNodeStyle.defaultStyleWithColor(Colors.red).copyWith(
          leaf: const Icon(Icons.remove, color: Colors.red),
        );

        final tree = FileTree.fromPaths(
          paths: rules.included + rules.excluded,
          pathStyle: (path) {
            if (rules.included.contains(path)) {
              return includedStyle;
            } else if (rules.excluded.contains(path)) {
              return excludedStyle;
            } else {
              return TreeNodeStyle.defaultStyle;
            }
          },
          pathDetails: (path) {
            return rules.explanation[path]
                ?.map((e) => '${e.operation.capitalize()} on line ${e.original.lineNumber}: [${e.original.line}]')
                .join('\n');
          },
        );

        final matched = Card(
          child: Column(
            children: [
              ListTile(
                title: Text(
                  'Matched (${rules.included.length + rules.excluded.length})',
                  style: theme.textTheme.titleMedium,
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(24.0, 0.0, 24.0, 8.0),
                child: tree,
              ),
            ],
          ),
        );

        final unmatched = Card(
          child: Column(
            children: [
              ListTile(
                leading: Icon(Icons.warning_amber, color: theme.colorScheme.error),
                title: Text('Unmatched (${rules.unmatched.length})', style: theme.textTheme.titleMedium),
              ),
              ListView(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                children: rules.unmatched.map((unmatched) {
                  final original = unmatched.a;
                  final error = unmatched.b;

                  return ListTile(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 24.0),
                    visualDensity: VisualDensity.compact,
                    title: Text(error, style: theme.textTheme.titleSmall?.copyWith(color: theme.colorScheme.error)),
                    subtitle: Text('Line ${original.lineNumber}: [${original.line}]'),
                  );
                }).toList(),
              ),
            ],
          ),
        );

        final configFile = files.paths.rules.toSplitPath();

        final showConfigFile = FloatingActionButton.small(
          heroTag: null,
          onPressed: () {
            showFileContentDialog(
              context,
              name: configFile.b,
              parentDirectory: configFile.a,
              content: Text(files.rules.isNotEmpty ? files.rules.join('\n') : 'none'),
            );
          },
          tooltip: 'Show config file',
          child: const Icon(Icons.file_open_outlined),
        );

        return Stack(
          children: [
            Align(
              alignment: Alignment.topCenter,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.max,
                  mainAxisAlignment: MainAxisAlignment.start,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: rules.unmatched.isEmpty ? [matched] : [unmatched, matched],
                ),
              ),
            ),
            Align(
              alignment: Alignment.bottomRight,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: showConfigFile,
              ),
            ),
          ],
        );
      },
    );
  }
}
