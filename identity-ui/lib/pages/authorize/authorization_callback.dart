import 'dart:html';

import 'package:flutter/material.dart';
import 'package:http/http.dart';
import 'package:identity_ui/api/oauth.dart';
import 'package:identity_ui/pages/default/components.dart';
import 'package:identity_ui/pages/page_router.dart';

class AuthorizationCallback extends StatelessWidget {
  AuthorizationCallback({super.key, required this.config});
  final OAuthConfig config;

  final List<String> _requiredParameters = ['code', 'state'];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final queryParameters = Uri.base.queryParameters;
    final error = queryParameters['error'];

    final logo = createLogo();

    Card card;
    if (error != null) {
      String errorText;

      final words = error.split('_').map((w) => w.trim()).where((w) => w.isNotEmpty);
      if (words.length > 1) {
        errorText = words.map((w) => '${w[0].toUpperCase()}${w.substring(1)}').join(' ');
      } else {
        errorText = error;
      }

      final content = ListTile(
        leading: const Icon(Icons.error),
        title: Text(errorText),
        subtitle: Text(queryParameters['error_description'] ?? 'No error description available.'),
      );

      card = createBasicCard(theme, [content]);
    } else if (areParametersValid(_requiredParameters, queryParameters)) {
      Widget content = FutureBuilder<Client?>(
        future: OAuth.handleAuthorizationResponse(config, Uri.base.queryParameters),
        builder: (context, snapshot) {
          if (snapshot.data != null && snapshot.connectionState == ConnectionState.done) {
            window.location.assign('${Uri.base.origin}/${PageRouterDestination.home.route}');

            return const ListTile(
              leading: Icon(Icons.error),
              title: Text('Processing Complete'),
              subtitle: Text('Redirecting ...'),
            );
          } else if (snapshot.error != null) {
            return ListTile(
              leading: const Icon(Icons.error),
              title: const Text('Error'),
              subtitle: Text(snapshot.error!.toString()),
            );
          } else {
            return const ListTile(
              title: Text('Processing'),
              subtitle: Text('Your request is being processed.'),
            );
          }
        },
      );

      card = createBasicCard(theme, [content]);
    } else {
      const content = ListTile(
        leading: Icon(Icons.error),
        title: Text('Invalid Request'),
        subtitle: Text('One or more required parameters were not provided.\n\n'
            'This can happen if you navigated to this page manually or due to a '
            'misconfigured authentication and authorization process.\n\nContact '
            'your system administrator for more information.'),
      );

      card = createBasicCard(theme, [content]);
    }

    return centerContent(
      content: [card, Padding(padding: const EdgeInsets.all(16.0), child: logo)],
    );
  }
}
