import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';
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
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    final defaultTheme = ThemeData.light();

    const primaryColor = Color(0xFFbd7600);
    const secondaryColor = Color(0xFF874a00);

    final actualTheme = defaultTheme.copyWith(
      primaryColor: primaryColor,
      colorScheme: defaultTheme.colorScheme.copyWith(
        primary: primaryColor,
        secondary: secondaryColor,
      ),
    );

    return MaterialApp(
      title: 'stasis',
      theme: actualTheme,
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
