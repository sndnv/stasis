import 'package:flutter/material.dart';
import 'package:identity_ui/api/api_client.dart';
import 'package:identity_ui/model/stored_authorization_code.dart';
import 'package:identity_ui/pages/default/components.dart';
import 'package:identity_ui/pages/manage/components/entity_table.dart';
import 'package:identity_ui/pages/manage/components/rendering.dart';

class Codes extends StatefulWidget {
  const Codes({super.key, required this.client});

  final ApiClient client;

  @override
  State createState() {
    return _CodesState();
  }
}

class _CodesState extends State<Codes> {
  @override
  Widget build(BuildContext context) {
    return buildPage<List<StoredAuthorizationCode>>(
      of: () => widget.client.getCodes(),
      builder: (context, codes) {
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            EntityTable<StoredAuthorizationCode>(
              entities: codes..sort((a, b) => a.owner.compareTo(b.owner)),
              actions: const [],
              header: const Text('Authorization Codes'),
              columns: const [
                DataColumn(label: Text('Code')),
                DataColumn(label: Text('Client')),
                DataColumn(label: Text('Resource Owner')),
                DataColumn(label: Text('Scope')),
                DataColumn(label: Text('Created')),
                DataColumn(label: Text('')),
              ],
              entityToRow: (e) {
                StoredAuthorizationCode code = e;
                return [
                  DataCell(Text(code.code)),
                  DataCell(Text(code.client)),
                  DataCell(Text(code.owner)),
                  DataCell(Text(code.scope ?? '-')),
                  DataCell(Text(code.created.render())),
                  DataCell(
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        IconButton(
                          tooltip: 'Remote Authorization Code',
                          onPressed: () => _removeCode(code.code),
                          icon: const Icon(Icons.delete),
                        ),
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

  void _removeCode(String code) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove authorization code [$code]?'),
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

                widget.client.deleteCode(code).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Authorization code removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove authorization code: [$e]')));
                }).whenComplete(() {
                  if (context.mounted) Navigator.pop(context);
                });
              },
              child: const Text('Remove'),
            ),
          ],
        );
      },
    );
  }
}
