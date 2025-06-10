import 'dart:math';

import 'package:fluro/fluro.dart';
import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:server_ui/api/api_client.dart';
import 'package:server_ui/api/bootstrap_api_client.dart';
import 'package:server_ui/api/default_api_client.dart';
import 'package:server_ui/api/derived_passwords.dart';
import 'package:server_ui/api/oauth.dart';
import 'package:server_ui/model/users/user.dart';
import 'package:server_ui/pages/authorize/authorization_callback.dart';
import 'package:server_ui/pages/default/components.dart';
import 'package:server_ui/pages/default/home.dart';
import 'package:server_ui/pages/default/not_found.dart';
import 'package:server_ui/pages/manage/analytics.dart';
import 'package:server_ui/pages/manage/codes.dart';
import 'package:server_ui/pages/manage/commands.dart';
import 'package:server_ui/pages/manage/definitions.dart';
import 'package:server_ui/pages/manage/device_keys.dart';
import 'package:server_ui/pages/manage/devices.dart';
import 'package:server_ui/pages/manage/nodes.dart';
import 'package:server_ui/pages/manage/reservations.dart';
import 'package:server_ui/pages/manage/schedules.dart';
import 'package:server_ui/pages/manage/users.dart';
import 'package:server_ui/pages/page_destinations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:web/web.dart' as web;

class PageRouter {
  static FluroRouter underlying = FluroRouter.appRouter;

  static String serverApi = dotenv.env['SERVER_UI_SERVER_API'] ?? 'http://localhost:8090';
  static String boostrapApi = dotenv.env['SERVER_UI_BOOTSTRAP_API'] ?? 'http://localhost:8091';

  static OAuthConfig config = OAuthConfig(
    authorizationEndpoint:
        Uri.parse(dotenv.env['SERVER_UI_AUTHORIZATION_ENDPOINT'] ?? 'http://localhost:8092/oauth/authorize'),
    tokenEndpoint: Uri.parse(dotenv.env['SERVER_UI_TOKEN_ENDPOINT'] ?? 'http://localhost:8092/oauth/token'),
    clientId: dotenv.env['SERVER_UI_CLIENT_ID'] ?? 'missing-client-id',
    redirectUri: Uri.parse(dotenv.env['SERVER_UI_REDIRECT_URI'] ?? 'http://localhost:8080/login/callback'),
    scopes: (dotenv.env['SERVER_UI_SCOPES'] ?? 'urn:stasis:identity:audience:server-api').split(','),
  );

  static UserAuthenticationPasswordDerivationConfig passwordDerivationConfig =
      UserAuthenticationPasswordDerivationConfig(
    enabled: dotenv.env['SERVER_UI_PASSWORD_DERIVATION_ENABLED']?.toLowerCase() == 'true',
    secretSize: int.tryParse(dotenv.env['SERVER_UI_PASSWORD_DERIVATION_SECRET_SIZE'] ?? '') ?? 16,
    iterations: int.tryParse(dotenv.env['SERVER_UI_PASSWORD_DERIVATION_ITERATIONS'] ?? '') ?? 100000,
    saltPrefix: dotenv.env['SERVER_UI_DERIVATION_SALT_PREFIX'] ?? 'changeme',
  );

  static Future<RouterContext> _login() async {
    final underlying = await OAuth.getClient();
    if (underlying != null) {
      final apiClient = DefaultApiClient(
        server: serverApi,
        underlying: underlying,
      );

      final bootstrapClient = BootstrapApiClient(
        server: boostrapApi,
        underlying: underlying,
      );

      final currentUser = await apiClient.getSelf();

      final usePrivilegedApis = UsePrivilegedApis(
        prefs: await SharedPreferences.getInstance(),
        currentUser: currentUser,
      );

      return RouterContext(
        apiClient: apiClient,
        bootstrapClient: bootstrapClient,
        currentUser: currentUser,
        usePrivilegedApis: usePrivilegedApis,
      );
    } else {
      final uri = await OAuth.generateAuthorizationUri(config);
      web.window.location.assign(uri.toString());
      return Future.error(RedirectPending.instance);
    }
  }

  static Future<void> _logout() async {
    final _ = await OAuth.discardCredentials();
    web.window.location.assign(PageRouterDestination.home.route);
  }

