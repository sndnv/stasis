import 'package:fluro/fluro.dart';
import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:identity_ui/api/api_client.dart';
import 'package:identity_ui/api/default_api_client.dart';
import 'package:identity_ui/api/oauth.dart';
import 'package:identity_ui/pages/authorize/authorization_callback.dart';
import 'package:identity_ui/pages/authorize/authorize.dart';
import 'package:identity_ui/pages/authorize/derived_passwords.dart';
import 'package:identity_ui/pages/default/home.dart';
import 'package:identity_ui/pages/default/not_found.dart';
import 'package:identity_ui/pages/manage/apis.dart';
import 'package:identity_ui/pages/manage/clients.dart';
import 'package:identity_ui/pages/manage/codes.dart';
import 'package:identity_ui/pages/manage/owners.dart';
import 'package:identity_ui/pages/manage/tokens.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:web/web.dart' as web;

class PageRouter {
  static FluroRouter underlying = FluroRouter.appRouter;

  static Route<dynamic>? generator(RouteSettings routeSettings) {
    final originalUri = Uri.parse(routeSettings.name ?? '/');
    final queryParams = Map.of(originalUri.queryParameters);

    final originalRedirectUri = queryParams['redirect_uri'];
    final escapedRedirectUri =
        originalRedirectUri != null ? Uri.encodeQueryComponent(Uri.decodeQueryComponent(originalRedirectUri)) : null;

    if (originalRedirectUri != escapedRedirectUri) {
      queryParams['redirect_uri'] = escapedRedirectUri!;
      final updatedUri = originalUri.replace(queryParameters: queryParams);

      return underlying.generator(RouteSettings(name: updatedUri.toString(), arguments: routeSettings.arguments));
    } else {
      return underlying.generator(routeSettings);
    }
  }

  static String server = dotenv.env['IDENTITY_UI_IDENTITY_SERVER'] ?? 'http://localhost:8080';

  static OAuthConfig oauthConfig = OAuthConfig(
    authorizationEndpoint: Uri.parse('${Uri.base.origin}/login/authorize'),
    tokenEndpoint: Uri.parse('$server${dotenv.env['IDENTITY_UI_TOKEN_ENDPOINT'] ?? '/oauth/token'}'),
    clientId: dotenv.env['IDENTITY_UI_CLIENT_ID'] ?? 'missing-client-id',
    redirectUri: Uri.parse(dotenv.env['IDENTITY_UI_REDIRECT_URI'] ?? 'http://localhost:8080/login/callback'),
    scopes: (dotenv.env['IDENTITY_UI_SCOPES'] ?? 'urn:stasis:identity:audience:manage-identity').split(','),
  );

  static UserAuthenticationPasswordDerivationConfig passwordDerivationConfig =
      UserAuthenticationPasswordDerivationConfig(
    enabled: dotenv.env['IDENTITY_UI_PASSWORD_DERIVATION_ENABLED']?.toLowerCase() == 'true',
    secretSize: int.tryParse(dotenv.env['IDENTITY_UI_PASSWORD_DERIVATION_SECRET_SIZE'] ?? '') ?? 16,
    iterations: int.tryParse(dotenv.env['IDENTITY_UI_PASSWORD_DERIVATION_ITERATIONS'] ?? '') ?? 100000,
    saltPrefix: dotenv.env['IDENTITY_UI_DERIVATION_SALT_PREFIX'] ?? 'changeme',
  );

  static Future<ApiClient> _login() async {
    final underlying = await OAuth.getClient();
    if (underlying != null) {
      final claims = JwtClaims.fromCredentials(underlying.credentials);

      return DefaultApiClient(
        server: server,
        underlying: underlying,
        clientId: oauthConfig.clientId,
        subject: claims.subject,
        audience: claims.audience,
      );
    } else {
      final uri = await OAuth.generateAuthorizationUri(oauthConfig);
      web.window.location.assign(uri.toString());
      return Future.error(RedirectPending.instance);
    }
  }

