import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/api/oauth.dart';
import 'package:server_ui/pages/page_destinations.dart';
import 'package:server_ui/pages/page_router.dart';

bool areParametersValid(List<String> required, Map<String, String> actual) {
  return actual.isNotEmpty &&
      required.every((parameter) {
        var parameterValue = actual[parameter];
        return parameterValue != null && parameterValue.trim().isNotEmpty;
      });
}

SvgPicture createLogo({double size = 48.0}) {
  return SvgPicture.asset(
    'logo.svg',
    width: size,
    height: size,
    fit: BoxFit.contain,
  );
}

Card createBasicCard(ThemeData theme, List<Widget> children) {
  return Card(
    color: theme.colorScheme.surfaceContainerHighest,
    child: Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: children.map((e) => Padding(padding: const EdgeInsets.all(4.0), child: e)).toList(),
          ),
        )
      ],
    ),
  );
}

Widget buildPage<T>({
  required Future<T> Function() of,
  required Widget Function(BuildContext context, T data) builder,
}) {
  return FutureBuilder<T>(
    future: of(),
    builder: (context, snapshot) {
      final theme = Theme.of(context);

      if (snapshot.data != null && snapshot.connectionState == ConnectionState.done) {
        return builder(context, snapshot.data as T);
      } else if (snapshot.error is AuthenticationFailure) {
        OAuth.discardCredentials().then((_) => PageRouter.navigateTo(context, destination: PageRouterDestination.home));
        return centerContent(
          content: [
            createBasicCard(
              theme,
              [errorInfo(title: 'Authentication Failed', description: 'Failed to authenticate; try to log in again.')],
            )
          ],
        );
      } else if (snapshot.error is AuthorizationFailure) {
        return centerContent(
          content: [
            createBasicCard(
              theme,
              [errorInfo(title: 'Not Authorized', description: 'Access to this resource is not allowed.')],
            )
          ],
        );
      } else if (snapshot.error != null) {
        return centerContent(
          content: [
            createBasicCard(
              theme,
              [errorInfo(title: 'Error', description: snapshot.error.toString())],
            )
          ],
        );
      } else {
        return const Center(child: CircularProgressIndicator());
      }
    },
  );
}

Widget centerContent({required List<Widget> content}) {
  return Scaffold(
    body: Center(
      child: Container(
        constraints: const BoxConstraints(minWidth: 400, maxWidth: 400),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: content,
        ),
      ),
    ),
  );
}

Widget errorInfo({required String title, required String description}) {
  return Center(
    child: ListTile(
      leading: const Icon(Icons.error),
      title: Text(title),
      subtitle: Text(description),
    ),
  );
}
