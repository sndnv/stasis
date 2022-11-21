import 'package:freezed_annotation/freezed_annotation.dart';

part 'crate_storage_reservation.freezed.dart';
part 'crate_storage_reservation.g.dart';

@freezed
class CrateStorageReservation with _$CrateStorageReservation {
  @JsonSerializable(fieldRename: FieldRename.snake)
  const factory CrateStorageReservation({
    required String id,
    required String crate,
    required int size,
    required int copies,
    required String origin,
    required String target,
  }) = _CrateStorageReservation;

  factory CrateStorageReservation.fromJson(Map<String, Object?> json) => _$CrateStorageReservationFromJson(json);
}
