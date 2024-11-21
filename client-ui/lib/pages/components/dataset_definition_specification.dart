import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/operations/specification_rules.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/file_tree.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';

class DatasetDefinitionSpecification extends StatelessWidget {
  const DatasetDefinitionSpecification({
    super.key,
    required this.client,
    required this.definition,
  });

  final ClientApi client;
  final String definition;

  @override
  Widget build(BuildContext context) {
    return buildPage<SpecificationRules>(
      of: () => client.getBackupSpecification(definition: definition),
      builder: (context, spec) {
        final theme = Theme.of(context);

        final includedStyle = TreeNodeStyle.defaultStyleWithColor(Colors.green).copyWith(
          leaf: const Icon(Icons.add, color: Colors.green),
        );

        final excludedStyle = TreeNodeStyle.defaultStyleWithColor(Colors.red).copyWith(
          leaf: const Icon(Icons.remove, color: Colors.red),
        );

        final tree = FileTree.fromPaths(
          paths: spec.included + spec.excluded,
          pathStyle: (path) {
            if (spec.included.contains(path)) {
              return includedStyle;
            } else if (spec.excluded.contains(path)) {
              return excludedStyle;
            } else {
              return TreeNodeStyle.defaultStyle;
            }
          },
          pathDetails: (path) {
            return spec.explanation[path]
                ?.map((e) => '${e.operation.capitalize()} on line ${e.original.lineNumber}: [${e.original.line}]')
                .join('\n');
          },
        );

        final matched = Card(
          child: Column(
            children: [
              ListTile(
                title: Text(
                  'Matched (${spec.included.length + spec.excluded.length})',
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
                title: Text('Unmatched (${spec.unmatched.length})', style: theme.textTheme.titleMedium),
              ),
              ListView(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                children: spec.unmatched.map((unmatched) {
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

        return SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.max,
            mainAxisAlignment: MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: spec.unmatched.isEmpty ? [matched] : [unmatched, matched],
          ),
        );
      },
    );
  }
}
