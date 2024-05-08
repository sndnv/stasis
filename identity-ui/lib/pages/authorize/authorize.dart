import 'package:flutter/material.dart';
import 'package:identity_ui/api/oauth.dart';
import 'package:identity_ui/pages/authorize/derived_passwords.dart';
import 'package:identity_ui/pages/default/components.dart';
import 'package:identity_ui/pages/authorize/credentials_form.dart';
import 'package:shared_preferences/shared_preferences.dart';

class Authorize extends StatelessWidget {
  Authorize({
    super.key,
    required this.authorizationEndpoint,
    required this.oauthConfig,
    required this.passwordDerivationConfig,
    required this.prefs,
  });
  final Uri authorizationEndpoint;
  final OAuthConfig oauthConfig;
  final UserAuthenticationPasswordDerivationConfig passwordDerivationConfig;
  final SharedPreferences prefs;

  final List<String> _requiredParameters = ['response_type', 'client_id', 'redirect_uri', 'scope', 'state'];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final queryParameters = Uri.base.queryParameters;

    final logo = createLogo();

    Card card;
    if (areParametersValid(_requiredParameters, queryParameters)) {
      final title = Text('Login', style: theme.textTheme.headlineSmall);

      const divider = Divider(indent: 15.0, endIndent: 15.0);

      final requestedScopes = (queryParameters['scope'] ?? 'none').split(' ');

      final actualScopes = requestedScopes.map((scope) => scope.replaceAll('urn:stasis:identity:audience:', ''));

      final scopes = Text(
        'Authorization requested for [${actualScopes.join(', ')}]',
        style: theme.textTheme.bodySmall?.copyWith(fontStyle: FontStyle.italic),
      );

      final form = CredentialsForm(
        authorizationEndpoint: authorizationEndpoint,
        requestedScopes: requestedScopes,
        oauthConfig: oauthConfig,
        passwordDerivationConfig: passwordDerivationConfig,
        prefs: prefs,
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
