import 'dart:io';

class ApiTokenFactory {
  static String? load({required String path}) {
    try {
      return File(path).readAsStringSync();
    } on FileSystemException {
      return null;
    }
  }
}