  static Future<void> _logout() async {
    final _ = await OAuth.discardCredentials();
    web.window.location.assign(PageRouterDestination.home.route);
  }

  static AppBar _appBar(BuildContext context, PageRouterDestination currentDestination) {
    final theme = Theme.of(context);

    return AppBar(
      title: RichText(
        text: TextSpan(
          children: [
            TextSpan(text: 'identity', style: theme.textTheme.titleLarge),
            const WidgetSpan(child: Padding(padding: EdgeInsets.only(left: 4))),
            TextSpan(text: '/', style: theme.textTheme.titleLarge?.copyWith(color: theme.colorScheme.secondary)),
            const WidgetSpan(child: Padding(padding: EdgeInsets.only(right: 4))),
            TextSpan(text: currentDestination.title, style: theme.textTheme.titleLarge),
          ],
        ),
      ),
      actions: [
        IconButton(
          icon: const Icon(Icons.logout),
          tooltip: 'Logout',
          onPressed: () => _logout(),
        ),
      ],
    );
  }

  static Drawer _drawer(BuildContext context, PageRouterDestination currentDestination) {
    const divider = Divider();

    ListTile destination(PageRouterDestination destination) {
      return ListTile(
        selected: currentDestination == destination,
        leading: Icon(destination.icon),
        title: Text(destination.title),
        onTap: () => {if (currentDestination != destination) navigateTo(context, destination: destination)},
      );
    }

    return Drawer(
      child: ListView(
        children: [
          destination(PageRouterDestination.home),
          destination(PageRouterDestination.apis),
          destination(PageRouterDestination.clients),
          destination(PageRouterDestination.owners),
          divider,
          destination(PageRouterDestination.codes),
          destination(PageRouterDestination.tokens),
          divider,
          ListTile(
            leading: const Icon(Icons.logout),
            title: const Text('Logout'),
            onTap: () => _logout(),
          ),
          divider,
          Center(
            child: SvgPicture.asset(
              'logo.svg',
              width: 48,
              height: 48,
              fit: BoxFit.contain,
            ),
          ),
        ],
      ),
    );
  }

  static Handler _pageHandler(
    PageRouterDestination destination,
    Widget Function(ApiClient client) builder,
  ) {
    return Handler(
        handlerFunc: (context, _) => Scaffold(
            appBar: _appBar(context!, destination),
            drawer: _drawer(context, destination),
            body: FutureBuilder<ApiClient>(
              future: _login(),
              builder: (context, snapshot) {
                if (snapshot.data != null && snapshot.connectionState == ConnectionState.done) {
                  return SelectionArea(child: builder(snapshot.data!));
                } else {
                  return _pendingOrFailedSnapshot(snapshot);
                }
              },
            )));
  }

  static final Handler _homeHandler = _pageHandler(
    PageRouterDestination.home,
    (client) => Home(client: client),
  );

  static final Handler _apisHandler = _pageHandler(
    PageRouterDestination.apis,
    (client) => Apis(client: client),
  );

  static final Handler _clientsHandler = _pageHandler(
    PageRouterDestination.clients,
    (client) => Clients(client: client),
  );

  static final Handler _codesHandler = _pageHandler(
    PageRouterDestination.codes,
    (client) => Codes(client: client),
  );

  static final Handler _ownersHandler = _pageHandler(
    PageRouterDestination.owners,
    (client) => Owners(client: client, passwordDerivationConfig: passwordDerivationConfig),
  );

  static final Handler _tokensHandler = _pageHandler(
    PageRouterDestination.tokens,
    (client) => Tokens(client: client),
  );

  static final Handler _authorizeHandler = Handler(
    handlerFunc: (_, _) => FutureBuilder<SharedPreferences>(
      future: SharedPreferences.getInstance(),
      builder: (_, snapshot) {
        if (snapshot.data != null && snapshot.connectionState == ConnectionState.done) {
          return Authorize(
            authorizationEndpoint: Uri.parse('$server/oauth/authorization'),
            oauthConfig: oauthConfig,
            passwordDerivationConfig: passwordDerivationConfig,
            prefs: snapshot.data!,
          );
        } else {
          return _pendingOrFailedSnapshot(snapshot);
        }
      },
    ),
  );

