import 'dart:io';

import 'package:flutter/foundation.dart' show kDebugMode;
import 'package:flutter/material.dart';
import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/api/app_processes.dart';
import 'package:stasis_client_ui/api/default_client_api.dart';
import 'package:stasis_client_ui/api/default_init_api.dart';
import 'package:stasis_client_ui/api/mock_api_client.dart';
import 'package:stasis_client_ui/color_schemes.dart';
import 'package:stasis_client_ui/config/app_dirs.dart';
import 'package:stasis_client_ui/config/app_files.dart';
import 'package:stasis_client_ui/config/config.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/client_not_configured_card.dart';
import 'package:stasis_client_ui/pages/components/invalid_config_file_card.dart';
import 'package:stasis_client_ui/pages/login.dart';
import 'package:stasis_client_ui/pages/page_destinations.dart';
import 'package:stasis_client_ui/pages/page_router.dart';
import 'package:stasis_client_ui/utils/env.dart';

class ClientApp extends StatefulWidget {
  const ClientApp({super.key});

  @override
  State createState() {
    return _ClientAppState();
  }
}

class _ClientAppState extends State<ClientApp> {
  static const String title = 'stasis';

  static const String applicationName = 'stasis-client';
  static const String applicationMainClass = 'stasis.client.Main';

  @override
  Widget build(BuildContext context) {
    final defaultTheme = ThemeData.from(colorScheme: lightColorScheme, useMaterial3: true);
    final darkTheme = ThemeData.from(colorScheme: darkColorScheme, useMaterial3: true);

    final logo = createLogo();

    MaterialApp appWithContent({required Widget content}) {
      return MaterialApp(
        title: title,
        theme: defaultTheme,
        darkTheme: darkTheme,
        home: centerContent(
          content: [
            content,
            Padding(padding: const EdgeInsets.all(16.0), child: logo),
          ],
        ),
      );
    }

    MaterialApp appWithLogin({required InitApi init, required ClientApi? api, required AppProcesses processes}) {
      return MaterialApp(
        title: title,
        theme: defaultTheme,
        darkTheme: darkTheme,
        home: Login(
          init: init,
          client: api,
          processes: processes,
          loginCallback: (isSuccessful) {
            if (isSuccessful) {
              _reload();
            }
          },
        ),
      );
    }

    final configDir = AppDirs.getUserConfigDir(applicationName: applicationName);

    const processes = AppProcesses(serviceBinary: applicationName, serviceMainClass: applicationMainClass);

    try {
      final mockEnabled = bool.tryParse(
        Platform.environment['STASIS_CLIENT_UI_MOCK_ENABLED'] ?? '',
        caseSensitive: false,
      );

      if (kDebugMode && mockEnabled == true) {
        return MaterialApp(
          title: title,
          theme: defaultTheme,
          darkTheme: darkTheme,
          initialRoute: PageRouterDestination.home.route,
          onGenerateRoute: PageRouter(
            api: MockApiClient(),
            onApiInactive: _reload,
            files: AppFiles.empty(),
          ).underlying.generator,
        );
      } else {
        final files = AppFiles.load(configDir: configDir);

        final apiConfig = files.config.getConfig('stasis.client.api');
        final apiTimeout = Duration(seconds: getConfiguredTimeout());

        InitApi init = DefaultInitApi.fromConfig(config: apiConfig, timeout: apiTimeout);

        final apiToken = files.apiToken;
        if (apiToken != null) {
          ClientApi api = DefaultClientApi.fromConfig(config: apiConfig, apiToken: apiToken, timeout: apiTimeout);

          return FutureBuilder<bool>(
            future: api.isActive(),
            builder: (context, snapshot) {
              if (snapshot.connectionState == ConnectionState.done) {
                final isActive = snapshot.data ?? false;

                if (isActive) {
                  return MaterialApp(
                    title: title,
                    theme: defaultTheme,
                    darkTheme: darkTheme,
                    initialRoute: PageRouterDestination.home.route,
                    onGenerateRoute: PageRouter(api: api, onApiInactive: _reload, files: files).underlying.generator,
                  );
                } else {
                  return appWithLogin(init: init, api: api, processes: processes);
                }
              } else {
                return const Center(child: CircularProgressIndicator());
              }
            },
          );
        } else {
          return appWithLogin(init: init, api: null, processes: processes);
        }
      }
    } on ConfigFileNotAvailableException catch (e) {
      return appWithContent(
        content: ClientNotConfiguredCard(
          applicationName: applicationName,
          processes: processes,
          e: e,
          bootstrapCallback: (isSuccessful) {
            if (isSuccessful) {
              _reload();
            }
          },
        ),
      );
    } on InvalidConfigFileException catch (e) {
      return appWithContent(content: InvalidConfigFileCard.build(context, e));
    }
  }

  void _reload() {
    setState(() {});
  }
}