  static AppBar _appBar(
    BuildContext buildContext,
    RouterContext routerContext,
    PageRouterDestination currentDestination,
  ) {
    final theme = Theme.of(buildContext);

    final managementEnabledIcon = IconButton(
      icon: const Icon(Icons.settings, color: Colors.orangeAccent),
      tooltip: 'Management Enabled',
      onPressed: () => showDialog(
        context: buildContext,
        builder: (context) => const AlertDialog(
          title: Text('Server Management Enabled'),
          content: Text(
            'Access to and management of all dataset information, users and devices has been enabled.\n\n'
            'If you want to go back to managing your own datasets and devices, disable server managed from the menu.',
          ),
        ),
      ),
    );

    final logoutButton = IconButton(
      icon: const Icon(Icons.logout),
      tooltip: 'Logout',
      onPressed: () => _logout(),
    );

    final actions = routerContext.usePrivilegedApis.enabled() ? [managementEnabledIcon, logoutButton] : [logoutButton];

    return AppBar(
      title: RichText(
        text: TextSpan(
          children: [TextSpan(text: 'stasis', style: theme.textTheme.titleLarge)],
        ),
      ),
      actions: actions,
    );
  }

  static Drawer _drawer(
    BuildContext buildContext,
    RouterContext routerContext,
    PageRouterDestination currentDestination,
  ) {
    const divider = Divider();

    ListTile destination(PageRouterDestination destination) {
      return ListTile(
        selected: currentDestination == destination,
        leading: Padding(padding: const EdgeInsets.only(left: 8.0), child: Icon(destination.icon)),
        title: Text(destination.title),
        onTap: () => {if (currentDestination != destination) navigateTo(buildContext, destination: destination)},
      );
    }

    final List<Widget> header = [
      ListTile(
        contentPadding: EdgeInsets.zero,
        horizontalTitleGap: 0.0,
        leading: SizedBox(width: 60, child: SvgPicture.asset('logo.svg')),
        title: Text(
          'stasis',
          style: Theme.of(buildContext).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
        ),
      ),
    ];

    final List<Widget> userDestinations = [
      destination(PageRouterDestination.home),
      destination(PageRouterDestination.definitions),
      destination(PageRouterDestination.devices),
      destination(PageRouterDestination.deviceKeys),
      destination(PageRouterDestination.codes),
    ];

    final List<Widget> publicDestinations = [
      destination(PageRouterDestination.schedules),
    ];

    final List<Widget> privilegedDestinations = routerContext.usePrivilegedApis.enabled()
        ? [
            divider,
            destination(PageRouterDestination.users),
            destination(PageRouterDestination.nodes),
            destination(PageRouterDestination.reservations),
            destination(PageRouterDestination.deviceCommands),
            destination(PageRouterDestination.analytics),
          ]
        : [];

    final List<Widget> togglePrivilegedSwitch =
        (routerContext.currentUser.viewPrivilegedAllowed() || routerContext.currentUser.viewServiceAllowed())
            ? [
                ListTile(
                  leading: const Padding(padding: EdgeInsets.only(left: 8.0), child: Icon(Icons.settings)),
                  title: const Text('Manage'),
                  trailing: Switch(
                    value: routerContext.usePrivilegedApis.enabled(),
                    activeColor: Theme.of(buildContext).colorScheme.primary,
                    onChanged: (value) {
                      routerContext.usePrivilegedApis.set(value);
                      web.window.location.assign(PageRouterDestination.home.route);
                    },
                  ),
                ),
              ]
            : [];

    final List<Widget> footer = [
      ListTile(
        leading: const Padding(padding: EdgeInsets.only(left: 8.0), child: Icon(Icons.logout)),
        title: const Text('Logout'),
        onTap: () => _logout(),
      ),
    ];

    return Drawer(
      child: ListView(
        children: header +
            userDestinations +
            [divider] +
            publicDestinations +
            privilegedDestinations +
            [divider] +
            togglePrivilegedSwitch +
            footer,
      ),
    );
  }

