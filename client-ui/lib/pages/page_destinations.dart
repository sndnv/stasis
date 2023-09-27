import 'package:flutter/material.dart';

abstract class PageRouterDestination {
  PageRouterDestination({required this.key, required this.title, required this.route, required this.icon, this.info});

  final String key;
  final String title;
  final String? info;
  final String route;
  final IconData icon;

  int get index => _destinationsByKey[key]!;

  static PageRouterDestination from(int index) => _destinationsByIndex[index];

  static const Map<String, int> _destinationsByKey = {
    'home': 0,
    'backup': 1,
    'recover': 2,
    'search': 3,
    'operations': 4,
    'status': 5,
    'rules': 6,
    'schedules': 7,
    'settings': 8,
    'about': 9,
  };

  static final List<PageRouterDestination> _destinationsByIndex = [
    PageRouterDestination.home,
    PageRouterDestination.backup,
    PageRouterDestination.recover,
    PageRouterDestination.search,
    PageRouterDestination.operations,
    PageRouterDestination.status,
    PageRouterDestination.rules,
    PageRouterDestination.schedules,
    PageRouterDestination.settings,
    PageRouterDestination.about,
  ];

  static PageRouterDestination home = PageRouterDestinationHome();
  static PageRouterDestination backup = PageRouterDestinationBackup();
  static PageRouterDestination recover = PageRouterDestinationRecover();
  static PageRouterDestination search = PageRouterDestinationSearch();
  static PageRouterDestination operations = PageRouterDestinationOperations();
  static PageRouterDestination status = PageRouterDestinationStatus();
  static PageRouterDestination rules = PageRouterDestinationRules();
  static PageRouterDestination schedules = PageRouterDestinationSchedules();
  static PageRouterDestination settings = PageRouterDestinationSettings();
  static PageRouterDestination about = PageRouterDestinationAbout();
}

class PageRouterDestinationHome extends PageRouterDestination {
  PageRouterDestinationHome()
      : super(
    key: 'home',
    title: 'Home',
    route: 'home',
    icon: Icons.home,
  );
}

class PageRouterDestinationBackup extends PageRouterDestination {
  PageRouterDestinationBackup()
      : super(
    key: 'backup',
    title: 'Backup',
    info: 'Backup definitions',
    route: 'backup',
    icon: Icons.upload,
  );
}

class PageRouterDestinationRecover extends PageRouterDestination {
  PageRouterDestinationRecover()
      : super(
    key: 'recover',
    title: 'Recover',
    route: 'recover',
    icon: Icons.download,
  );
}

class PageRouterDestinationSearch extends PageRouterDestination {
  PageRouterDestinationSearch()
      : super(
    key: 'search',
    title: 'Search',
    route: 'search',
    icon: Icons.search,
  );
}

class PageRouterDestinationOperations extends PageRouterDestination {
  PageRouterDestinationOperations()
      : super(
    key: 'operations',
    title: 'Operations',
    route: 'operations',
    icon: Icons.notes,
  );
}

class PageRouterDestinationStatus extends PageRouterDestination {
  PageRouterDestinationStatus()
      : super(
    key: 'status',
    title: 'Status',
    route: 'status',
    icon: Icons.show_chart,
  );
}

class PageRouterDestinationRules extends PageRouterDestination {
  PageRouterDestinationRules()
      : super(
    key: 'rules',
    title: 'Rules',
    route: 'rules',
    icon: Icons.rule,
  );
}

class PageRouterDestinationSchedules extends PageRouterDestination {
  PageRouterDestinationSchedules()
      : super(
    key: 'schedules',
    title: 'Schedules',
    route: 'schedules',
    icon: Icons.schedule,
  );
}

class PageRouterDestinationSettings extends PageRouterDestination {
  PageRouterDestinationSettings()
      : super(
    key: 'settings',
    title: 'Settings',
    route: 'settings',
    icon: Icons.settings,
  );
}

class PageRouterDestinationAbout extends PageRouterDestination {
  PageRouterDestinationAbout()
      : super(
    key: 'about',
    title: 'About',
    route: 'about',
    icon: Icons.info_outline,
  );
}
