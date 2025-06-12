import 'dart:io';

import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/config/app_files.dart';
import 'package:stasis_client_ui/pages/about.dart';
import 'package:stasis_client_ui/pages/backup.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/top_bar.dart';
import 'package:stasis_client_ui/pages/home.dart';
import 'package:stasis_client_ui/pages/operations.dart';
import 'package:stasis_client_ui/pages/page_destinations.dart';
import 'package:stasis_client_ui/pages/recover.dart';
import 'package:stasis_client_ui/pages/rules.dart';
import 'package:stasis_client_ui/pages/schedules.dart';
import 'package:stasis_client_ui/pages/search.dart';
import 'package:stasis_client_ui/pages/settings.dart';
import 'package:stasis_client_ui/pages/status.dart';
import 'package:fluro/fluro.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

class PageRouter {
  PageRouter({
    required this.api,
    required this.onApiInactive,
    required this.files,
  }) {
    _withDestination(PageRouterDestination.home, (client) => Home(client: client));
    _withDestination(PageRouterDestination.backup, (client) => Backup(client: client));
    _withDestination(PageRouterDestination.recover, (client) => Recover(client: client));
    _withDestination(PageRouterDestination.search, (client) => Search(client: client));
    _withDestination(PageRouterDestination.operations, (client) => Operations(client: client));
    _withDestination(PageRouterDestination.status, (client) => Status(client: client));
    _withDestination(PageRouterDestination.rules, (client) => Rules(client: client, files: files));
    _withDestination(PageRouterDestination.schedules, (client) => Schedules(client: client, files: files));
    _withDestination(PageRouterDestination.settings, (client) => Settings(files: files, client: client));
    _withDestination(PageRouterDestination.about, (client) => const About());
  }

  final FluroRouter underlying = FluroRouter();

  late ClientApi api;
  late Function() onApiInactive;
  late AppFiles files;

  Drawer _drawer(BuildContext context, PageRouterDestination currentDestination) {
    const divider = Divider();

    ListTile destination(PageRouterDestination destination) {
      return ListTile(
        selected: currentDestination == destination,
        leading: Icon(destination.icon),
        title: Text(destination.title),
        onTap: () => {navigateTo(context, destination: destination)},
      );
    }

    final logo = Center(
      child: SvgPicture.asset(
        'assets/logo.svg',
        width: 48,
        height: 48,
        fit: BoxFit.contain,
      ),
    );

    return Drawer(
      child: ListView(
        padding: EdgeInsets.zero,
        children: [
          divider,
          destination(PageRouterDestination.home),
          destination(PageRouterDestination.backup),
          destination(PageRouterDestination.recover),
          destination(PageRouterDestination.search),
          divider,
          destination(PageRouterDestination.operations),
          destination(PageRouterDestination.status),
          destination(PageRouterDestination.rules),
          destination(PageRouterDestination.schedules),
          divider,
          destination(PageRouterDestination.settings),
          destination(PageRouterDestination.about),
          divider,
          ListTile(
            leading: const Icon(Icons.exit_to_app),
            title: const Text('Close'),
            onTap: () {
              confirmationDialog(
                context,
                title: 'Close application?',
                content: Text(
                  'Closing the application will not stop the background service.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                onConfirm: () => exit(0),
              );
            },
          ),
          ListTile(
            leading: const Icon(Icons.stop_circle_outlined),
            title: const Text('Stop and Close'),
            onTap: () {
              confirmationDialog(
                context,
                title: 'Close application and stop background service?',
                content: Text(
                  'Stopping the background service will terminate all active operations!',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                onConfirm: () {
                  final messenger = ScaffoldMessenger.of(context);
                  api.stop().then((_) {
                    messenger.showSnackBar(const SnackBar(content: Text('Background service stopped...')));
                  }).onError((e, stackTrace) {
                    messenger.showSnackBar(SnackBar(content: Text('Failed to stop background service: [$e]')));
                  }).then((_) => Future.delayed(const Duration(seconds: 1), () => exit(0)));
                },
              );
            },
          ),
          logo,
        ],
      ),
    );
  }

  void _withDestination(PageRouterDestination destination, Widget Function(ClientApi) builder) {
    underlying.define(destination.route, handler: _pageHandler(destination, builder));
  }

  Handler _pageHandler(
    PageRouterDestination destination,
    Widget Function(ClientApi client) builder,
  ) {
    return Handler(
      handlerFunc: (context, _) => Scaffold(
        appBar: TopBar.fromDestination(context!, destination),
        drawer: _drawer(context, destination),
        body: FutureBuilder<bool>(
          future: api.isActive(),
          builder: (context, snapshot) {
            final isActive = snapshot.data ?? false;
            if (snapshot.connectionState == ConnectionState.done) {
              if (isActive) {
                return builder(api);
              } else {
                Future.delayed(const Duration(seconds: 1), () => onApiInactive());
                return const Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.max,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      CircularProgressIndicator(),
                      Padding(padding: EdgeInsets.all(16.0)),
                      Text('Background service is not active; redirecting to login...'),
                    ],
                  ),
                );
              }
            } else {
              return const Center(child: CircularProgressIndicator());
            }
          },
        ),
      ),
    );
  }

  void navigateTo(BuildContext context, {required PageRouterDestination destination}) {
    underlying.navigateTo(context, destination.route);
  }
}