  static NavigationRail _navRail(
    BuildContext buildContext,
    RouterContext routerContext,
    PageRouterDestination currentDestination,
  ) {
    NavigationRailDestination destination(destination) {
      final icon = Tooltip(message: destination.title, child: Icon(destination.icon));
      return NavigationRailDestination(
        icon: icon,
        selectedIcon: icon,
        label: Text(destination.title.split(' ').last),
      );
    }

    final List<PageRouterDestination> userDestinations = [
      PageRouterDestination.home,
      PageRouterDestination.definitions,
      PageRouterDestination.devices,
      PageRouterDestination.deviceKeys,
      PageRouterDestination.codes,
    ];

    final List<PageRouterDestination> publicDestinations = [
      PageRouterDestination.schedules,
    ];

    final List<PageRouterDestination> privilegedDestinations = routerContext.usePrivilegedApis.enabled()
        ? [
            PageRouterDestination.users,
            PageRouterDestination.nodes,
            PageRouterDestination.reservations,
            PageRouterDestination.deviceCommands,
            PageRouterDestination.analytics,
          ]
        : [];

    final destinations = userDestinations + publicDestinations + privilegedDestinations;

    final currentDestinationIndex = max(destinations.indexOf(currentDestination), 0);

    return NavigationRail(
      labelType: NavigationRailLabelType.selected,
      destinations: destinations.map((d) => destination(d)).toList(),
      selectedIndex: currentDestinationIndex,
      onDestinationSelected: (nextDestination) => navigateTo(buildContext, destination: destinations[nextDestination]),
    );
  }

  static Handler _pageHandler(
    PageRouterDestination destination,
    Widget Function(BuildContext buildContext, RouterContext routerContext) builder,
  ) {
    return Handler(
        handlerFunc: (_, __) => FutureBuilder<RouterContext>(
              future: _login(),
              builder: (buildContext, snapshot) {
                if (snapshot.data != null && snapshot.connectionState == ConnectionState.done) {
                  final routerContext = snapshot.data!;

                  return Scaffold(
                    appBar: _appBar(buildContext, routerContext, destination),
                    drawer: _drawer(buildContext, routerContext, destination),
                    body: Row(
                      children: [
                        _navRail(buildContext, routerContext, destination),
                        const VerticalDivider(thickness: 1, width: 1),
                        Expanded(child: SelectionArea(child: builder(buildContext, routerContext))),
                      ],
                    ),
                  );
                } else {
                  if (snapshot.error == null || snapshot.error.runtimeType == RedirectPending) {
                    return const Scaffold(body: Center(child: CircularProgressIndicator()));
                  } else if (snapshot.error is AuthenticationFailure) {
                    OAuth.discardCredentials().then((_) {
                      if (buildContext.mounted) navigateTo(buildContext, destination: PageRouterDestination.home);
                    });

                    return centerContent(
                      content: [
                        createBasicCard(
                          Theme.of(buildContext),
                          [
                            errorInfo(
                                title: 'Authentication Failed',
                                description: 'Failed to authenticate; try to log in again.')
                          ],
                        )
                      ],
                    );
                  } else {
                    return centerContent(
                      content: [
                        createBasicCard(
                          Theme.of(buildContext),
                          [errorInfo(title: 'Error', description: snapshot.error.toString())],
                        )
                      ],
                    );
                  }
                }
              },
            ));
  }

  static final Handler _homeHandler = _pageHandler(
    PageRouterDestination.home,
    (_, routerContext) => Home(
      currentUser: routerContext.currentUser,
      usersClient: routerContext.apiClient,
      devicesClient: routerContext.apiClient,
      definitionsClient: routerContext.apiClient,
      passwordDerivationConfig: passwordDerivationConfig,
    ),
  );

  static final Handler _definitionsHandler = _pageHandler(
    PageRouterDestination.definitions,
    (_, routerContext) => DatasetDefinitions(
      definitionsClient: routerContext.apiClient,
      entriesClient: routerContext.apiClient,
      manifestsClient: routerContext.apiClient,
      devicesClient: routerContext.apiClient,
      privileged: routerContext.usePrivilegedApis.enabled(),
    ),
  );

  static final Handler _usersHandler = _pageHandler(
    PageRouterDestination.users,
    (_, routerContext) => Users(
      client: routerContext.apiClient,
      currentUser: routerContext.currentUser,
    ),
  );

  static final Handler _devicesHandler = _pageHandler(
    PageRouterDestination.devices,
    (_, routerContext) => Devices(
      devicesClient: routerContext.apiClient,
      nodesClient: routerContext.apiClient,
      usersClient: routerContext.apiClient,
      bootstrapClient: routerContext.bootstrapClient,
      privileged: routerContext.usePrivilegedApis.enabled(),
    ),
  );

