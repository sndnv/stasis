import 'package:stasis_client_ui/pages/page_destinations.dart';
import 'package:flutter/material.dart';

class TopBar {
  static AppBar fromDestination(BuildContext context, PageRouterDestination currentDestination) {
    return fromTitle(context, currentDestination.info ?? currentDestination.title);
  }

  static AppBar fromTitle(BuildContext context, String title) {
    final theme = Theme.of(context);

    return AppBar(
      centerTitle: false,
      title: RichText(
        text: TextSpan(
          children: [
            TextSpan(text: 'stasis\n', style: theme.textTheme.titleLarge),
            TextSpan(text: title, style: theme.textTheme.bodyMedium),
          ],
        ),
      ),
    );
  }
}
