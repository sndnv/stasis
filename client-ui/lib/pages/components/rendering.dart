import 'dart:io';
import 'dart:math';

import 'package:intl/intl.dart';
import 'package:stasis_client_ui/model/datasets/dataset_definition.dart';
import 'package:stasis_client_ui/model/operations/operation.dart' as operation;
import 'package:stasis_client_ui/model/schedules/active_schedule.dart';
import 'package:stasis_client_ui/utils/chrono_unit.dart';
import 'package:stasis_client_ui/utils/file_size_unit.dart';
import 'package:stasis_client_ui/utils/pair.dart';

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

  String renderApproximate() {
    if (inSeconds >= 120) {
      if (inMinutes >= 120) {
        if (inHours >= 48) {
          return Duration(days: inDays).render();
        } else {
          return Duration(hours: inHours).render();
        }
      } else {
        return Duration(minutes: inMinutes).render();
      }
    } else {
      return render();
    }
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
    if (this == 0) {
      return Pair(0, FileSizeUnit.bytes);
    } else {
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
  }

  String renderFileSize() {
    if (this == 0) {
      return '0 ${FileSizeUnit.bytes.symbol}';
    } else {
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
  }

  String renderNumber() {
    return NumberFormat.compact().format(this);
  }
}

extension ExtendedOperationType on operation.Type {
  String render() {
    switch (this) {
      case operation.Type.backup:
        return 'Backup';
      case operation.Type.recovery:
        return 'Recovery';
      case operation.Type.expiration:
        return 'Expiration';
      case operation.Type.validation:
        return 'Validation';
      case operation.Type.keyRotation:
        return 'Key Rotation';
      case operation.Type.garbageCollection:
        return 'Garbage Collection';
    }
  }
}

extension ExtendedOperationStageName on String {
  String toOperationStageString() {
    switch (this) {
      case 'discovered':
        return 'Discovered';
      case 'examined':
        return 'Examined';
      case 'collected':
        return 'Collected';
      case 'pending':
        return 'Pending';
      case 'processed':
        return 'Processed';
      case 'metadata-applied':
        return 'Metadata Applied';
      default:
        return this;
    }
  }
}

extension ExtendedPath on String {
  Pair<String, String> toSplitPath() {
    final trimmed = trim();
    final sanitized = (trimmed.length > 1 && trimmed.endsWith('/')) ? substring(0, length - 1) : trimmed;
    final parent = FileSystemEntity.parentOf(sanitized);
    final name = sanitized.split('/').last;

    return Pair(parent, name.isNotEmpty ? name : '.');
  }
}

extension ExtendedString on String {
  String capitalize() {
    return toBeginningOfSentenceCase(this) ?? this;
  }
}

extension ExtendedAssignment on Assignment {
  String toAssignmentTypeString() {
    if (this is BackupAssignment) {
      return 'Backup';
    } else if (this is ExpirationAssignment) {
      return 'Expiration';
    } else if (this is ValidationAssignment) {
      return 'Validation';
    } else if (this is KeyRotationAssignment) {
      return 'Key Rotation';
    } else {
      throw ArgumentError('Unexpected assignment type encountered: [$runtimeType]');
    }
  }

  String schedule() {
    if (this is BackupAssignment) {
      return (this as BackupAssignment).schedule;
    } else if (this is ExpirationAssignment) {
      return (this as ExpirationAssignment).schedule;
    } else if (this is ValidationAssignment) {
      return (this as ValidationAssignment).schedule;
    } else if (this is KeyRotationAssignment) {
      return (this as KeyRotationAssignment).schedule;
    } else {
      throw ArgumentError('Unexpected assignment type encountered: [$runtimeType]');
    }
  }
}
