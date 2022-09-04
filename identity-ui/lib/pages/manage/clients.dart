import 'package:flutter/material.dart';
import 'package:identity_ui/api/api_client.dart';
import 'package:identity_ui/model/client.dart';
import 'package:identity_ui/model/requests/create_client.dart';
import 'package:identity_ui/model/requests/update_client.dart';
import 'package:identity_ui/model/requests/update_client_credentials.dart';
import 'package:identity_ui/pages/default/components.dart';
import 'package:identity_ui/pages/manage/components/entity_form.dart';
import 'package:identity_ui/pages/manage/components/entity_table.dart';

class Clients extends StatefulWidget {
  const Clients({super.key, required this.client});

  final ApiClient client;

  @override
  State createState() {
    return _ClientsState();
  }
}

class _ClientsState extends State<Clients> {
  @override
  Widget build(BuildContext context) {
    return buildPage<List<Client>>(
      of: () => widget.client.getClients(),
      builder: (context, clients) {
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            EntityTable<Client>(
              entities: clients..sort((a, b) => a.id.compareTo(b.id)),
              actions: [
                IconButton(
                  tooltip: 'Create New Client',
                  onPressed: () => _createClient(context),
                  icon: const Icon(Icons.add),
                ),
              ],
              header: const Text('Clients'),
              columns: const [
                DataColumn(label: Text('ID')),
                DataColumn(label: Text('Redirect URI')),
                DataColumn(label: Text('Subject')),
                DataColumn(label: Text('Token Expiration')),
                DataColumn(label: Text('Active')),
                DataColumn(label: Text('')),
              ],
              entityToRow: (client) => [
                DataCell(
                  client.active
                      ? Text(client.id)
                      : Row(
                          children: [
                            Text(client.id),
                            const Padding(padding: EdgeInsets.symmetric(horizontal: 4)),
                            const IconButton(
                              tooltip: 'Deactivated client',
                              onPressed: null,
                              icon: Icon(Icons.warning),
                            )
                          ],
                        ),
                ),
                DataCell(Text(client.redirectUri)),
                DataCell(Text(client.subject ?? '-')),
                DataCell(Text('${client.tokenExpiration} s')),
                DataCell(Text(client.active ? 'Yes' : 'No')),
                DataCell(
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      IconButton(
                        tooltip:
                            widget.client.clientId == client.id ? 'Cannot update the current client' : 'Update Client',
                        onPressed: widget.client.clientId == client.id ? null : () => _editClient(context, client),
                        icon: const Icon(Icons.edit),
                      ),
                      IconButton(
                        tooltip: 'Update Client Credentials',
                        onPressed: () => _editClientCredentials(context, client),
                        icon: const Icon(Icons.lock_reset),
                      ),
                      IconButton(
                        tooltip:
                            widget.client.clientId == client.id ? 'Cannot remove the current client' : 'Remove Client',
                        onPressed: widget.client.clientId == client.id ? null : () => _removeClient(context, client.id),
                        icon: const Icon(Icons.delete),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  void _createClient(BuildContext context) async {
    final redirectUriField = formField(
      title: 'Redirect URI',
      errorMessage: 'Redirect URI cannot be empty',
      controller: TextEditingController(),
    );

    final tokenExpirationField = formField(
      title: 'Token Expiration (seconds)',
      errorMessage: 'Token expiration is required',
      type: TextInputType.number,
      controller: TextEditingController(),
    );

    final rawSecretField = formField(
      title: 'Secret',
      secret: true,
      errorMessage: 'Secret cannot be empty',
      controller: TextEditingController(),
    );

    final subjectField = formField(
      title: 'Subject',
      controller: TextEditingController(),
    );

    showDialog(
      context: context,
      builder: (_) {
        return SimpleDialog(
          title: const Text('Create New Client'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [
                redirectUriField,
                tokenExpirationField,
                rawSecretField,
                subjectField,
              ],
              submitAction: 'Create',
              onFormSubmitted: () {
                final subject = subjectField.controller!.text.trim();

                final request = CreateClient(
                  redirectUri: redirectUriField.controller!.text.trim(),
                  tokenExpiration: int.parse(tokenExpirationField.controller!.text.trim()),
                  rawSecret: rawSecretField.controller!.text.trim(),
                  subject: subject.isEmpty ? null : subject,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.client.postClient(request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Client created...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to create client: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _editClient(BuildContext context, Client existing) {
    final tokenExpirationField = formField(
      title: 'Token Expiration (seconds)',
      errorMessage: 'Token expiration is required',
      type: TextInputType.number,
      controller: TextEditingController(text: existing.tokenExpiration.toString()),
    );

    bool currentActiveState = existing.active;
    final activeField = StateField(
      title: 'State',
      initialState: existing.active,
      onStateUpdated: (updated) => currentActiveState = updated,
    );

    showDialog(
      context: context,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update Client [${existing.id.asShortId()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [
                tokenExpirationField,
                activeField,
              ],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateClient(
                  tokenExpiration: int.parse(tokenExpirationField.controller!.text.trim()),
                  active: currentActiveState,
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.client.putClient(existing.id, request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Client updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update client: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _editClientCredentials(BuildContext context, Client existing) {
    final rawSecretField = formField(
      title: 'Secret',
      secret: true,
      errorMessage: 'Secret cannot be empty',
      controller: TextEditingController(),
    );

    showDialog(
      context: context,
      builder: (context) {
        return SimpleDialog(
          title: Text('Update Client Credentials [${existing.id.asShortId()}]'),
          contentPadding: const EdgeInsets.all(16),
          children: [
            EntityForm(
              fields: [rawSecretField],
              submitAction: 'Update',
              onFormSubmitted: () {
                final request = UpdateClientCredentials(
                  rawSecret: rawSecretField.controller!.text.trim(),
                );

                final messenger = ScaffoldMessenger.of(context);

                widget.client.putClientCredentials(existing.id, request).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Client credentials updated...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to update client credentials: [$e]')));
                }).whenComplete(() => Navigator.pop(context));
              },
            )
          ],
        );
      },
    );
  }

  void _removeClient(BuildContext context, String id) {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Remove client [${id.asShortId()}]?'),
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

                widget.client.deleteClient(id).then((_) {
                  messenger.showSnackBar(const SnackBar(content: Text('Client removed...')));
                  setState(() {});
                }).onError((e, stackTrace) {
                  messenger.showSnackBar(SnackBar(content: Text('Failed to remove client: [$e]')));
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

extension ExtendedUuidString on String {
  String asShortId() {
    return split('-').first;
  }
}
