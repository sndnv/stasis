import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:identity_ui/pages/page_router.dart';

class NotFound extends StatelessWidget {
  const NotFound({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('404', style: theme.textTheme.displayLarge),
            Text('Page Not Found', style: theme.textTheme.displaySmall),
            Text('The requested resource could not be found.', style: theme.textTheme.titleLarge),
            RichText(
              text: TextSpan(
                  text: 'Go Home',
                  style: theme.textTheme.bodyLarge?.copyWith(color: theme.primaryColor, fontStyle: FontStyle.italic),
                  recognizer: TapGestureRecognizer()
                    ..onTap = () => PageRouter.navigateTo(context, destination: PageRouterDestination.home)),
            ),
          ].map((e) => Padding(padding: const EdgeInsets.all(4.0), child: e)).toList(),
        ),
      ),
    );
  }
}
