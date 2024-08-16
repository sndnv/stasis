import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/model/datasets/dataset_metadata_search_result.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/extensions.dart';
import 'package:stasis_client_ui/pages/components/forms/date_time_field.dart';
import 'package:stasis_client_ui/pages/components/rendering.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:flutter/material.dart';

class Search extends StatefulWidget {
  const Search({
    super.key,
    required this.client,
  });

  final ClientApi client;

  @override
  State createState() {
    return _SearchState();
  }
}

class _SearchState extends State<Search> {
  final _key = GlobalKey<FormState>();

  final TextEditingController _searchController = TextEditingController();
  final DateTimeController _untilController = DateTimeController();

  DatasetMetadataSearchResult? _searchResult;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    void searchHandler() {
      if (_key.currentState?.validate() ?? false) {
        widget.client
            .searchDatasetMetadata(searchQuery: _searchController.text.trim(), until: _untilController.value)
            .then((result) => setState(() => _searchResult = result))
            .onError((e, stackTrace) {
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Search failed: [$e]')));
          }
        });
      }
    }

    final searchField = TextFormField(
      decoration: const InputDecoration(labelText: 'Search'),
      controller: _searchController,
      validator: (value) => (value?.trim().isEmpty ?? true) ? 'Search query cannot be empty' : null,
      onFieldSubmitted: (_) => searchHandler(),
    );

    final label = Row(
      mainAxisSize: MainAxisSize.max,
      children: [
        Padding(
          padding: const EdgeInsets.only(top: 12.0),
          child: Text(
            'Until',
            style: theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w500),
            textAlign: TextAlign.left,
          ),
        ),
      ],
    );

    final untilField = DateTimeField(controller: _untilController);

    final submitButton = ElevatedButton(
      onPressed: () => searchHandler(),
      child: const Text('SEARCH'),
    );

    List<Widget> searchResults = [];
    if (_searchResult != null) {
      final results = (_searchResult?.definitions.entries ?? []).fold(<Pair<String, DatasetDefinitionResult>>[],
          (collected, e) => e.value != null ? collected + [Pair(e.key, e.value!)] : collected);

      const divider = Padding(padding: EdgeInsets.fromLTRB(16.0, 8.0, 16.0, 4.0), child: Divider());

      final defaultStyle = theme.textTheme.bodyMedium;
      final mediumBold = defaultStyle?.copyWith(fontWeight: FontWeight.bold);
      final mediumItalic = defaultStyle?.copyWith(fontStyle: FontStyle.italic);
      final smallBold = theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.bold);

      if (results.isNotEmpty) {
        searchResults = [
          divider,
          ListView(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            children: results.map((definitionResult) {
              final definition = definitionResult.a;
              final result = definitionResult.b;

              final title = RichText(
                text: TextSpan(
                  children: [
                    TextSpan(text: result.definitionInfo, style: mediumBold),
                    TextSpan(text: ' (', style: defaultStyle),
                    TextSpan(text: definition.toMinimizedString(), style: mediumItalic),
                    TextSpan(text: ')', style: defaultStyle),
                  ],
                ),
              );

              final subtitle = RichText(
                text: TextSpan(
                  children: [
                    TextSpan(
                      text: ' ${result.entryCreated.renderAsDate()}, ${result.entryCreated.renderAsTime()}',
                      style: mediumBold,
                    ),
                    TextSpan(text: ' (', style: defaultStyle),
                    TextSpan(text: result.entryId.toMinimizedString(), style: mediumItalic),
                    TextSpan(text: ')', style: defaultStyle),
                  ],
                ),
              );

              final children = result.matches.entries.map((e) {
                final split = e.key.toSplitPath();
                final parent = split.a;
                final name = split.b;

                final title = Text(name, style: const TextStyle(fontWeight: FontWeight.bold));

                final subtitle = RichText(
                  text: TextSpan(
                    children: [TextSpan(text: '$parent\n', style: theme.textTheme.bodySmall)],
                  ),
                );

                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4.0),
                  child: ListTile(
                    leading: e.value.entityIcon(),
                    title: title,
                    subtitle: subtitle,
                    visualDensity: VisualDensity.compact,
                    onTap: () {
                      confirmationDialog(
                        context,
                        title: 'Recover file [$name]?',
                        content: RichText(
                          text: TextSpan(
                            children: [
                              TextSpan(text: 'File saved on ', style: theme.textTheme.bodySmall),
                              TextSpan(text: result.entryCreated.renderAsDate(), style: smallBold),
                              TextSpan(text: ' will be recovered as ', style: theme.textTheme.bodySmall),
                              TextSpan(text: e.key, style: smallBold),
                              TextSpan(text: '.', style: theme.textTheme.bodySmall),
                            ],
                          ),
                        ),
                        onConfirm: () {
                          final operation = widget.client.recoverFrom(
                            definition: definition,
                            entry: result.entryId,
                            pathQuery: RegExp.escape(e.key),
                            destination: null,
                            discardPaths: null,
                          );

                          final messenger = ScaffoldMessenger.of(context);

                          operation.then((_) {
                            messenger.showSnackBar(const SnackBar(content: Text('Recovery started...')));
                          }).onError((e, stackTrace) {
                            messenger.showSnackBar(SnackBar(content: Text('Failed to start recovery: [$e]')));
                          }).whenComplete(() {
                            if (context.mounted) Navigator.pop(context);
                          });
                        },
                      );
                    },
                  ),
                );
              }).toList();

              return ExpansionTile(
                title: title,
                subtitle: subtitle,
                children: children,
              );
            }).toList(),
          )
        ];
      } else {
        searchResults = [
          divider,
          RichText(
            text: TextSpan(
              children: [
                TextSpan(text: 'No results found for ', style: defaultStyle),
                TextSpan(text: _searchController.text.trim(), style: mediumBold),
              ],
            ),
          ),
        ];
      }
    }

    return boxed(
      context,
      child: Form(
        key: _key,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: Column(
          mainAxisSize: MainAxisSize.max,
          mainAxisAlignment: MainAxisAlignment.start,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
                Card(
                  margin: const EdgeInsets.all(16.0),
                  child: Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: Column(
                      children: [
                        searchField,
                        label,
                        untilField,
                      ],
                    ),
                  ),
                ),
                submitButton,
              ] +
              searchResults,
        ),
      ),
    );
  }
}
