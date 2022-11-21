enum UserPermission {
  viewSelf(name: 'view-self'),
  viewPrivileged(name: 'view-privileged'),
  viewPublic(name: 'view-public'),
  viewService(name: 'view-service'),
  manageSelf(name: 'manage-self'),
  managePrivileged(name: 'manage-privileged'),
  manageService(name: 'manage-service');

  const UserPermission({required this.name});

  final String name;

  static UserPermission fromName(String name) {
    switch (name) {
      case 'view-self':
        return UserPermission.viewSelf;
      case 'view-privileged':
        return UserPermission.viewPrivileged;
      case 'view-public':
        return UserPermission.viewPublic;
      case 'view-service':
        return UserPermission.viewService;
      case 'manage-self':
        return UserPermission.manageSelf;
      case 'manage-privileged':
        return UserPermission.managePrivileged;
      case 'manage-service':
        return UserPermission.manageService;
      default:
        throw ArgumentError('Unexpected user permission encountered: [$name]');
    }
  }
}
