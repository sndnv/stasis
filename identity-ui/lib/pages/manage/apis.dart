import 'package:flutter/material.dart';
import 'package:identity_ui/api/api_client.dart';
import 'package:identity_ui/model/api.dart';
import 'package:identity_ui/model/requests/create_api.dart';
import 'package:identity_ui/pages/default/components.dart';
import 'package:identity_ui/pages/manage/components/entity_form.dart';
import 'package:identity_ui/pages/manage/components/entity_table.dart';

class Apis extends StatefulWidget {
  const Apis({super.key, required this.client});

  final ApiClient client;

  @override
  State createState() {
    return _ApisState();
  }
}

class _ApisState extends State<Apis> {
  @override
  Widget build(BuildContext context) {
    return buildPage<List<Api>>(
      of: () => widget.client.getApis(),
      builder: (context, apis) {
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            EntityTable<Api>(
              entities: apis..sort((a, b) => a.id.compareTo(b.id)),
              actions: [
                IconButton(
                  tooltip: 'Create New API',
                  onPressed: () => _createApi(context),
                  icon: const Icon(Icons.add),
                ),
              ],
              header: const Text('APIs'),
              columns: const [
                DataColumn(label: Text('ID')),
                DataColumn(label: Text('')),
              ],
              entityToRow: (api) {
                return [
                  DataCell(Text(api.id)),
                  DataCell(
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        IconButton(
                          tooltip: widget.client.audience == api.id ? 'Cannot remove the current API' : 'Remove API',
                          onPressed: widget.client.audience == api.id ? null : () => _removeApi(context, api.id),
                          icon: const Icon(Icons.delete),
                        )
                      ],
                    ),
                  ),
                ];
              },
            ),
          ],
        );
      },
    );
  }

  void _createApi(BuildContext context) async {
    final idField = formField(
      title: 'ID',
      errorMessage: 'API ID cannot be empty',
      controller: TextEditingController(),
    );

    showDialog(
      context: context,
      builder: (_) {
        return SimpleDialog(
          title: const Text('Create New API'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [idField],
              submitAction: 'Create',
              onFormSubmitted: () {
                final request = CreateApi(id: idField.controller!.text.trim());

                final messenger = ScaffoldMessenger.of(context);

                widget.client.postApi(request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('API created...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to create API: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _removeApi(BuildContext context, String id) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove API [$id]?'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
              },
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                final messenger = ScaffoldMessenger.of(context);

                widget.client.deleteApi(id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('API removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove API: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
              child: const Text('Remove'),
            ),
          ],
        );
      },
    );
  }
}
