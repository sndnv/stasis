import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/config/app_files.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/model/operations/rule.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/dataset_definition_specification.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/top_bar.dart';
import 'package:stasis_client_ui/utils/pair.dart';

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
    return buildPage<Pair<List<DatasetDefinition>, Map<String, List<Rule>>>>(
      of: () => client.getBackupRules().then((rules) {
        return client.getDatasetDefinitions().then((definitions) => Pair(definitions, rules));
      }),
      builder: (context, result) {
        final theme = Theme.of(context);

        final mediumBold = theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold);
        final mediumItalic = theme.textTheme.bodyMedium?.copyWith(fontStyle: FontStyle.italic);

        final Map<String, DatasetDefinition> definitions = Map.fromIterable(result.a, key: (d) => d.id);
        final Map<String, List<Rule>> rulesPerDefinition = result.b;

        late final ListTile noRulesTile = ListTile(
          title: Text('No rules found', style: mediumItalic, textAlign: TextAlign.center),
        );

        late final List<ExpansionTile> tiles = rulesPerDefinition.entries.map((e) {
          final definitionId = e.key;
          final definition = definitions[definitionId];
          final rules = List.from(e.value)..sort((a, b) => a.original.lineNumber.compareTo(b.original.lineNumber));

          final Widget title;
          if (definitionId.toLowerCase() == 'default') {
            title = Text('Default', style: mediumBold);
          } else if (definition != null) {
            title = RichText(
              text: TextSpan(
                children: [
                  TextSpan(text: definition.info, style: mediumBold),
                  TextSpan(text: ' (', style: theme.textTheme.bodyMedium),
                  TextSpan(text: definition.id.toMinimizedString(), style: mediumItalic),
                  TextSpan(text: ')', style: theme.textTheme.bodyMedium),
                ],
              ),
            );
          } else {
            title = Text(definitionId, style: mediumBold);
          }

          final List<Widget> tiles;
          if (rules.isNotEmpty) {
            final ruleTiles = rules.map((rule) {
              final icon = rule.operation == 'include'
                  ? const Icon(Icons.check_circle_outline, color: Colors.green)
                  : const Icon(Icons.highlight_off_outlined, color: Colors.red);

              return ListTile(
                dense: true,
                contentPadding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 0.0),
                visualDensity: VisualDensity.compact,
                leading: icon,
                title: Text(rule.directory, style: theme.textTheme.titleSmall),
                subtitle: Text(rule.pattern),
              );
            }).toList();

            late final ListTile showSpecificationTile = ListTile(
              dense: true,
              contentPadding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 0.0),
              visualDensity: VisualDensity.compact,
              title: const Text('Show Files', textAlign: TextAlign.center),
              onTap: () {
                final title =
                    definition != null ? '${definition.info} (${definition.id.toMinimizedString()})' : 'Default';

                Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => Scaffold(
                      appBar: TopBar.fromTitle(context, 'Backup files specification - $title'),
                      body: DatasetDefinitionSpecification(definition: definitionId, client: client),
                    ),
                    fullscreenDialog: true,
                  ),
                );
              },
            );

            late final ListTile missingDefinitionTile = ListTile(
              dense: true,
              contentPadding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 0.0),
              visualDensity: VisualDensity.compact,
              title: Text('No dataset definition found', textAlign: TextAlign.center, style: mediumItalic),
            );

            final List<ListTile> extraTiles = definitionId.toLowerCase() == 'default' || definition != null
                ? [showSpecificationTile]
                : [missingDefinitionTile];

            tiles = ruleTiles + extraTiles;
          } else {
            tiles = [noRulesTile];
          }

          return ExpansionTile(
            initiallyExpanded: rulesPerDefinition.length == 1,
            title: title,
            children: tiles,
          );
        }).toList();

        return boxed(
          context,
          child: Card(
            margin: const EdgeInsets.all(16.0),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.max,
                mainAxisAlignment: MainAxisAlignment.start,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: tiles.isNotEmpty ? tiles : [noRulesTile],
              ),
            ),
          ),
        );
      },
    );
  }
}