  static final Handler _loginCallbackHandler = Handler(
    handlerFunc: (_, _) => AuthorizationCallback(config: oauthConfig),
  );

  static final Handler _notFoundHandler = Handler(
    handlerFunc: (_, _) => const NotFound(),
  );

  static void init() {
    underlying.define(PageRouterDestination.home.route, handler: _homeHandler);
    underlying.define(PageRouterDestination.apis.route, handler: _apisHandler);
    underlying.define(PageRouterDestination.clients.route, handler: _clientsHandler);
    underlying.define(PageRouterDestination.codes.route, handler: _codesHandler);
    underlying.define(PageRouterDestination.owners.route, handler: _ownersHandler);
    underlying.define(PageRouterDestination.tokens.route, handler: _tokensHandler);
    underlying.define('login/authorize', handler: _authorizeHandler);
    underlying.define('login/callback', handler: _loginCallbackHandler);
    underlying.notFoundHandler = _notFoundHandler;
  }

  static void navigateTo(BuildContext context, {required PageRouterDestination destination, bool replace = false}) {
    underlying.navigateTo(context, destination.route);
  }

  static Widget _pendingOrFailedSnapshot<T>(AsyncSnapshot<T> snapshot) {
    if (snapshot.error == null || snapshot.error.runtimeType == RedirectPending) {
      return const Center(child: CircularProgressIndicator());
    } else {
      final error = snapshot.error.toString();

      return Center(
        child: Container(
          constraints: const BoxConstraints.tightFor(width: 400, height: 150),
          child: Card(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Padding(
                  padding: const EdgeInsets.only(left: 8, top: 12, right: 8),
                  child: ListTile(
                    leading: const Icon(Icons.error),
                    title: const Text('Error'),
                    subtitle: Text(error),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }
  }
}

abstract class PageRouterDestination {
  PageRouterDestination({required this.key, required this.title, required this.route, required this.icon});

  final String key;
  final String title;
  final String route;
  final IconData icon;

  static PageRouterDestination home = PageRouterDestinationHome();
  static PageRouterDestination apis = PageRouterDestinationApis();
  static PageRouterDestination clients = PageRouterDestinationClients();
  static PageRouterDestination codes = PageRouterDestinationCodes();
  static PageRouterDestination owners = PageRouterDestinationOwners();
  static PageRouterDestination tokens = PageRouterDestinationTokens();
}

class PageRouterDestinationHome extends PageRouterDestination {
  PageRouterDestinationHome()
      : super(
          key: 'home',
          title: 'Home',
          route: '/manage',
          icon: Icons.home,
        );
}

class PageRouterDestinationApis extends PageRouterDestination {
  PageRouterDestinationApis()
      : super(
          key: 'apis',
          title: 'APIs',
          route: '/manage/apis',
          icon: Icons.storage,
        );
}

class PageRouterDestinationClients extends PageRouterDestination {
  PageRouterDestinationClients()
      : super(
          key: 'clients',
          title: 'Clients',
          route: '/manage/clients',
          icon: Icons.apps,
        );
}

class PageRouterDestinationCodes extends PageRouterDestination {
  PageRouterDestinationCodes()
      : super(
          key: 'codes',
          title: 'Authorization Codes',
          route: '/manage/codes',
          icon: Icons.lock,
        );
}

class PageRouterDestinationOwners extends PageRouterDestination {
  PageRouterDestinationOwners()
      : super(
          key: 'owners',
          title: 'Resource Owners',
          route: '/manage/owners',
          icon: Icons.person,
        );
}

class PageRouterDestinationTokens extends PageRouterDestination {
  PageRouterDestinationTokens()
      : super(
          key: 'tokens',
          title: 'Refresh Tokens',
          route: '/manage/tokens',
          icon: Icons.refresh,
        );
}

class RedirectPending {
  static RedirectPending instance = RedirectPending();
}
