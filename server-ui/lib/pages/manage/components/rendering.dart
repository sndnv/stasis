import 'dart:math';

import 'package:intl/intl.dart';
import 'package:server_ui/model/datasets/dataset_definition.dart';
import 'package:server_ui/utils/chrono_unit.dart';
import 'package:server_ui/utils/file_size_unit.dart';
import 'package:server_ui/utils/pair.dart';

extension ExtendedDuration on Duration {
  Pair<int, ChronoUnit> toFields() {
    var amount = inSeconds;
    var unit = ChronoUnit.seconds;

    if (inSeconds > 0 && inSeconds % 60 == 0) {
      final minutes = inSeconds ~/ 60;
      if (minutes % 60 == 0) {
        final hours = minutes ~/ 60;
        if (hours % 24 == 0) {
          final days = hours ~/ 24;
          amount = days;
          unit = ChronoUnit.days;
        } else {
          amount = hours;
          unit = ChronoUnit.hours;
        }
      } else {
        amount = minutes;
        unit = ChronoUnit.minutes;
      }
    }

    return Pair(amount, unit);
  }

  String render() {
    final fields = toFields();
    final amount = fields.a;
    final unit = fields.b;

    return '$amount ${amount == 1 ? unit.singular : unit.plural}';
  }
}

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

extension ExtendedRetention on Retention {
  String render() {
    final renderedDuration = duration.render();

    switch (policy.policyType) {
      case 'all':
        return '$renderedDuration, all versions';
      case 'at-most':
        return '$renderedDuration, at most ${policy.versions ?? 0} ${policy.versions != 1 ? 'versions' : 'version'}';
      case 'latest-only':
        return '$renderedDuration, latest version only';
    }

    return '$renderedDuration, ${policy.policyType}';
  }
}

extension ExtendedNum on num {
  Pair<int, FileSizeUnit> toFields() {
    const base = 1000;

    const units = [
      FileSizeUnit.bytes,
      FileSizeUnit.kilobytes,
      FileSizeUnit.megabytes,
      FileSizeUnit.gigabytes,
      FileSizeUnit.terabytes,
      FileSizeUnit.petabytes,
    ];

    Pair<int, int> nextAmount(int current, int count) {
      if (current % base == 0) {
        final next = current ~/ base;
        return nextAmount(next, count + 1);
      } else {
        return Pair(current, count);
      }
    }

    final fields = nextAmount(toInt(), 0);
    return Pair(fields.a, units[fields.b]);
  }

  String renderFileSize() {
    const base = 1000;
    const units = [
      FileSizeUnit.bytes,
      FileSizeUnit.kilobytes,
      FileSizeUnit.megabytes,
      FileSizeUnit.gigabytes,
      FileSizeUnit.terabytes,
      FileSizeUnit.petabytes,
    ];
    final digitGroups = (log(this) / log(base)).round();
    final amount = NumberFormat('#,##0.#').format(this / pow(base, digitGroups));
    final unit = units[digitGroups].symbol;

    return '$amount $unit';
  }

  String renderNumber() {
    return NumberFormat.compact().format(this);
  }
}
