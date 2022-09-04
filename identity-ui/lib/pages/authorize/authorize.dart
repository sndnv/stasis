import 'package:flutter/material.dart';
import 'package:identity_ui/pages/default/components.dart';
import 'package:identity_ui/pages/authorize/credentials_form.dart';

class Authorize extends StatelessWidget {
  Authorize({Key? key, required this.authorizationEndpoint}) : super(key: key);
  final Uri authorizationEndpoint;

  final List<String> _requiredParameters = ['response_type', 'client_id', 'redirect_uri', 'scope', 'state'];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final queryParameters = Uri.base.queryParameters;

    final logo = createLogo();

    Card card;
    if (areParametersValid(_requiredParameters, queryParameters)) {
      final title = Text('Login', style: theme.textTheme.headlineSmall);

      final form = CredentialsForm(authorizationEndpoint: authorizationEndpoint);

      const divider = Divider(indent: 15.0, endIndent: 15.0);

      final actualScopes =
          (queryParameters['scope'] ?? 'none').replaceAll('urn:stasis:identity:audience:', '').split(' ');

      final scopes = Text(
        'Authorization requested for [${actualScopes.join(', ')}]',
        style: theme.textTheme.caption?.copyWith(fontStyle: FontStyle.italic),
      );

      card = createBasicCard(theme, [title, form, divider, scopes]);
    } else {
      const error = ListTile(
        leading: Icon(Icons.error),
        title: Text('Invalid Request'),
        subtitle: Text('One or more required parameters were not provided.\n\n'
            'This can happen if you navigated to this page manually or due to a '
            'misconfigured authentication and authorization process.\n\nContact '
            'your system administrator for more information.'),
      );

      card = createBasicCard(theme, [error]);
    }

    return centerContent(
      content: [card, Padding(padding: const EdgeInsets.all(16.0), child: logo)],
    );
  }
}
