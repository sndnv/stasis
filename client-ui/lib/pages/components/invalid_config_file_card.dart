import 'package:flutter/material.dart';
import 'package:stasis_client_ui/config/config.dart';
import 'package:stasis_client_ui/pages/common/components.dart';

class InvalidConfigFileCard {
  static Widget build(BuildContext context, InvalidConfigFileException e) {
    final theme = Theme.of(context);

    return createBasicCard(
      theme,
      [
        Text('Invalid Client Configuration', style: theme.textTheme.headlineSmall),
        RichText(
          text: TextSpan(
            children: [
              TextSpan(text: 'Configuration ', style: theme.textTheme.bodyMedium),
              WidgetSpan(
                alignment: PlaceholderAlignment.middle,
                child: Tooltip(
                  message: e.path,
                  child: Text(
                    'file',
                    style: theme.textTheme.bodyMedium?.copyWith(decoration: TextDecoration.underline),
                  ),
                ),
              ),
              TextSpan(text: ' is not valid', style: theme.textTheme.bodyMedium)
            ],
          ),
        ),
        const Divider(),
        SelectionArea(
          child: Text(
            'Error: ${e.message}',
            style: theme.textTheme.bodySmall,
            textAlign: TextAlign.center,
          ),
        ),
      ],
    );
  }
}
