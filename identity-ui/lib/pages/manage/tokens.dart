import 'package:flutter/material.dart';
import 'package:identity_ui/api/api_client.dart';
import 'package:identity_ui/model/stored_refresh_token.dart';
import 'package:identity_ui/pages/default/components.dart';
import 'package:identity_ui/pages/manage/components/entity_table.dart';
import 'package:identity_ui/pages/manage/components/rendering.dart';

class Tokens extends StatefulWidget {
  const Tokens({super.key, required this.client});

  final ApiClient client;

  @override
  State createState() {
    return _TokensState();
  }
}

class _TokensState extends State<Tokens> {
  @override
  Widget build(BuildContext context) {
    return buildPage<List<StoredRefreshToken>>(
      of: () => widget.client.getTokens(),
      builder: (context, tokens) {
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            EntityTable<StoredRefreshToken>(
              entities: tokens..sort((a, b) => a.created.compareTo(b.created)),
              actions: const [],
              header: const Text('Refresh Tokens'),
              columns: const [
                DataColumn(label: Text('Token')),
                DataColumn(label: Text('Client')),
                DataColumn(label: Text('Resource Owner')),
                DataColumn(label: Text('Scope')),
                DataColumn(label: Text('Expiration')),
                DataColumn(label: Text('Created')),
                DataColumn(label: Text('')),
              ],
              entityToRow: (e) {
                StoredRefreshToken token = e;
                return [
                  DataCell(Text(token.token.substring(0, 16))),
                  DataCell(Text(token.client)),
                  DataCell(Text(token.owner)),
                  DataCell(Text(token.scope?.split(':audience:').last ?? '-')),
                  DataCell(Text(token.expiration?.render() ?? '-')),
                  DataCell(Text(token.created.render())),
                  DataCell(
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        IconButton(
                          tooltip: 'Remove Refresh Token',
                          onPressed: () => _removeToken(token.token),
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

  void _removeToken(String token) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove refresh token [${token.substring(0, 16)}]?'),
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

                widget.client.deleteToken(token).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Refresh token removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove refresh token: [$e]')));
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
