import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:identity_ui/api/api_client.dart';
import 'package:identity_ui/pages/default/components.dart';
import 'package:url_launcher/url_launcher.dart';

class Home extends StatelessWidget {
  const Home({super.key, required this.client});

  final ApiClient client;

  @override
  Widget build(BuildContext context) {
    final info = {
      'API': 'The server hosting the protected resources, capable of accepting and '
          'responding to protected resource requests using access tokens.',
      'Client': 'An application making protected resource requests on behalf of the resource '
          'owner and with its authorization.',
      'Resource Owner': 'An entity capable of granting access to a protected resource, '
          'usually a person (end-user).',
    };

    final rfcUri = Uri.parse('https://tools.ietf.org/html/rfc6749');

    final theme = Theme.of(context);

    return buildPage<bool>(
      of: () => client.ping(),
      builder: (context, _) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Column(
            children: [
              Wrap(
                children: info.keys.map((title) => _buildInfoCard(title, info[title]!)).toList(),
              ),
              const Padding(padding: EdgeInsets.all(8)),
              RichText(
                text: TextSpan(
                  children: [
                    TextSpan(
                      style: theme.textTheme.caption?.copyWith(fontStyle: FontStyle.italic),
                      text: 'For mode details, check the OAuth 2.0 RFC - ',
                    ),
                    TextSpan(
                        text: rfcUri.toString(),
                        style:
                            theme.textTheme.caption?.copyWith(color: theme.primaryColor, fontStyle: FontStyle.italic),
                        recognizer: TapGestureRecognizer()..onTap = () => launchUrl(rfcUri)),
                  ],
                ),
              )
            ],
          )
        ],
      ),
    );
  }

  Widget _buildInfoCard(String title, String content) {
    return Container(
      constraints: const BoxConstraints.tightFor(width: 400, height: 150),
      child: Card(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.only(left: 8, top: 12, right: 8),
              child: ListTile(
                leading: const Icon(Icons.info_outline),
                title: Text(title),
                subtitle: Text(content),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
