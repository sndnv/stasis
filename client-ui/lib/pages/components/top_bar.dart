import 'dart:io';

import 'package:flutter/foundation.dart' show kDebugMode;
import 'package:flutter/material.dart';
import 'package:stasis_client_ui/pages/page_destinations.dart';

class TopBar {
  static AppBar fromDestination(BuildContext context, PageRouterDestination currentDestination) {
    return fromTitle(context, currentDestination.info ?? currentDestination.title);
  }

  static AppBar fromTitle(BuildContext context, String title) {
    final theme = Theme.of(context);

    final mockEnabled = bool.tryParse(
      Platform.environment['STASIS_CLIENT_UI_MOCK_ENABLED'] ?? '',
      caseSensitive: false,
    );

    final List<Widget> mockMode;
    if (kDebugMode && mockEnabled == true) {
      final widget = Padding(
        padding: const EdgeInsets.only(left: 8.0),
        child: IconButton(
          tooltip: 'Mock Mode Enabled',
          onPressed: () {
            showDialog(
              context: context,
              builder: (_) => const SimpleDialog(
                title: Text('Mock Mode Enabled'),
                children: [
                  ListTile(
                    title: Text(
                      'The application is running in mock mode; there is no connection to a server '
                      'and many features will not work as expected.',
                    ),
                  ),
                ],
              ),
            );
          },
          icon: const Icon(Icons.block),
        ),
      );

      mockMode = [widget];
    } else {
      mockMode = [];
    }

    return AppBar(
      centerTitle: false,
      title: Row(
        children: <Widget>[
              RichText(
                text: TextSpan(
                  children: [
                    TextSpan(text: 'stasis\n', style: theme.textTheme.titleLarge),
                    TextSpan(text: title, style: theme.textTheme.bodyMedium),
                  ],
                ),
              ),
            ] +
            mockMode,
      ),
    );
  }
}
