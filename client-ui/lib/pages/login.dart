import 'package:stasis_client_ui/api/api_client.dart';
import 'package:stasis_client_ui/api/app_processes.dart';
import 'package:stasis_client_ui/model/service/init_state.dart';
import 'package:stasis_client_ui/pages/common/components.dart';
import 'package:stasis_client_ui/pages/components/background_processes.dart';
import 'package:stasis_client_ui/pages/components/credentials_form.dart';
import 'package:stasis_client_ui/utils/pair.dart';
import 'package:flutter/material.dart';

class Login extends StatefulWidget {
  const Login({
    super.key,
    required this.init,
    required this.client,
    required this.processes,
    required this.loginCallback,
  });

  final InitApi init;
  final ClientApi? client;
  final AppProcesses processes;

  final void Function(bool isSuccessful) loginCallback;

  @override
  State createState() {
    return _LoginState();
  }
}

class _LoginState extends State<Login> {
  static const Duration initRetryWaitTime = Duration(milliseconds: 200);
  static const int initMaxRetries = 10;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
      future: _getApiState(),
      builder: (context, snapshot) {
        final theme = Theme.of(context);

        if (snapshot.data != null && snapshot.connectionState == ConnectionState.done) {
          final data = snapshot.data as Pair<List<int>, InitState?>;
          final activeProcesses = data.a;
          final initState = data.b;

          final logo = createLogo();
          final title = Text('Login', style: theme.textTheme.headlineSmall);

          Widget content;
          if (activeProcesses.isNotEmpty && initState?.startup != 'pending') {
            content = BackgroundProcesses(
              terminationHandler: () {
                widget.processes.stop(activeProcesses);
                Future.delayed(initRetryWaitTime, () => setState(() {}));
              },
            );
          } else {
            content = CredentialsForm(
              applicationName: widget.processes.serviceBinary,
              loginHandler: (u, p) async {
                if (activeProcesses.isEmpty) {
                  await widget.processes.start();
                }

                await _waitForApi(
                  initApi: widget.init,
                  lastState: initState,
                  waitTime: initRetryWaitTime * 2,
                  retriesLeft: initMaxRetries,
                );

                await widget.init.provideCredentials(username: u, password: p);

                final initResult = await _waitForInit(
                  initApi: widget.init,
                  lastState: initState,
                  waitTime: initRetryWaitTime,
                  retriesLeft: initMaxRetries,
                );

                if (initResult.startup == 'successful') {
                  widget.loginCallback(true);
                  return Future.value();
                } else if (initResult.startup == 'failed' && initResult.cause == 'credentials') {
                  return Future.error(AuthenticationFailure());
                } else {
                  widget.loginCallback(false);
                  return Future.error(ApiInitFailure(initResult));
                }
              },
            );
          }

          return centerContent(
            content: [
              createBasicCard(theme, [title, content]),
              Padding(padding: const EdgeInsets.all(16.0), child: logo)
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

  Future<Pair<List<int>, InitState?>> _getApiState() async {
    final activeProcesses = await widget.processes.get();

    final initState = (activeProcesses.isNotEmpty) ? await widget.init.state() : null;

    return Pair(activeProcesses, initState);
  }

  Future<void> _waitForApi({
    required InitApi initApi,
    required InitState? lastState,
    required Duration waitTime,
    required int retriesLeft,
  }) async {
    if (retriesLeft == 0) {
      if (lastState?.startup == 'pending') {
        return;
      } else {
        throw ApiUnavailable();
      }
    } else {
      return Future.delayed(waitTime, () async {
        final currentState = await initApi.state();

        if (currentState.startup == 'pending') {
          return;
        } else {
          return await _waitForApi(
            initApi: initApi,
            lastState: currentState,
            waitTime: waitTime,
            retriesLeft: retriesLeft - 1,
          );
        }
      });
    }
  }

  Future<InitState> _waitForInit({
    required InitApi initApi,
    required InitState? lastState,
    required Duration waitTime,
    required int retriesLeft,
  }) async {
    if (retriesLeft == 0) {
      return lastState ?? InitState.empty();
    } else {
      return Future.delayed(waitTime, () async {
        final currentState = await initApi.state();

        if (currentState.startup == 'successful' || currentState.startup == 'failed') {
          return currentState;
        } else {
          return await _waitForInit(
            initApi: initApi,
            lastState: currentState,
            waitTime: waitTime,
            retriesLeft: retriesLeft - 1,
          );
        }
      });
    }
  }
}

class ApiInitFailure implements Exception {
  ApiInitFailure(this.state);

  InitState state;

  late String message = _transformInitStateCause(state.cause, state.message);

  @override
  String toString() {
    return message;
  }

  String _transformInitStateCause(String? cause, String? message) {
    switch (cause) {
      case 'credentials':
        return 'No or invalid credentials provided';
      case 'token':
        return 'JWT retrieval failed - $message';
      case 'config':
        return 'One or more invalid settings were encountered - $message';
      default:
        return message ?? 'Unexpected failure';
    }
  }
}
