import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:server_ui/color_schemes.dart';
import 'package:server_ui/pages/page_destinations.dart';
import 'package:server_ui/pages/page_router.dart';
import 'package:url_strategy/url_strategy.dart';

void main() async {
  await dotenv.load(fileName: '.env');

  setPathUrlStrategy();
  PageRouter.init();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    final defaultTheme = ThemeData.from(colorScheme: lightColorScheme, useMaterial3: true);
    final darkTheme = ThemeData.from(colorScheme: darkColorScheme, useMaterial3: true);

    return MaterialApp(
      title: 'stasis',
      theme: defaultTheme,
      darkTheme: darkTheme,
      initialRoute: PageRouterDestination.home.route,
      onGenerateRoute: PageRouter.underlying.generator,
      builder: (context, child) {
        return MediaQuery(
          data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true),
          child: child!,
        );
      },
    );
  }
}