  static final Handler _deviceKeysHandler = _pageHandler(
    PageRouterDestination.deviceKeys,
    (_, routerContext) => DeviceKeys(
      client: routerContext.apiClient,
      privileged: routerContext.usePrivilegedApis.enabled(),
    ),
  );

  static final Handler _deviceCommandsHandler = _pageHandler(
    PageRouterDestination.deviceCommands,
    (_, routerContext) => Commands(client: routerContext.apiClient, privileged: true, forDevice: null),
  );

  static final Handler _schedulesHandler = _pageHandler(
    PageRouterDestination.schedules,
    (_, routerContext) => Schedules(
      client: routerContext.apiClient,
      privileged: routerContext.usePrivilegedApis.enabled(),
    ),
  );

  static final Handler _nodesHandler = _pageHandler(
    PageRouterDestination.nodes,
    (_, routerContext) => Nodes(client: routerContext.apiClient),
  );

  static final Handler _reservationsHandler = _pageHandler(
    PageRouterDestination.reservations,
    (_, routerContext) => CrateStorageReservations(client: routerContext.apiClient),
  );

  static final Handler _codesHandler = _pageHandler(
    PageRouterDestination.codes,
    (_, routerContext) => BootstrapCodes(
      client: routerContext.bootstrapClient,
      privileged: routerContext.usePrivilegedApis.enabled(),
    ),
  );

  static final Handler _analyticsHandler = _pageHandler(
    PageRouterDestination.analytics,
    (_, routerContext) => Analytics(client: routerContext.apiClient),
  );

  static final Handler _loginCallbackHandler = Handler(
    handlerFunc: (_, __) => AuthorizationCallback(config: config),
  );

  static final Handler _notFoundHandler = Handler(
    handlerFunc: (_, __) => const NotFound(),
  );

  static void init() {
    underlying.define(PageRouterDestination.home.route, handler: _homeHandler);
    underlying.define(PageRouterDestination.definitions.route, handler: _definitionsHandler);
    underlying.define(PageRouterDestination.users.route, handler: _usersHandler);
    underlying.define(PageRouterDestination.devices.route, handler: _devicesHandler);
    underlying.define(PageRouterDestination.deviceKeys.route, handler: _deviceKeysHandler);
    underlying.define(PageRouterDestination.deviceCommands.route, handler: _deviceCommandsHandler);
    underlying.define(PageRouterDestination.schedules.route, handler: _schedulesHandler);
    underlying.define(PageRouterDestination.nodes.route, handler: _nodesHandler);
    underlying.define(PageRouterDestination.reservations.route, handler: _reservationsHandler);
    underlying.define(PageRouterDestination.codes.route, handler: _codesHandler);
    underlying.define(PageRouterDestination.analytics.route, handler: _analyticsHandler);
    underlying.define('login/callback', handler: _loginCallbackHandler);
    underlying.notFoundHandler = _notFoundHandler;
  }

  static void navigateTo(
    BuildContext context, {
    required PageRouterDestination destination,
    String? withFilter,
    bool replace = false,
  }) {
    final route = withFilter == null ? destination.route : '${destination.route}?filter=$withFilter';
    underlying.navigateTo(context, route, replace: replace);
  }
}

class RouterContext {
  RouterContext({
    required this.apiClient,
    required this.bootstrapClient,
    required this.currentUser,
    required this.usePrivilegedApis,
  });

  DefaultApiClient apiClient;
  BootstrapApiClient bootstrapClient;
  User currentUser;
  UsePrivilegedApis usePrivilegedApis;
}

class RedirectPending {
  static RedirectPending instance = RedirectPending();
}

class UsePrivilegedApis {
  UsePrivilegedApis({required this.prefs, required this.currentUser});

  SharedPreferences prefs;
  User currentUser;

  bool _userHasPrivilegedPermissions() {
    return currentUser.viewPrivilegedAllowed() || currentUser.viewServiceAllowed();
  }

  bool enabled() {
    return _userHasPrivilegedPermissions() && (prefs.getBool(Keys._usePrivilegedApis) ?? false);
  }

  void set(bool value) {
    prefs.setBool(Keys._usePrivilegedApis, value);
  }
}

class Keys {
  static const String _usePrivilegedApis = 'stasis.server_ui.settings.use_privileged_apis';
}
