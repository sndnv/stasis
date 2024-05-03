import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
import 'package:identity_ui/color_schemes.dart';
import 'package:identity_ui/pages/page_router.dart';
import 'package:url_strategy/url_strategy.dart';

void main() async {
  await dotenv.load(fileName: '.env');

  setPathUrlStrategy();
  PageRouter.init();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final defaultTheme = ThemeData.from(colorScheme: lightColorScheme, useMaterial3: true);
    final darkTheme = ThemeData.from(colorScheme: darkColorScheme, useMaterial3: true);

    return MaterialApp(
      title: 'identity',
      theme: defaultTheme,
      darkTheme: darkTheme,
      initialRoute: PageRouterDestination.home.route,
      onGenerateRoute: PageRouter.generator,
    );
  }
}
