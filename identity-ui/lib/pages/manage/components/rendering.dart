import 'package:intl/intl.dart';

extension ExtendedDateTime on DateTime {
  String render() {
    final DateFormat formatter = DateFormat('yyyy-MM-dd HH:mm');
    return formatter.format(toLocal());
  }

  String renderAsDate() {
    final DateFormat formatter = DateFormat('yyyy-MM-dd');
    return formatter.format(toLocal());
  }

  String renderAsTime() {
    final DateFormat formatter = DateFormat('HH:mm');
    return formatter.format(toLocal());
  }
}
