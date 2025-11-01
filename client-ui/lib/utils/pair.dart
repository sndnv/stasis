import 'package:freezed_annotation/freezed_annotation.dart';

class Pair<A, B> {
  A a;
  B b;

  Pair(this.a, this.b);

  @override
  String toString() => '($a,$b)';

  @override
  bool operator ==(Object other) =>
      other is Pair && a == other.a && b == other.b;

  @override
  int get hashCode => Object.hash(
    runtimeType,
    const DeepCollectionEquality().hash(a),
    const DeepCollectionEquality().hash(b),
  );
}
