import 'package:flutter/material.dart';

abstract class PageRouterDestination {
  PageRouterDestination({required this.key, required this.title, required this.route, required this.icon});

  final String key;
  final String title;
  final String route;
  final IconData icon;

  static PageRouterDestination home = PageRouterDestinationHome();
  static PageRouterDestination definitions = PageRouterDestinationDefinitions();
  static PageRouterDestination users = PageRouterDestinationUsers();
  static PageRouterDestination devices = PageRouterDestinationDevices();
  static PageRouterDestination deviceKeys = PageRouterDestinationDeviceKeys();
  static PageRouterDestination schedules = PageRouterDestinationSchedules();
  static PageRouterDestination nodes = PageRouterDestinationNodes();
  static PageRouterDestination reservations = PageRouterDestinationReservations();
  static PageRouterDestination codes = PageRouterDestinationBootstrapCodes();
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

class PageRouterDestinationDefinitions extends PageRouterDestination {
  PageRouterDestinationDefinitions()
      : super(
          key: 'definitions',
          title: 'Dataset Definitions',
          route: '/manage/datasets/definitions',
          icon: Icons.backup_table,
        );
}

class PageRouterDestinationUsers extends PageRouterDestination {
  PageRouterDestinationUsers()
      : super(
          key: 'users',
          title: 'Users',
          route: '/manage/users',
          icon: Icons.group,
        );
}

class PageRouterDestinationDevices extends PageRouterDestination {
  PageRouterDestinationDevices()
      : super(
          key: 'devices',
          title: 'Devices',
          route: '/manage/devices',
          icon: Icons.devices,
        );
}

class PageRouterDestinationDeviceKeys extends PageRouterDestination {
  PageRouterDestinationDeviceKeys()
      : super(
          key: 'devices-keys',
          title: 'Device Keys',
          route: '/manage/device-keys',
          icon: Icons.key,
        );
}

class PageRouterDestinationSchedules extends PageRouterDestination {
  PageRouterDestinationSchedules()
      : super(
          key: 'schedules',
          title: 'Schedules',
          route: '/manage/schedules',
          icon: Icons.schedule,
        );
}

class PageRouterDestinationNodes extends PageRouterDestination {
  PageRouterDestinationNodes()
      : super(
          key: 'nodes',
          title: 'Nodes',
          route: '/manage/nodes',
          icon: Icons.hub,
        );
}

class PageRouterDestinationReservations extends PageRouterDestination {
  PageRouterDestinationReservations()
      : super(
          key: 'reservations',
          title: 'Crate Storage Reservations',
          route: '/manage/reservations',
          icon: Icons.data_usage,
        );
}

class PageRouterDestinationBootstrapCodes extends PageRouterDestination {
  PageRouterDestinationBootstrapCodes()
      : super(
          key: 'codes',
          title: 'Bootstrap Codes',
          route: '/manage/codes',
          icon: Icons.qr_code,
        );
}
